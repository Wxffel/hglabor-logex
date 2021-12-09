package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import java.io.BufferedReader
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.io.path.Path

const val HGLABOR_START_DATE = "2020-01-01-1"

val DEFAULT_CHARSET: Charset = StandardCharsets.ISO_8859_1 // or utf

val CURRENT_DATE = LocalDate.now().toString()
val SYSTEM_OS_NAME: String = System.getProperty("os.name")
val USER_HOME: String = System.getProperty("user.home")

val DIR_DELIMITER = if (SYSTEM_OS_NAME == "Windows 10") {
    """\"""
} else "/"

val USER_DESKTOP_PATH = if (SYSTEM_OS_NAME == "Windows 10") {
    Path("""$USER_HOME\Desktop""")
} else Path("$USER_HOME/Desktop")

val MC_LOGS_PATH = if (SYSTEM_OS_NAME == "Windows 10") {
    Path("""$USER_HOME\AppData\Roaming.minecraft\logs""")
} else Path("$USER_HOME/.minecraft/logs")

val BLC_LOGS_PATH = if (SYSTEM_OS_NAME == "Windows 10") {
    Path("""\blclient\minecraft""")
} else Path("blclient/minecraft")


val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248")
val hgLaborDomains = arrayListOf("hglabor.de", "hglabor.lol", "pvplabor.net")

fun String.isHGLaborIP() = hgLaborDomains.any { lowercase().contains(it) } || hgLaborIPs.contains(this)
fun String.isHGLaborPrivateMessage() = LogExPatterns.privateChatMessages.any { it.matches(this) }

/**
 * Checks if a message is sent on a specific server(-ip).
 * Here it returns messages sent on "hglabor" and its ips.
 */

fun Collection<Pair<String, Boolean>>.extractMessagesOnHGLabor(): MutableSet<String> {
    val hgLaborMessages = mutableSetOf<String>()
    val sortedSet = toSortedSet(compareBy { it.first })
    var lastHostWasHGLabor = false

    for (pair in sortedSet) {
        if (!pair.second) { // connecting message
            val serverIP = pair.first.split(" ").getOrNull(2) ?: continue
            lastHostWasHGLabor = serverIP.isHGLaborIP()
        } else if (lastHostWasHGLabor) { // a message sent on hglabor
            hgLaborMessages.add(pair.first)
        }
    }

    terminal.println(TextColors.brightGreen("Extracted HGLabor messages: ${hgLaborMessages.size}"))
    return hgLaborMessages
}

/**
 * Detects file type and extracts it with the appropriate function
 */

fun String.extractFromPath(fileName: String): MutableSet<Pair<String, Boolean>>? {
    val file = File(this)
    return when (file.extension) {
        "gz" -> file.extractGZipFile()
        "log" -> file.extractFile()
        "zip" -> ZipFile(this).extractZipFile()
        else -> {
            terminal.println(TextColors.brightRed("""Wrong file type "${file.extension}" file="$fileName" directory="$this""""))
            null
        }
    }
}

/**
 * Extracts messages from a file
 */

private fun File.extractFile(): MutableSet<Pair<String, Boolean>> {
    val date = LogExPatterns.logNameAsDate.find(name)?.value ?: creationTimeFromAttr()
    return bufferedReader(DEFAULT_CHARSET).extractMessages(date)
}

/**
 * Extracts messages from a gzip file
 */

private fun File.extractGZipFile(): MutableSet<Pair<String, Boolean>> {
    val date = LogExPatterns.logNameAsDate.find(name)?.value ?: creationTimeFromAttr()
    return GZIPInputStream(inputStream()).bufferedReader(DEFAULT_CHARSET).extractMessages(date)
}

/**
 * Extracts messages from a zip file
 */

private fun ZipFile.extractZipFile(): MutableSet<Pair<String, Boolean>> {
    val entry = entries().toList().first() // getting the first entry, should be the log
    val date = LogExPatterns.logNameAsDate.find(name)?.value ?: entry.creationTime.date()
    return getInputStream(entry).bufferedReader(DEFAULT_CHARSET).extractMessages(date)
}

/**
 * Extracts messages from a buffered reader e.g.
 * from files, zip files and gzip files.
 */

private fun BufferedReader.extractMessages(date: String): MutableSet<Pair<String, Boolean>> {
    val messages = hashSetOf<Pair<String, Boolean>>()

    forEachLine { line ->
        val messagePair = line.extract(date) ?: return@forEachLine
        messages.add(messagePair)
    }

    return messages
}

/**
 * Extracts all chat messages and connection messages.
 */

private fun String.extract(date: String): Pair<String, Boolean>? {
    if (isBlank() || isEmpty() || length < 24) return null

    val time = LogExPatterns.time.find(this)?.value // should never be null
    val message = LogExPatterns.chatMessage.find(this)?.value

    // connecting-message
    if (message == null) {
        val connectingMessage = LogExPatterns.connecting.find(this)?.value ?: return null
        var serverIP = connectingMessage.substring(16)
        if (serverIP.endsWith('.')) serverIP = serverIP.dropLast(1)
        return "$date $time $serverIP" to false
    }

    // removes ": [CHAT] " so only the message content is left
    val chatMessage = message.drop(9)

    if (chatMessage.isHGLaborPrivateMessage()) // MSG
        return null // private messages are not included in the extracted messages, so null is returned

    return "$date $time $chatMessage" to true
}
