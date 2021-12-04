package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import de.kpaw.logex.Utils
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

object CombineLogs : CliktCommand(
    help = "Combines two txts"
) {
    private val inputPath by argument(help = "The folder containing the files to be combined").path(
        mustExist = true, canBeFile = false
    )

    private val outputPath by argument(help = "The folder containing the file with the result").path(
        mustExist = true, canBeFile = false
    )

    private val outputFileName by argument(help = "This file will contain the result")

    override fun run() {
        val filenames = File("$inputPath/").list() ?: kotlin.run {
            println("Error! There are no files in $inputPath/")
            return
        }

        val inputFiles = (filenames as Array<*>).map { File("$inputPath/$it") }
            .filter { file -> file.isFile }.toMutableList()

        val outputFile = Utils.createFile("$outputPath/", outputFileName) ?: return
        val allLines = hashSetOf<String>()

        runBlocking {
            inputFiles.forEach { file ->
                launch {
                    file.useLines { line -> line.forEach { allLines.add(it) } }
                }
            }
        }
        allLines.toSortedSet().forEach { outputFile.appendText("$it\n") }
    }
}