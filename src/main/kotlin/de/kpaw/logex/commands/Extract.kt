package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.kpaw.logex.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile
import kotlin.system.measureTimeMillis

object Extract : CliktCommand(
    help = "Extracts specified lines of files"
) {
    private val inputPath by argument(help = "The path of the folder to extract lines from").path(
        mustExist = true, canBeFile = false
    ).default(Path.of("${System.getProperty("user.home")}\\AppData\\Roaming\\.minecraft\\logs")) // trash

    private val outputPath by argument(help = "The path where to put the output file").path(
        mustExist = true, canBeFile = false
    ).default(Path.of("${System.getProperty("user.home")}\\Desktop\\")) // trash

    private val outputFileName by option(
        "-of", "--outputfilename",
        help = "The name of the file with the extracted lines"
    ).default("HGLaborMessages")

    private val startDate by option(
        "-sd", "--startdate",
        help = "Only extracts files after the start date"
    ).default(hgLaborStartDate)

    override fun run() = extract()

    private fun extract() {
        val filenames = File("$inputPath/").list() ?: kotlin.run {
            println("Error! There are no files in $inputPath/")
            return
        }

        println("outputPath=$outputPath")
        println("inputPath=$inputPath")

        val inputFiles = (filenames as Array<*>).map { File("$inputPath/$it") }
            .filter { it.isFile }.toMutableList()

        val inputZips = (filenames as Array<*>).filter {
            (it as String).endsWith(".zip") ||
                    (it as String).endsWith(".gz")
        }
            .map { ZipFile("$inputPath/$it") }.toMutableList()

        inputZips.forEach { zipFile ->
            val entry = zipFile.entries().toList().first()
            zipFile.getInputStream(entry).use { input ->
                val file = File(entry.name)
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
                inputFiles.add(file)
            }
        }

        // Adding blc logs to vanilla logs (inputfiles)
        if (filenames.contains("blclient")) {
            val blcFilePath = "$inputPath/blclient/minecraft/"
            val blcFileNames = File(blcFilePath).list() ?:
            println("No files in $inputPath/blclient/minecraft/ were found")
            val blcInputFiles = (blcFileNames as Array<*>).map { File("$blcFilePath$it") }
                .filter { file -> file.isFile }

            inputFiles.addAll(blcInputFiles)
        }

        val outputFile = Utils.createFile("$outputPath/", outputFileName) ?: return

        val messageHolder = hashSetOf<String>()
        val survivalMessageFiles = mutableMapOf<String, SurvivalMessageHolder>()

        val timeToTake = measureTimeMillis {

            runBlocking(Dispatchers.IO) {
                inputFiles.forEach { file ->
                    launch {
                        val cleanFileName = file.nameWithoutExtension
                        if (cleanFileName.split("-")[0] < startDate) return@launch

                        var date = cleanFileName.split("-").dropLast(1).joinToString("-")

                        if (cleanFileName.contains("debug") || cleanFileName == "debug") {
                            val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                            date = attr.creationTime().toString().split("T")[0]
                        }

                        file.forEachLine(isoCharset) { line ->
                            if (line.isBlank() || line.length < 24) return@forEachLine

                            val vanillaSubstring = line.substring(24)
                            val blcSubstring: String? = if (line.length >= 33) line.substring(33) else null
                            val optifineString: String? =
                                // [26Jun2021 18:22:21.883] [Render thread/INFO] [net.minecraft.client.gui.NewChatGui/]: [CHAT] <msg>
                                if (line.length >= 46) {
                                    line.substring(46).split(" ")
                                        .drop(1).joinToString(" ")
                                } else null

                            var time = line.substring(1, 9)

                            var chatMessage: String? = when {
                                vanillaSubstring.startsWith("[CHAT]") -> vanillaSubstring
                                blcSubstring?.startsWith("[CHAT]") == true -> blcSubstring
                                else -> null
                            }

                            if (chatMessage == null) {
                                if (optifineString?.startsWith("[CHAT]") == true) {
                                    chatMessage = optifineString

                                    if (line.indexOfFirst { it == '.' } > 0) {

                                        try {
                                            time = line.substring(
                                                line.indexOfFirst { it == ' ' } + 1,
                                                line.indexOfFirst { it == '.' }
                                            )
                                        } catch (e: Exception) {
                                            println(e)
                                            println("Line \"$line\" in log \"${file.name}\"")
                                        }

                                    } else println("Line \"$line\" in log named \"${file.name}\" seems unhandled.")
                                }
                            }

                            // connecting-messages
                            if (chatMessage == null) {

                                var connectingMessage: String? = when {
                                    vanillaSubstring.startsWith("Connecting") -> vanillaSubstring
                                    blcSubstring?.startsWith("Connecting") == true -> blcSubstring
                                    else -> null
                                }

                                if (connectingMessage == null) {
                                    // Optifine check
                                    if (optifineString?.startsWith("Connecting") == true)
                                        connectingMessage = optifineString
                                    else return@forEachLine
                                }

                                // Connecting to server.hglabor.de., 25751
                                var serverIP = connectingMessage.substring(14, connectingMessage.indexOf(','))
                                if (serverIP.endsWith('.')) serverIP = serverIP.dropLast(1)

                                survivalMessageFiles.putIfAbsent(cleanFileName, SurvivalMessageHolder(cleanFileName))
                                survivalMessageFiles[cleanFileName]?.connectionMessages?.add("$date $time $serverIP")
                                return@forEachLine
                            }

                            chatMessage = chatMessage.drop(7) // get rid of "[CHAT]" + a whitespace

                            if (chatMessage.isHGLaborPrivateMessage()) return@forEachLine

                            chatMessage = chatMessage.conditioning()

                            // skip this weird playerlist thing on this guild server
                            if (chatMessage.endsWith("?  ")) return@forEachLine

                            if (chatMessage.isHGLaborPublicMessage()) {
                                val index = chatMessage.indexOf(' ') + 1
                                chatMessage = chatMessage.replaceRange(index..index, formattedMessageDelimiter)
                                messageHolder.add("$date $time $chatMessage")
                            } else if (chatMessage.isSurvivalMessage()) {
                                survivalMessageFiles.putIfAbsent(cleanFileName, SurvivalMessageHolder(cleanFileName))
                                survivalMessageFiles[cleanFileName]?.survivalMessages?.add("$date $time $chatMessage")
                            }
                        }
                    }
                }
            }

            val hgLaborSurvivalMessages = extractHGLaborSurvivalMessages(survivalMessageFiles)
            hgLaborSurvivalMessages.forEach {
                val formattedMessage = it.replaceFirst("<", "")
                    .replaceFirst(">", " $formattedMessageDelimiter")

                messageHolder.add(formattedMessage)
            }

            messageHolder.toSortedSet().forEach { outputFile.appendText(it + "\n") }
        }
        println("Extracting took $timeToTake ms")
    }
}