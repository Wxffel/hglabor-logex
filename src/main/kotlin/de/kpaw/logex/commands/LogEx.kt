package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextColors.*
import de.kpaw.logex.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

object LogEx : CliktCommand(
    help = "Extracts all public messages sent on HGLabor."
) {
    private val inputPath by argument(help = "The path to the directory containing the logs").path(
        mustExist = true, canBeFile = false
    ).default(MC_LOGS_PATH)

    private val outputPath by argument(help = "The path where to put the output file").path(
        mustExist = true, canBeFile = false
    ).default(USER_DESKTOP_PATH)

    private val outputFileName by option(
        "-of", "--outputfilename",
        help = "The name of the file which will contain the hglabor-messages"
    ).default("HGLaborMessages_$CURRENT_DATE")

    private val startDate by option(
        "-sd", "--startdate",
        help = "Only extracts files after the start date (yyyy-mm-dd) (<- ansonsten gl haha)"
    ).default(HGLABOR_START_DATE)

    private val chunksPerCoroutine by option(
        "-cpc", "-chunkspercoroutine",
        help = "Anzahl der Dateien pro Coroutine (Weniger Dateien = mehr Coroutines = schneller). Default: 200"
    ).int().check("Must >= 1") { it >= 1 }

    private val charset by option(
        "-cs", "-charset",
        help = "Charset for reading logs. iso or utf_8 (default)"
    ).choice("utf_8", "iso").default("utf_8")

    override fun run() = extract()

    private fun extract() {

        val actualInputPath = "$inputPath/"
        val actualBLCPath = "$inputPath/$BLC_LOGS_PATH/"
        val actualOutputPath = "$outputPath/"
        actualCharset = if (charset == "iso") StandardCharsets.ISO_8859_1 else actualCharset

        terminal.println(brightMagenta("inputPath=$actualInputPath"))
        terminal.println(brightMagenta("outputPath=$actualOutputPath"))
        terminal.println(brightMagenta("outputFile=$outputFileName.txt"))
        terminal.println(brightMagenta("charset=$actualCharset"))
        terminal.println(brightMagenta("SYSTEM_OS_NAME=$SYSTEM_OS_NAME"))
        terminal.println(brightMagenta("USER_HOME=$USER_HOME"))
        terminal.println(brightMagenta("MC_LOG_PATH=$actualInputPath"))
        terminal.println(brightMagenta("BLC_LOG_PATH=$actualBLCPath"))

        val inputStrings = File(actualInputPath).pathContent() ?: return
        val blcStrings: MutableList<String> = File(actualBLCPath).pathContent() ?: mutableListOf()

        inputStrings.remove("blclient")

        terminal.println(brightYellow("Found ${inputStrings.size} files/directories in $actualInputPath"))
        terminal.println(brightYellow("Found ${blcStrings.size} files/directories in $actualBLCPath"))

        // filter files which are too old
        val tooOld = inputStrings.filter { it.split('.')[0] < "$startDate-1" }
        val tooOldBLC = blcStrings.filter { it.split('.')[0] < "$startDate-1" }

        inputStrings.removeAll(tooOld)
        blcStrings.removeAll(tooOldBLC)

        terminal.println(brightYellow("Too old vanilla logs: ${tooOld.size} (before $startDate)"))
        terminal.println(brightYellow("Too old blc logs: ${tooOldBLC.size} (before $startDate)"))
        terminal.println(brightYellow("\nExtracting files..."))

        val messageHolderVanilla = mutableSetOf<Pair<String, Boolean>>() // String=Content, Boolean=IS_CHAT_MESSAGE
        val messageHolderBLC = mutableSetOf<Pair<String, Boolean>>() // String=Content, Boolean=IS_CHAT_MESSAGE
        val extractedFiles = AtomicInteger()

        runBlocking(Dispatchers.IO) {
            val mutex = Mutex() // thanks blue
            val chunkedSize = chunksPerCoroutine ?: 200 // 200 is the default value
            val inputStringsChunked = inputStrings.chunked(chunkedSize)
            val blcStringChunked = blcStrings.chunked(chunkedSize)

            suspend fun extractFromChunks(
                chunks: List<List<String>>,
                filePath: String,
                messageHolder: MutableSet<Pair<String, Boolean>>,
                mutex: Mutex
            ) {
                launch {
                    for (chunk in chunks) {
                        launch {
                            for (fileName in chunk) {
                                launch {
                                    val messages = "$filePath$fileName".extractFromPath(fileName)
                                    if (messages != null) {
                                        mutex.withLock {
                                            messageHolder.addAll(messages)
                                        }
                                    }
                                    extractedFiles.incrementAndGet()
                                }
                            }
                        }.join()  // wait until the inner for loop is finished
                    }
                }
            }
            extractFromChunks(inputStringsChunked, actualInputPath, messageHolderVanilla, mutex)
            extractFromChunks(blcStringChunked, actualBLCPath, messageHolderBLC, mutex)
        }

        terminal.println("${brightBlue("Extracted files: ")}${brightCyan("$extractedFiles")}")
        terminal.println("${brightBlue("Extracted messages vanilla logs: ")}${brightCyan("${messageHolderVanilla.size}")}")
        terminal.println("${brightBlue("Extracted messages blc logs: ")}${brightCyan("${messageHolderBLC.size}")}")

        val outputFile = Utils.createFile(actualOutputPath, outputFileName) ?: return

        terminal.println(brightYellow("\nExtracting HGLabor messages..."))
        val hgLaborChatMessagesVanilla = messageHolderVanilla.extractMessagesOnHGLabor()
        terminal.println(brightGreen("Extracted HGLabor messages (vanilla logs): ${hgLaborChatMessagesVanilla.size}"))
        val hgLaborChatMessagesBLC = messageHolderBLC.extractMessagesOnHGLabor()
        terminal.println(brightGreen("Extracted HGLabor messages (blc logs): ${hgLaborChatMessagesBLC.size}"))

        hgLaborChatMessagesVanilla.addAll(hgLaborChatMessagesBLC)

        val hgLaborChatMessages = hgLaborChatMessagesVanilla.toSortedSet()

        terminal.println(brightYellow("\nExtracting corrupted messages..."))
        val corruptedMessages = mutableSetOf<String>()
        for (line in hgLaborChatMessages) {
            val lineDate = line.split(" ")[0]
            if (corruptedLogs.contains(lineDate))
                corruptedMessages.add(line)
        }

        terminal.println(brightRed("Corrupted Files: ${corruptedLogs.size}"))
        terminal.println(brightRed("Corrupted messages: ${corruptedMessages.size}"))

        terminal.println(brightYellow("\nRemoving corrupted messages..."))
        hgLaborChatMessages.removeAll(corruptedMessages)

        terminal.println("${brightBlue("HGLabor messages without corrupted messages: ")}${brightCyan("${hgLaborChatMessages.size}")}")

        terminal.println(brightYellow("\nWriting messages to file..."))
        for (message in hgLaborChatMessages) {
            outputFile.appendText("$message\n")
        }

        terminal.println(brightGreen("Finished! Thanks.\n"))

        terminal.println("\nPress any key to stop the program and close this window...")
        readLine()
    }
}