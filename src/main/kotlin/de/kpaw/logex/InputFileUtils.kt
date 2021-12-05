package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

object InputFileUtils {
    fun inputFilesToMinecraftLogs(path: String, startDate: String = hgLaborStartDate, charset: Charset): List<MinecraftLog> {
        val pathContent = File(path).list() ?: kotlin.run {
            TerminalMessages.noFilesFound(path = "$path/")
            return listOf()
        }

        val pathContentAfterDate = pathContent.toList().filter { it.split('.')[0] >= "$startDate-1.log" }
        val pathContentAsFiles = pathContentAfterDate.map { File("$path$it") }

        // tells one how many files were too old
        val discarded = pathContent.toMutableList()
        discarded.removeAll(pathContentAfterDate)
        terminal.println(TextColors.cyan("Amount of too old files: ${discarded.size}"))

        // decompress files and returns them as MinecraftLogs
        return pathContentAsFiles.mapNotNull {
            when {
                it.name.endsWith(".zip") -> {
                    terminal.println(TextColors.brightBlue("Unzipped file named: ${it.name}"))
                    ZipFile(it).unzip(charset)
                }

                it.name.endsWith("log.gz") -> {
                    terminal.println(TextColors.yellow("G unzipped file named: ${it.name}"))
                    it.gunzip(charset)
                }

                it.name.endsWith(".log") -> {
                    terminal.println(TextColors.brightBlue("End with log: ${it.name}"))
                    MinecraftLog(it.name, it.readLines(charset).toHashSet(), it.creationTimeFromAttr())
                }

                else -> {
                    terminal.println(
                        TextColors.brightRed(
                            """Wrong Type: "${it.name}" in directory "$path" has to be ".zip" or ".log.gz""""
                        )
                    )
                    null
                }
            }
        }
    }
    // gets the creation time from an attribute of the file
    private fun File.creationTimeFromAttr() = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java)
        .creationTime().toString().split("T")[0]

    // unzips files (.zip files)
    private fun ZipFile.unzip(charset: Charset): MinecraftLog {
        val entry = this.entries().toList().first() // getting the first entry, should be the log
        val zipFileBytes = this.getInputStream(entry).readAllBytes()
        val entryContent = String(zipFileBytes, charset).split("\n").toHashSet()
        val creationTime = entry.toString().split("T")[0]
        return MinecraftLog(entry.name, entryContent, creationTime)
    }

    // unzips gzip files (log.gz files)
    private fun File.gunzip(charset: Charset): MinecraftLog {
        val gzipFileBytes = GZIPInputStream(this.inputStream()).readAllBytes()
        val gzipFileContent = String(gzipFileBytes, charset).split("\n").toHashSet()
        return MinecraftLog(this.name, gzipFileContent, this.creationTimeFromAttr())
    }
}