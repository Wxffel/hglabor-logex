package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path

object Extract : CliktCommand(
    help = "Extracts specified lines of files"
) {
    private val inputPath by argument(help = "The path of the folder to extract lines from").path(
        mustExist = true, canBeFile = false
    )

    private val outputPath by argument(help = "The path where to put the output file").path(
        mustExist = true, canBeFile = false
    )

    private val outputFileName by option(
        "-of", "--outputfilename",
        help = "The name of the file with the extracted lines"
    ).default("HGLaborMessages")

    private val startDate by option(
        "-sd", "--startdate",
        help = "Only extracts files after the start date (yyyy-mm-dd)"
    ).default(hgLaborStartDate)

    private val charset by option(
        "-cs", "--charset",
        help = "Specifies the charset which will be used to read the files"
    ).default("UTF_8")

    override fun run() = extract()

    private fun extract() {
        val pathContent = File("$inputPath/").list() ?: kotlin.run {
            TerminalMessages.noFilesFound("$inputPath/")
            return
        }

        terminal.println(TextColors.brightMagenta("inputPath=$inputPath/"))
        terminal.println(TextColors.brightMagenta("outputPath=$outputPath/"))

        val vanillaInputFiles = InputFileUtils.inputFilesToMinecraftLogs("$inputPath/", startDate, charset.toCharset())
        val blcInputFiles: List<MinecraftLog> =
            if (pathContent.contains("blclient"))
                InputFileUtils.inputFilesToMinecraftLogs(
                    "$inputPath/blclient/minecraft/",
                    startDate,
                    charset.toCharset()
                )
            else listOf()

        terminal.println(TextColors.brightYellow("Found ${vanillaInputFiles.size} vanilla files"))
        terminal.println(TextColors.brightYellow("Found ${blcInputFiles.size} blc files"))

        val messageHolder = hashSetOf<Pair<String, Boolean>>() // String=Content, Boolean=IS_CHAT_MESSAGE
        var processedFiles = 0

        runBlocking(Dispatchers.IO) {
            for (minecraftLog in vanillaInputFiles) {
                launch {
                    processedFiles++
                    val date = if (minecraftLog.name.isCreationDateFromAttrNeeded())
                        minecraftLog.creationDateFromAttributes
                    else minecraftLog.creationDateFromName

                    for (line in minecraftLog.content) {
                        if (line.isBlank() || line.length < 24) continue

                        val time = LogExRegex.timePattern.find(line)?.value

                        // the 21st char in the vanilla log is (in case of a chat message) the ':'
                        val message = LogExRegex.messagePattern.find(line, startIndex = 21)?.value

                        // connecting-messages
                        if (message == null) {
                            // the 23st char in the vanilla log is (in case of a connecting message) the 'C'
                            // e.g. [12:07:12] [main/INFO]: Connecting to tcpshield.hglabor.de., 25565
                            // continue if null -> not a chat message nor a connecting message
                            val connectingMessage = LogExRegex.connectingPattern
                                .find(line, startIndex = 23)?.value ?: continue
                            // Connecting to server.hglabor.de., 25751
                            var serverIP = connectingMessage.substring(14)
                            if (serverIP.endsWith('.')) serverIP = serverIP.dropLast(1)
                            messageHolder.add("$date $time $serverIP" to false)
                            continue
                        }

                        var chatMessage =
                            message.drop(9) // get rid of ": [CHAT] " now there is only the message content

                        if (chatMessage.isHGLaborPrivateMessage()) continue

                        chatMessage = chatMessage.conditioning()

                        if (chatMessage.isPossiblyHGLaborPublicMessage()) {
                            // replaces "?" or "»" with the formattedMessageDelimiter ">"
                            val index = chatMessage.indexOf(' ') + 1
                            chatMessage = chatMessage.replaceRange(index..index, messageDelimiter)
                            messageHolder.add("$date $time $chatMessage" to true)
                        } else if (chatMessage.isSurvivalMessage()) {
                            // replaces "<" and ">" before and after the minecraft name, also a delimiter is added
                            chatMessage = chatMessage.replaceFirst("<", "")
                                .replaceFirst(">", " $messageDelimiter")
                            messageHolder.add("$date $time $chatMessage" to true)
                        }
                    }
                }
            }
        }
        val hgLaborChatMessages = messageHolder.extractHGLaborSurvivalMessages()
        val outputFile = Utils.createFile("$outputPath/", outputFileName) ?: return
        hgLaborChatMessages.toSortedSet().forEach { outputFile.appendText(it + "\n") }
        terminal.println(TextColors.brightMagenta("Processed $processedFiles files"))
    }
}