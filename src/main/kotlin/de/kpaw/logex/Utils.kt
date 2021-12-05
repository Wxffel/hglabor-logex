package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

const val messageDelimiter = ">"
const val hgLaborStartDate = "2020-01-01-1"

val defaultCharset: Charset = StandardCharsets.UTF_8

val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248")
val hgLaborDomains = arrayListOf("hglabor", "pvplabor")

// these are "names" used by gamemodes and more
val blacklistedNames = arrayListOf(
    "duels", "uhc", "gewinner", "verlierer", "woodcutting", "knockout",
    "thearchon", "potato", "admin", "spieler", "fisch", "console", "skyblock"
)

object TerminalMessages {
    fun noFilesFound(path: String) {
        terminal.println(TextColors.brightRed("ERROR: There are no files in directory $path"))
    }
}

object Utils {
    fun createFile(outputPath: String, fileName: String): File? {
        val file = File("$outputPath$fileName.txt")
        val isFileCreated = file.createNewFile()

        return if (isFileCreated) {
            println("File named \"$fileName\" was created successfully in $outputPath")
            file
        } else {
            println("File named \"$fileName\" already exist in $outputPath")
            println("Do you want to overwrite it?")
            if (awaitConfirmation()) {
                when (file.delete()) {
                    true -> {
                        val isCreated = file.createNewFile()
                        if (isCreated) {
                            println("File named \"$fileName\" was overwritten successfully in $outputPath")
                            file
                        } else {
                            println("Could not create \"$fileName\" in $outputPath")
                            null
                        }
                    }
                    false -> {
                        println("Could not overwrite file. Stopping.")
                        null
                    }
                }
            } else {
                println("Stopping.")
                null
            }
        }
    }

    private fun awaitConfirmation(): Boolean {
        print(" (yes / no) ")
        var sure: Boolean? = null
        while (sure == null) {
            sure = when (readLine()) {
                "y", "yes" -> true
                "n", "no", null -> false
                else -> {
                    print("Please type in yes or no: ")
                    null
                }
            }
        }
        return sure
    }
}

fun String.toCharset(): Charset = Charset.forName(this)

fun String.isCreationDateFromAttrNeeded() = when {
    this.contains("debug") -> true
    this == "debug" -> true
    this == "latest" -> true
    else -> false
}