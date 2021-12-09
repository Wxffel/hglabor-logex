package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
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
import java.util.concurrent.atomic.AtomicInteger

object Extract : CliktCommand(
    help = "Extracts specified lines of files"
) {
    private val inputPath by argument(help = "The path of the folder to extract lines from").path(
        mustExist = true, canBeFile = false
    ).default(MC_LOGS_PATH)

    private val outputPath by argument(help = "The path where to put the output file").path(
        mustExist = true, canBeFile = false
    ).default(USER_DESKTOP_PATH)

    private val outputFileName by option(
        "-of", "--outputfilename",
        help = "The name of the file with the extracted lines"
    ).default("HGLaborMessages_$CURRENT_DATE")

    private val startDate by option(
        "-sd", "--startdate",
        help = "Only extracts files after the start date (yyyy-mm-dd) ansonsten gl haha"
    ).default(HGLABOR_START_DATE)

    private val chunksPerCoroutine by option(
        "-cpc", "-chunkspercoroutine",
        help = "Amount of files one coroutine processes, default: 200." +
                "Higher number = less coroutines = slower extracting, but resources are saved"
    ).int().check("Must >= 1") { it >= 1 }

    override fun run() = extract()

    private fun extract() {

        val actualInputPath = "$inputPath$DIR_DELIMITER"
        val actualBLCPath = "$inputPath$DIR_DELIMITER$BLC_LOGS_PATH$DIR_DELIMITER"
        val actualOutputPath = "$outputPath$DIR_DELIMITER"

        terminal.println(brightMagenta("inputPath=$actualInputPath"))
        terminal.println(brightMagenta("outputPath=$actualOutputPath"))
        terminal.println(brightMagenta("outputFile=$outputFileName.txt"))
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

        val messageHolder = mutableSetOf<Pair<String, Boolean>>() // String=Content, Boolean=IS_CHAT_MESSAGE
        val extractedFiles = AtomicInteger()

        runBlocking(Dispatchers.IO) {
            val mutex = Mutex() // thanks blue
            val chunkedSize = chunksPerCoroutine ?: 200 // 200 is the default value
            val inputStringsChunked = inputStrings.chunked(chunkedSize)
            val blcStringChunked = blcStrings.chunked(chunkedSize)

            suspend fun extractFromChunks(chunks: List<List<String>>, filePath: String, mutex: Mutex) {
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
            extractFromChunks(inputStringsChunked, actualInputPath, mutex)
            extractFromChunks(blcStringChunked, actualBLCPath, mutex)
        }

        terminal.println("${brightBlue("Extracted files: ")}${brightCyan("$extractedFiles")}")
        terminal.println("${brightBlue("Extracted messages: ")}${brightCyan("${messageHolder.size}")}")

        val outputFile = Utils.createFile(actualOutputPath, outputFileName) ?: return

        terminal.println(brightYellow("\nExtracting HGLabor messages..."))
        val hgLaborChatMessages = messageHolder.extractMessagesOnHGLabor()

        terminal.println(brightYellow("\nWriting messages to file..."))
        for (message in hgLaborChatMessages) {
            outputFile.appendText("$message\n")
        }

        terminal.println(brightGreen("Finished! Thanks."))
    }
}