package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.Utils
import de.kpaw.logex.terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

object Difference : CliktCommand(
    help = "Difference between two files"
) {
    private val inputPath1 by argument(help = "First file path (WITH MORE LINES)")
    private val inputPath2 by argument(help = "Second file path (WITH LESS LINES)")
    private val outputPath by argument(help = "Where to output the file")
    private val outputFileName by argument(help = "This file will contain the result")

    override fun run() {

        val file1 = File(inputPath1)
        val file2 = File(inputPath2)

        val file1Lines = file1.readLines()
        val file2Lines = file2.readLines()

        val outputFile = Utils.createFile(outputPath, outputFileName) ?: kotlin.run {
            terminal.println(TextColors.red("outputfile bug!"))
            return
        }

        terminal.println(TextColors.cyan("file1=${file1Lines.size} file2=${file2Lines.size}"))

        val differenceList = mutableListOf<String>()

        var i = 0
        runBlocking(Dispatchers.IO) {
            val chunked = file1Lines.chunked(20000)
            println("Chunking finished.")
            for (chunk in chunked) {
                launch {
                    for (line in chunk) {
                        if (file2Lines.contains(line))
                            continue
                        else differenceList.add(line)
                    }
                }
                i++
                println("Launched Coroutine ($i)")
            }
        }

        terminal.println(TextColors.brightGreen("${differenceList.size} lines difference"))

        differenceList.forEach { outputFile.appendText("$it\n") }
        println("Fertig.")
        return
    }
}