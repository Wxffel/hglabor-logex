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

val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248")
val hgLaborDomains = arrayListOf("hglabor", "pvplabor")

// these are "names" used by gamemodes and more
val blacklistedNames = arrayListOf(
    "duels", "uhc", "gewinner", "verlierer", "woodcutting", "knockout",
    "thearchon", "potato", "admin", "spieler", "fisch", "console", "skyblock"
)

// LOG PATTERNS:

// DEF: Default, Vanilla
// BLC: Badlion Client
// IDK: Possibly Optifine

// DEF  [23:36:11] [main/INFO]: [CHAT] <msg>
// BLC  [11:07:24] [Client thread/INFO]: [CHAT] <msg>
// IDK  [26Jun2021 18:22:21.883] [Render thread/INFO] [net.minecraft.client.gui.NewChatGui/]: [CHAT] <msg>

// DEF  [12:07:12] [main/INFO]: Connecting to tcpshield.hglabor.de., 25565
// IDK  [29Jun2021 21:10:20.240] [Render thread/INFO] [net.minecraft.client.gui.screen.ConnectingScreen/]: Connecting to hgbuild.gq, 25565
// BLC  [15:59:15] [Render thread/INFO]: Connecting to hgbuild.gq, 25565 // these are attempts
// BLC  [15:59:17] [Render thread/INFO]: Worker done, connecting to hgbuild.gq, 25565 // this is the final connection
// BLC  [10:43:08] [Client thread/INFO]: Connecting to mc.hypixel.net, 25565

// MINECRAFT HGLABOR PATTERNS:
// Public:
// $minecraftName » (.*)
// $minecraftName ? (.*)
// ? $minecraftName » (.*)
// ? $minecraftName ? (.*)
// ? (ffa) $minecraftName » (.*)

// Msg:
// $minecraftName ? to you ?  (.*)
// you to ? $minecraftName ?  (.*)
// You -> $minecraftName: (.*)
// $minecraftName -> You: (.*)
// MSG ? $minecraftName ? $minecraftName ?  (.*)
// ? $minecraftName ? $minecraftName ?  (.*)

// Vanilla Survival
// <$minecraftName> (.*)

object LogExRegex {
    val minecraftNamePattern = Regex("\\w{3,16}")
    val timePattern = Regex("""([012]\d:[0-5]\d:[0-5]\d)""")
    val messagePattern = Regex(""": \[CHAT] (.*)""")
    val connectingPattern = Regex("""(?i)connecting to [\w\d.]*""")
    val survivalMsgPattern = Regex("<$minecraftNamePattern> (.*)")
    val msgRegexes = arrayListOf(
        Regex("\\? you to \\? $minecraftNamePattern \\?  (.*)"),
        Regex("$minecraftNamePattern \\? to you \\?  (.*)"),
        Regex("\\? $minecraftNamePattern \\? $minecraftNamePattern \\?  (.*)"),
        Regex("MSG \\? $minecraftNamePattern \\? $minecraftNamePattern \\?  (.*)"),
        Regex("\\? $minecraftNamePattern \\? to you \\?  (.*)")
    )
}

fun String.isBlacklistedName() = blacklistedNames.contains(this.lowercase())

fun String.isValidMinecraftName() = this.matches(LogExRegex.minecraftNamePattern)

fun String.isHGLaborIP() = hgLaborDomains.any { this.lowercase().contains(it) } || hgLaborIPs.contains(this)

fun String.isSurvivalMessage() = LogExRegex.survivalMsgPattern.matches(this)

fun String.isHGLaborPrivateMessage() = LogExRegex.msgRegexes.any { it.matches(this) }

fun String.isPossiblyHGLaborPublicMessage(): Boolean {
    val splitMessage = this.split(" ", limit = 3)

    var name = splitMessage.getOrNull(0) ?: return false
    val delimiter = splitMessage.getOrNull(1) ?: return false

    if (name.startsWith("."))
        name = name.drop(1) // dot "." could be hglabor bedrock marker

    if ((delimiter == "?" || delimiter == "»") && name.isValidMinecraftName() && !name.isBlacklistedName())
        return true

    return false
}

// $minecraftName » (.*)
// $minecraftName ? (.*)
// ? $minecraftName » (.*)
// ? $minecraftName ? (.*)
// ? (ffa) $minecraftName » (.*)
fun String.conditioning(): String {
    var message = this
    if (message.startsWith("? ")) message = message.drop(2)
    // now they are modified to:
    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    // (ffa) $minecraftName » (.*)

    // "(ffa) $minecraftName » (.*)" gets possibly edited to "$minecraftName » (.*)"
    if (message.firstOrNull() == '(') message = message.substring(message.indexOf(" "))

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
    this.toSortedSet(compareBy { it.first }).forEach {
        val messageSplit = it.first.split(" ", limit = 4)
        val nameOrIP = messageSplit[2]

        if (!nameOrIP.isValidMinecraftName()) // is not a valid minecraft name
            lastHostWasHGLabor = nameOrIP.isHGLaborIP() // so it could be (must be) an ip address
        else if (lastHostWasHGLabor) hgLaborChatMessages.add(it.first)
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
        "zip" -> {
            val messages = ZipFile(this).extractZipFile()
            println(messages.firstOrNull().toString())
            messages
        }
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

private fun String.extract(date: String): Pair<String, Boolean>? {
    if (this.isBlank() || this.length < 24) return null

    val time = LogExRegex.timePattern.find(this)?.value
    val message = LogExRegex.messagePattern.find(this)?.value

    // connecting-messages
    if (message == null) {
        // e.g. [12:07:12] [main/INFO]: Connecting to tcpshield.hglabor.de., 25565
        // if null -> not a chat message nor a connecting message -> continue
        val connectingMessage = LogExRegex.connectingPattern
            .find(this)?.value ?: return null
        // Connecting to server.hglabor.de., 25751
        var serverIP = connectingMessage.substring(14)
        if (serverIP.endsWith('.'))
            serverIP = serverIP.dropLast(1)

        return "$date $time $serverIP" to false
    }

    var chatMessage =
        message.drop(9) // get rid of ": [CHAT] " now there is only the message content

    // used for charset debugging lol
    // terminal.println(TextColors.white(chatMessage))

    if (chatMessage.isHGLaborPrivateMessage()) return null

    chatMessage = chatMessage.conditioning()

    if (chatMessage.isPossiblyHGLaborPublicMessage()) {
        // replaces "?" or "»" with the formattedMessageDelimiter ">"
        val index = chatMessage.indexOf(' ') + 1
        chatMessage = chatMessage.replaceRange(index..index, messageDelimiter)
        return "$date $time $chatMessage" to true
    } else if (chatMessage.isSurvivalMessage()) {
        // replaces "<" and ">" before and after the minecraft name, also a delimiter is added
        chatMessage = chatMessage.replaceFirst("<", "")
            .replaceFirst(">", " $messageDelimiter")

        return "$date $time $chatMessage" to true
    }
    return null
}
