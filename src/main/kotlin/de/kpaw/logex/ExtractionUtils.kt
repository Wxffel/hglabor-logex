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
val CURRENT_DATE = LocalDate.now().toString()
val SYSTEM_OS_NAME: String = System.getProperty("os.name")
val USER_HOME: String = System.getProperty("user.home")
val USER_DESKTOP_PATH = Path("$USER_HOME/Desktop")
val BLC_LOGS_PATH = Path("blclient/minecraft")
val MC_LOGS_PATH = if (SYSTEM_OS_NAME == "Windows 10") {
    Path("""$USER_HOME\AppData\Roaming\.minecraft\logs""")
} else Path("$USER_HOME/.minecraft/logs")

// alternative is utf_8, but iso works right for logs
var actualCharset: Charset = StandardCharsets.ISO_8859_1

val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248")
val hgLaborDomains = arrayListOf("hglabor.de", "hglabor.lol", "pvplabor.net")

object LogExPatterns {
    val logNameAsDate = Regex("""\d{4}-[01][0-9]-[0-3][0-9]""")
    val time = Regex("""([012]\d:[0-5]\d:[0-5]\d)""")
    val connecting = Regex(""": (?i)connecting to [a-zA-Z\d.-]*""")
    val stopping = Regex(""": Stopping!""")
    val chatMessage = Regex(""": \[CHAT] (.*)""")
    val privateChatMessages = arrayListOf(
        // minec4ft_name ? to you ?  Das ist eine private Nachricht
        // ? minec4ft_name ? to you ?  Peter Fox
        // ? minec4ft_name ? minec4ft_name ?  787 meine Telefonnummer
        // MSG ? minec4ft_name ? minec4ft_name ?  bist du ein mädchen?????
        // you to ? minec4ft_name ?  Lass das niemandem erzählen!111
        Regex("""(MSG )?(\? )?((\.?\w{3,16})|(you to)) \? ((\.?\w{3,16})|(to you)) \?  (.*)"""),
        // You -> minec4ft_name: Kannst du HG kommen? Ist grad voll leer :(
        // minec4ft_name -> You: Ähm...ich spiele lieber FFA hahaa
        Regex("""((You)|(\.?\w{3,16})) -> ((You)|(\.?\w{3,16})): (.*)"""),
        // You whisper to minec4ft_name: als ob XD
        // You whisper to RANG | minec4ft_name: ok
        // You whisper to ? minec4ft_name: ups
        Regex("""You whisper to ((.+ \| )|(\? ))?(\.?\w{3,16}): (.*)"""),
        // minec4ft_name whispers to you: of course
        // ? minec4ft_name whispers to you: so viele patterns
        // $ minec4ft_name whispers to you: Pirvat Nachricht hehe
        // RANG | minec4ft_name whispers to you: yes
        Regex("""((.+ [|] )|(\? )|(\$ ))?(\.?\w{3,16}) whispers to you: (.*)"""),
        // §d?§r §6you to §f? §minec4ft_name §8?§7  okyyyy bye
        Regex("""§d\?§r §6you to §f\? §7(\.?\w{3,16}) §8\?§7  (.*)"""),
        // §d?§r §minec4ft_name §f? §6to you §8?§7  tricks
        Regex("""§d\?§r §7(\.?\w{3,16}) §f\? §6to you §8\?§7  (.*)"""),
    )
}

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
        if (!pair.second) { // connecting message OR stopping message
            val serverIP = pair.first.split(" ").getOrNull(2) ?: continue
            lastHostWasHGLabor = serverIP.isHGLaborIP() // a stopping message will intentionally return "false"
        } else if (lastHostWasHGLabor) { // a message sent on hglabor
            hgLaborMessages.add(pair.first)
        }
    }

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
    return bufferedReader(actualCharset).extractMessages(date)
}

/**
 * Extracts messages from a gzip file
 */

private fun File.extractGZipFile(): MutableSet<Pair<String, Boolean>> {
    val date = LogExPatterns.logNameAsDate.find(name)?.value ?: creationTimeFromAttr()
    return GZIPInputStream(inputStream()).bufferedReader(actualCharset).extractMessages(date)
}

/**
 * Extracts messages from a zip file
 */

private fun ZipFile.extractZipFile(): MutableSet<Pair<String, Boolean>> {
    val entry = entries().toList().first() // getting the first entry, should be the log
    val date = LogExPatterns.logNameAsDate.find(name)?.value ?: entry.creationTime.date()
    return getInputStream(entry).bufferedReader(actualCharset).extractMessages(date)
}

/**
 * Extracts messages from a buffered reader e.g.
 * from files, zip files and gzip files.
 */

val corruptedLogs = mutableSetOf<String>()
private fun BufferedReader.extractMessages(date: String): MutableSet<Pair<String, Boolean>> {
    val messages = hashSetOf<Pair<String, Boolean>>()

    /**
     * THIS LITTLE DUDE DETECTS A CORRUPTED LOG
     * A LOG IS CORRUPTED (FOR ME AT LEAST) IF THE TIMESTAMP AFTER THE CURRENT MESSAGE
     * IS LOWER/BEFORE/SMALLER THAN THE TIMESTAMP OF THE CURRENT MESSAGE
     * THIS IS ESSENTIAL
     */

    var lastTimeStamp = "-1"
    var corrupted = false

    forEachLine { line ->

        if (corrupted)
            return@forEachLine

        val messagePair = line.extract(date) ?: return@forEachLine
        val timeStamp = messagePair.first.split(" ")[1]

        // only executed ones - sets the first timestamp
        if (lastTimeStamp == "-1") {
            lastTimeStamp = timeStamp
        }

        if (timeStamp < lastTimeStamp) {
            terminal.println(TextColors.red("FILE CORRUPTED: fileDate=$date message=${messagePair.first}"))
            corrupted = true
            return@forEachLine
        } else {
            lastTimeStamp = timeStamp
            messages.add(messagePair)
        }
    }

    return if (corrupted) {
        corruptedLogs.add(date)
        mutableSetOf()
    } else messages
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
        val connectingMessage = LogExPatterns.connecting.find(this)?.value
        if (connectingMessage != null) {
            var serverIP = connectingMessage.substring(16)
            if (serverIP.endsWith('.')) serverIP = serverIP.dropLast(1)
            return "$date $time $serverIP" to false
        }
        // a stopping-message indicates the end of a log, at such messages we will later reset the "last-ip" boolean
        val stoppingMessage = LogExPatterns.stopping.find(this)?.value ?: return null
        return "$date $time ${stoppingMessage.drop(2)}" to false
    }

    // removes ": [CHAT] " so only the message content is left
    val chatMessage = message.drop(9)

    if (chatMessage.isHGLaborPrivateMessage()) // MSG
        return null // private messages are not included in the extracted messages, so null is returned

    return "$date $time $chatMessage" to true
}
