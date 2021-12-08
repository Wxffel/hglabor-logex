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
    var message = this.drop(9) // get rid of ": [CHAT] " now there is only the message content
    if (message.startsWith("? ")) message = message.drop(2)

    // now they are modified to:
    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    // (ffa) $minecraftName » (.*)

    // "(ffa) $minecraftName » (.*)" gets possibly edited to "$minecraftName » (.*)"
    if (message.firstOrNull() == '(') message = message.substring(message.indexOf(" ") + 1)

    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    return message
}

/**
 * That is the final station
 * it checks if a message is sent on a specific server(-ip)
 * here it returns messages sent on "hglabor" and its ips
 */

fun HashSet<Pair<String, Boolean>>.extractHGLaborMessages(): MutableSet<String> {
    val hgLaborChatMessages = mutableSetOf<String>()
    var lastHostWasHGLabor = false

    val sortedSet = this.toSortedSet(compareBy { it.first })

    val notNull = this.filter { true }
    val sorted = notNull.sortedBy { it.first }

    println("original size=" + this.size)
    println("notNull size=" + notNull.size)
    println("sorted set size=" + sortedSet.size)
    println("sorted list size=" + sorted.size)

    // 2020-05-13 19:20:30 mc.hypixel.net isHGLaborIP=false

    for (pair in sorted) {

        if (!pair.second) { // connecting message
            val serverIP = pair.first.split(" ").getOrNull(2)

            if (serverIP != null) {
                lastHostWasHGLabor = serverIP.isHGLaborIP()
                hgLaborChatMessages.add("${pair.first} isHGLaborIP=$lastHostWasHGLabor")
                continue
            } else {
                hgLaborChatMessages.add("${pair.first} NULL")
                continue
            }
        }

        // val playerName = it.first.split(" ", limit = 4)[2]
        hgLaborChatMessages.add("${pair.first} onLabor=$lastHostWasHGLabor")
    }
    terminal.println(TextColors.brightGreen("\nExtracted HGLabor Messages: ${hgLaborChatMessages.size}"))
    return hgLaborChatMessages
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
        // zip file
        "zip" -> ZipFile(this).extractZipFile()
        // gzip file
        "gz" -> file.extractGZipFile()
        // file
        "log" -> file.extractFile()
        // other, gets discarded
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
    val messages = mutableSetOf<Pair<String, Boolean>>()

    this.forEachLine { line ->
        val messagePair = line.extract(date) ?: return@forEachLine
        messages.add(messagePair)
    }

    return messages
}

/**
 * Trys to extract a valid message from a string
 * this is the core function, it is used to extract
 * messages from a buffered reader
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

private fun String.extract(date: String): Pair<String, Boolean> {
    if (this.isBlank() || this.length < 24) return Pair("-1", false)

    val time = LogExPatterns.time.find(this)?.value
    val message = LogExPatterns.allChatMessages.find(this)?.value

    // connecting-messages
    if (message == null) {
        val connectingMessage = LogExPatterns.connecting.find(this)?.value ?: return Pair("-1", false)
        return "$date $time $connectingMessage" to false
    }

    return "$date $time $message" to true
}
