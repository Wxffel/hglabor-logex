package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

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

    // changing charset is not yet supported
    private val charset by option(
        "-cs", "--charset",
        help = "Specifies the charset which will be used to read the files [NOT YET SUPPORTED]"
    ).default("UTF_8") // alternative: ISO_8859_1

    override fun run() = extract()

    private fun extract() {
        terminal.println(TextColors.brightMagenta("inputPath=$inputPath/"))
        terminal.println(TextColors.brightMagenta("outputPath=$outputPath/"))

        val inputStrings = File("$inputPath/").pathContent() ?: return
        val blcStrings: MutableList<String> =
            if (inputStrings.contains("blclient"))
                File("$inputPath$blcPath").pathContent() ?: mutableListOf()
            else mutableListOf()

        inputStrings.remove("blclient")

        terminal.println(TextColors.brightYellow("Found ${inputStrings.size} files/directories in $inputPath"))
        terminal.println(TextColors.brightYellow("Found ${blcStrings.size} files/directories in $inputPath$blcPath"))

        // filter files which are too old
        val tooOld = inputStrings.filter { it.split('.')[0] < "$startDate-1" }
        val tooOldBLC = blcStrings.filter { it.split('.')[0] < "$startDate-1" }

        inputStrings.removeAll(tooOld)
        blcStrings.removeAll(tooOldBLC)

        terminal.println(TextColors.brightCyan("${tooOld.size} files and ${tooOldBLC.size} BLC files were too old (before $startDate)"))

        val messageHolder = mutableSetOf<Pair<String, Boolean>>() // String=Content, Boolean=IS_CHAT_MESSAGE
        var processedFiles = 0

        runBlocking(Dispatchers.IO) {
/*            val inputStringsChunked = inputStrings.chunked(200)
            val blcStringChunked = blcStrings.chunked(200)

            // extracting inputStrings
            launch {
                for (inputStringChunk in inputStringsChunked) {
                    launch {
                        for (fileName in inputStringChunk) {
                            launch {
                                val filePath = "$inputPath/$fileName"
                                val messages = filePath.extractFromPath(fileName)
                                if (messages != null)
                                    messageHolder.addAll(messages)
                                processedFiles++
                            }
                        }
                    }.join()  // wait until the inner for loop is finished
                }
            }*/

            for (fileName in inputStrings) {
                launch {
                    val filePath = "$inputPath/$fileName"
                    val messages = filePath.extractFromPath(fileName)
                    if (messages != null)
                        messageHolder.addAll(messages)
                    processedFiles++
                }
            }

/*            for (fileName in blcStrings) {
                launch {
                    val filePath = "$inputPath$blcPath/$fileName"
                    val messages = filePath.extractFromPath(fileName)
                    if (messages != null)
                        messageHolder.addAll(messages)
                    processedFiles++
                }
            }*/

            // extracting blcStrings
/*            launch {
                for (inputStringChunk in blcStringChunked) {
                    launch {
                        for (fileName in inputStringChunk) {
                            launch {
                                val filePath = "$inputPath$blcPath/$fileName"
                                val messages = filePath.extractFromPath(fileName)
                                if (messages != null)
                                    messageHolder.addAll(messages)
                                processedFiles++
                            }
                        }
                    }.join()  // wait until the inner for loop is finished
                }
            }*/
        }

        terminal.println(TextColors.brightMagenta("Processed $processedFiles files"))
        //val hgLaborChatMessages = messageHolder.extractHGLaborMessages()
        //val outputFile = Utils.createFile("$outputPath/", outputFileName) ?: return
        //hgLaborChatMessages.toSortedSet().forEach { outputFile.appendText(it + "\n") }

        val notNullList = mutableListOf<Pair<String, Boolean>>()
        val nullList = mutableListOf<Pair<String, Boolean>>()

        for (pair in messageHolder) {
            if (pair != null) {
                if (pair.first != null && pair.second != null) {
                    notNullList.add(pair)
                } else nullList.add(pair)
            } else nullList.add(pair)
        }

        val notNullListSorted = notNullList.sortedBy { it.first }

        val sorted = messageHolder.sortedBy { it.first }
        terminal.println(TextColors.cyan("sorted=${sorted.size}"))

        terminal.println(TextColors.brightRed("messageHolderSize=${messageHolder.size}"))
        terminal.println(TextColors.brightRed("messageHolderNotNullSize=${notNullList.size}"))
        terminal.println(TextColors.brightRed("messageHolderNotNullSortedSize=${notNullListSorted.size}"))
        terminal.println(TextColors.brightRed("messageHolderNullList=${nullList.size}"))
    }
}