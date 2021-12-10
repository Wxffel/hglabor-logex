package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

object TerminalMessages {
    fun noFilesFound(path: String) {
        terminal.println(brightRed("ERROR: There are no files in directory $path"))
    }

    fun directoryDoesntExist(path: String) {
        terminal.println(brightRed("ERROR: $path is not a directory"))
    }
}

object Utils {
    fun createFile(outputPath: String, fileName: String): File? {
        val file = File("$outputPath$fileName.txt")
        val isFileCreated = file.createNewFile()

        val overwrite: Boolean

        if (isFileCreated) {
            terminal.println(brightGreen("""File named "$fileName" was created successfully in "$outputPath""""))
            return file
        } else {
            terminal.println(brightRed("""File named "$fileName" already exist in "$outputPath""""))
            terminal.println(brightRed("Do you want to overwrite it?"))
            overwrite = awaitConfirmation()
        }

        if (!overwrite) {
            terminal.println(red("Stopping."))
            return null
        }

        val fileDeleted = file.delete()
        if (fileDeleted) {
            val isCreated = file.createNewFile()

            if (isCreated) {
                terminal.println(brightGreen("""File named "$fileName" was overwritten successfully in "$outputPath""""))
                return file
            }

            terminal.println(brightRed("""Could not create "$fileName" in "$outputPath""""))
            return null
        }

        terminal.println(red("Could not overwrite file (mission permissions?). Stopping."))
        return null
    }

    // stolen from https://github.com/jakobkmar/pacmc/blob/main/src/main/kotlin/net/axay/pacmc/logging/Confirm.kt
    private fun awaitConfirmation(): Boolean {
        print(" (yes / no) ")
        var sure: Boolean? = null
        while (sure == null) {
            sure = when (readLine()) {
                "y", "yes" -> true
                "n", "no", null -> false
                else -> {
                    print("Please type in ${brightGreen("y")}es ${TextStyles.bold("or")} ${brightRed("n")}o: ")
                    null
                }
            }
        }
        return sure
    }
}

// get names of alle the things (files, directories) in this file
fun File.pathContent(): MutableList<String>? {
    val pathContent = list()
    if (pathContent == null) {
        TerminalMessages.directoryDoesntExist(path)
        return null
    } else return when {
        pathContent.isEmpty() -> {
            TerminalMessages.noFilesFound(path)
            return null
        }
        else -> pathContent.toMutableList()
    }
}

// gets the date from se file time
fun FileTime.date() = toString().split("T")[0]

// gets the creation time from an attribute of the file
fun File.creationTimeFromAttr() =
    Files.readAttributes(toPath(), BasicFileAttributes::class.java).creationTime().date()

