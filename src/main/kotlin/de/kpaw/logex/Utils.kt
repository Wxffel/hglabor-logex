package de.kpaw.logex

import java.io.File
import java.nio.charset.Charset

const val formattedMessageDelimiter = ">"
const val hgLaborStartDate = "2020"

data class SurvivalMessageHolder(val fileName: String) {
    val connectionMessages = mutableSetOf<String>()
    val survivalMessages = mutableSetOf<String>()
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

    fun awaitConfirmation(): Boolean {
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

    fun awaitContinueAnyways(): Boolean {
        print("Do you want to continue anyways?")
        return awaitConfirmation()
    }
}

// LOG PATTERNS:

// Vanilla [23:36:11] [main/INFO]: [CHAT] <msg>
// BLC     [11:07:24] [Client thread/INFO]: [CHAT] <msg>
// IDK     [26Jun2021 18:22:21.883] [Render thread/INFO] [net.minecraft.client.gui.NewChatGui/]: [CHAT] <msg>
//         [29Jun2021 21:10:20.240] [Render thread/INFO] [net.minecraft.client.gui.screen.ConnectingScreen/]: Connecting..

// MINECRAFT HGLABOR PATTERNS:

// $minecraftName » (.*)
// $minecraftName ? (.*)
// ? $minecraftName » (.*)
// ? $minecraftName ? (.*)
// ? (ffa) $minecraftName » (.*)
// $minecraftName ? to you ?  (.*)
// you to ? $minecraftName ?  (.*)
// You -> $minecraftName: (.*)
// $minecraftName -> You: (.*)
// MSG ? $minecraftName ? $minecraftName ?  (.*)
// ? $minecraftName ? $minecraftName ?  (.*)
// <$minecraftName> (.*) // Note: EXTRACT THIS ONLY ON HGLABOR

// UTILS
// Wenn Charset falsch eingestellt, dann alles rip
val isoCharset: Charset = Charset.forName("ISO8859-1")

val minecraftNameRegex = Regex("\\w{3,16}")
fun String.isValidMinecraftName(): Boolean = this.matches(minecraftNameRegex)

val survivalMsgPattern = Regex("<$minecraftNameRegex> (.*)")
fun String.isSurvivalMessage(): Boolean = survivalMsgPattern.matches(this)

val msgRegexs = arrayListOf(
    Regex("\\? you to \\? $minecraftNameRegex \\?  (.*)"),
    Regex("$minecraftNameRegex \\? to you \\?  (.*)"),
    Regex("\\? $minecraftNameRegex \\? $minecraftNameRegex \\?  (.*)"),
    Regex("MSG \\? $minecraftNameRegex \\? $minecraftNameRegex \\?  (.*)"),
    Regex("\\? $minecraftNameRegex \\? to you \\?  (.*)")
)

fun String.isHGLaborPrivateMessage(): Boolean {
    msgRegexs.forEach { if (it.matches(this)) return true }
    return false
}

fun String.isHGLaborPublicMessage(): Boolean {
    val splitMessage = this.split(" ", limit = 6)

    var name = splitMessage.getOrNull(0) ?: return false
    val delimiter = splitMessage.getOrNull(1) ?: return false

    if (name.startsWith("."))
        name = name.drop(1) // dot "." could be hglabor bedrock marker

    if ((delimiter == "?" || delimiter == "»") && name.isValidMinecraftName() && !name.isBlacklistedName())
        return true

    return false
}

val blacklistedNames = arrayListOf(
    "duels", "uhc", "gewinner", "verlierer", "woodcutting", "knockout",
    "thearchon", "potato", "admin", "spieler", "fisch", "console", "skyblock"
)

fun String.isBlacklistedName(): Boolean = blacklistedNames.contains(this.lowercase())

fun String.conditioning(): String {
    var message = this
    if (message.startsWith("? ")) message = message.drop(2)
    // now they are modified to:
    // $minecraftName » (.*)
    // $minecraftName ? (.*)
    // (ffa) $minecraftName » (.*)

    // "(ffa) $minecraftName » (.*)" gets edited to "$minecraftName » (.*)"
    if (message.firstOrNull() == '(')
        message = message.substring(message.indexOf(" ") + 1)

    return message
}

fun extractHGLaborSurvivalMessages(survivalMessageFiles: MutableMap<String, SurvivalMessageHolder>): MutableSet<String> {
    val connectionAndSurvivalMessages = mutableSetOf<String>()
    val hgLaborSurvivalMessages = mutableSetOf<String>()

    survivalMessageFiles.forEach { (_, survivalMessageHolder) ->
        survivalMessageHolder.survivalMessages.forEach { connectionAndSurvivalMessages.add(it) }
        survivalMessageHolder.connectionMessages.forEach { connectionAndSurvivalMessages.add(it) }
    }

    // Extracts ONLY messages send on a specific server (ip)
    var lastHostWasHGLabor = false
    connectionAndSurvivalMessages.toSortedSet().forEach {
        val messageSplit = it.split(" ")
        val nameOrIP = messageSplit[2]
        if (nameOrIP[0] != '<')
            lastHostWasHGLabor = nameOrIP.isHGLaborIP() || nameOrIP.lowercase().contains("axay")
        else if (lastHostWasHGLabor) hgLaborSurvivalMessages.add(it)
    }
    println("\nHGLabor Survival Messages: ${hgLaborSurvivalMessages.size}")
    return hgLaborSurvivalMessages
}

val hgLaborIPs = arrayListOf("178.32.80.96", "213.32.61.248")
fun String.isHGLaborIP(): Boolean = this.lowercase().contains("hglabor") || hgLaborIPs.contains(this)
