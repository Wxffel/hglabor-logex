package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.commands.Extract
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

object TerminalMessages {
    fun noFilesFound(path: String) {
        terminal.println(TextColors.brightRed("ERROR: There are no files in directory $path"))
    }

    fun directoryDoesntExist(path: String) {
        terminal.println(TextColors.brightRed("ERROR: $path is not a directory"))
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

// get names of alle the things (files, directories) in this file
fun File.pathContent(): MutableList<String>? {
    val pathContent = this.list()
    if (pathContent == null) {
        TerminalMessages.directoryDoesntExist(path = "$path/")
        return null
    } else return when {
        pathContent.isEmpty() -> {
            TerminalMessages.noFilesFound(path = "$path/")
            return null
        }
        else -> pathContent.toMutableList()
    }
}

// gets the date from se file time
fun FileTime.date() = this.toString().split("T")[0]

// gets the creation time from an attribute of the file
fun File.creationTimeFromAttr() = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java)
    .creationTime().date()

// gets the creation time from mc log name
fun String.creationTimeFromName() = this.split("-").dropLast(1).joinToString("-")

fun String.isCreationDateFromAttrNeeded() = when {
    this.contains("debug") -> true
    this == "debug" -> true
    this == "latest" -> true
    else -> false
}

fun String.toCharset(): Charset = Charset.forName(this)

// DECOMPRESSING / UNZIPPING
// data class containing the decompressed content and more
data class MinecraftLog(
    val name: String,
    val content: List<String>,
    val creationDateFromAttributes: String
) {
    val creationDateFromName: String = name.creationTimeFromName()
}

// unzips files (.zip files)
fun ZipFile.unzip(charset: Charset): MinecraftLog {
    val entry = this.entries().toList().first() // getting the first entry, should be the log
    val zipFileBytes = this.getInputStream(entry).readAllBytes()
    val entryContent = String(zipFileBytes, charset).split("\n")
    val creationTime = entry.toString().split("T")[0]
    return MinecraftLog(entry.name, entryContent, creationTime)
}

// unzips gzip files (log.gz files)
fun File.gunzip(charset: Charset): MinecraftLog {
    val gzipFileBytes = GZIPInputStream(this.inputStream()).readAllBytes()
    val gzipFileContent = String(gzipFileBytes, charset).split("\n")
    return MinecraftLog(this.name, gzipFileContent, this.creationTimeFromAttr())
}