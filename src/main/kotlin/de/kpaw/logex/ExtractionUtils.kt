package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.blacklistedNames
import de.kpaw.logex.hgLaborDomains
import de.kpaw.logex.hgLaborIPs
import de.kpaw.logex.terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

const val messageDelimiter = ">"
const val hgLaborStartDate = "2020-01-01-1"
const val blcPath = "/blclient/minecraft/"

val defaultCharset: Charset = StandardCharsets.ISO_8859_1

val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248") // 2020-03-01 16:44:41 178.32.80.96 NULL
val hgLaborDomains = arrayListOf("hglabor", "pvplabor")

// these are "names" used by gamemodes and more
val blacklistedNames = arrayListOf(
    "duels", "uhc", "gewinner", "verlierer", "woodcutting", "knockout",
    "thearchon", "potato", "admin", "spieler", "fisch", "console", "skyblock"
)

fun String.isBlacklistedName() = blacklistedNames.contains(this.lowercase())

fun String.isHGLaborIP() = hgLaborDomains.any { this.lowercase().contains(it) } || hgLaborIPs.contains(this)

fun String.isSurvivalMessage() = LogExPatterns.vanillaChatMessage.matches(this)

fun String.isHGLaborPrivateMessage() = LogExPatterns.privateChatMessage.matches(this)

// $minecraftName » (.*)
// $minecraftName ? (.*)
// ? $minecraftName » (.*)
// ? $minecraftName ? (.*)
// ? (ffa) $minecraftName » (.*)
fun String.conditioning(): String {
    var message = this
    // if (message.startsWith("? ")) message = message.drop(2)

    // now they are modified to:
    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    // (ffa) $minecraftName » (.*)

    // "(ffa) $minecraftName » (.*)" gets possibly edited to "$minecraftName » (.*)"
    // if (message.firstOrNull() == '(') message = message.substring(message.indexOf(" ") + 1)

    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    return message
}

/**
 * That is the final station
 * it checks if a message is sent on a specific server(-ip)
 * here it returns messages sent on "hglabor" and its ips
 */

fun Collection<Pair<String, Boolean>>.extractHGLaborMessages(): MutableSet<String> {
    val hgLaborMessages = mutableSetOf<String>()
    val sortedSet = this.toSortedSet(compareBy { it.first })
    var lastHostWasHGLabor = false

    for (pair in sortedSet) {
        if (!pair.second) { // connecting message
            val serverIP = pair.first.split(" ").getOrNull(2) ?: continue
            lastHostWasHGLabor = serverIP.isHGLaborIP()
        } else if (lastHostWasHGLabor) { // other messages on hglabor
            hgLaborMessages.add(pair.first)
        }
    }

    terminal.println(TextColors.brightGreen("\nExtracted HGLabor Messages: ${hgLaborMessages.size}"))
    return hgLaborMessages
}

/**
 * Detects file type and extracts
 * it with the appropriate function
 * this is the "highest" function
 * it implements many other extraction functions
 */

fun String.extractFromPath(fileName: String): MutableSet<Pair<String, Boolean>>? {
    val file = File(this)
    return when (file.extension) {
        "zip" -> ZipFile(this).extractZipFile()
        "gz" -> file.extractGZipFile()
        "log" -> file.extractFile()
        else -> {
            terminal.println(TextColors.brightRed("""Wrong file type "${file.extension} file="$fileName" directory="$this""""))
            null
        }
    }
}

/**
 * Extracts messages from a file
 */

private fun File.extractFile(): MutableSet<Pair<String, Boolean>> {
    val date = if (this.name.isCreationDateFromAttrNeeded())
        this.creationTimeFromAttr()
    else this.name.creationTimeFromName()
    return this.bufferedReader(defaultCharset).extractMessages(date)
}

/**
 * Extracts messages from a gzip file
 */

private fun File.extractGZipFile(): MutableSet<Pair<String, Boolean>> {
    val date = if (this.name.isCreationDateFromAttrNeeded())
        this.creationTimeFromAttr()
    else this.name.creationTimeFromName()
    return GZIPInputStream(this.inputStream()).bufferedReader(defaultCharset).extractMessages(date)
}

/**
 * Extracts messages from a zip file
 */

private fun ZipFile.extractZipFile(): MutableSet<Pair<String, Boolean>> {
    val entry = this.entries().toList().first() // getting the first entry, should be the log
    val name = this.name.substringAfterLast('/', "")
    val date = if (name.isCreationDateFromAttrNeeded())
        entry.creationTime.date()
    else name.creationTimeFromName()
    return this.getInputStream(entry).bufferedReader(defaultCharset).extractMessages(date)
}

/**
 * Extracts messages from a buffered reader
 * it is used to extract files, zip files and gzip files
 */

private fun BufferedReader.extractMessages(date: String): MutableSet<Pair<String, Boolean>> {
    val messages = hashSetOf<Pair<String, Boolean>>()

    this.forEachLine { line ->
        val messagePair = line.extract(date) ?: return@forEachLine
        messages.add(messagePair)
    }

    return messages
}

/**
 * Trys to extract a valid public hglabor message from a string
 */

/*private fun String.extract(date: String): Pair<String, Boolean>? {
    if (this.isBlank() || this.length < 24) return null

    val time = LogExPatterns.time.find(this)?.value
    val message = LogExPatterns.hgLaborChatMessage.find(this)?.value

    // connecting-messages
    if (message == null) {
        val connectingMessage = LogExPatterns.connecting
            .find(this)?.value ?: return null
        // : Connecting to server.hglabor.de.
        var serverIP = connectingMessage.substring(16)
        if (serverIP.endsWith('.'))
            serverIP = serverIP.dropLast(1)

        return "$date $time $serverIP" to false
    }

    var chatMessage = message.conditioning()

    if (message.isHGLaborPrivateMessage()) {
        return "$date $time $chatMessage MSG" to true
    }

    if (chatMessage.isSurvivalMessage()) {
        // replaces "<" and ">" before and after the minecraft name, also a delimiter is added
        // todo: to conditioning/extract HGLabor messages
        chatMessage = chatMessage.replaceFirst("<", "")
            .replaceFirst(">", " $messageDelimiter")
        return "$date $time $chatMessage" to true
    }

    // replaces "?" or "»" with the formattedMessageDelimiter ">"
    val index = chatMessage.indexOf(' ') + 1
    chatMessage = chatMessage.replaceRange(index..index, messageDelimiter)
    // todo: to conditioning/extract HGLabor messages
    return "$date $time $chatMessage" to true
}*/

/**
 * Extracts all chat messages and connection messages
 */

private fun String.extract(date: String): Pair<String, Boolean>? {
    if (this.isBlank() || this.length < 24) return null

    val time = LogExPatterns.time.find(this)?.value
    val message = LogExPatterns.chatMessage.find(this)?.value

    // connecting-message
    if (message == null) {
        val connectingMessage = LogExPatterns.connecting.find(this)?.value ?: return null
        var serverIP = connectingMessage.substring(16)
        if (serverIP.endsWith('.')) serverIP = serverIP.dropLast(1)
        return "$date $time $serverIP" to false
    }

    val chatMessage = message.drop(9) // removes ": [CHAT] ", only the message content is left
    return "$date $time $chatMessage" to true
}
