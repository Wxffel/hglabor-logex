package de.kpaw.logex.commands

import com.github.ajalt.mordant.rendering.TextColors
import de.kpaw.logex.blacklistedNames
import de.kpaw.logex.hgLaborDomains
import de.kpaw.logex.hgLaborIPs
import de.kpaw.logex.terminal

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
    val minecraftNameRegex = Regex("\\w{3,16}")

    val timePattern = Regex("""([012]\d:[0-5]\d:[0-5]\d)""")

    val messagePattern = Regex(""": \[CHAT] (.*)""")
    val connectingPattern = Regex("""(?i)connecting to [\w\d.]*""")
    val survivalMsgPattern = Regex("<$minecraftNameRegex> (.*)")

    val msgRegexes = arrayListOf(
        Regex("\\? you to \\? $minecraftNameRegex \\?  (.*)"),
        Regex("$minecraftNameRegex \\? to you \\?  (.*)"),
        Regex("\\? $minecraftNameRegex \\? $minecraftNameRegex \\?  (.*)"),
        Regex("MSG \\? $minecraftNameRegex \\? $minecraftNameRegex \\?  (.*)"),
        Regex("\\? $minecraftNameRegex \\? to you \\?  (.*)")
    )
}

fun String.isBlacklistedName() = blacklistedNames.contains(this.lowercase())

fun String.isValidMinecraftName() = this.matches(LogExRegex.minecraftNameRegex)

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

fun HashSet<Pair<String, Boolean>>.extractHGLaborSurvivalMessages(): MutableSet<String> {
    val hgLaborChatMessages = mutableSetOf<String>()
    // Extracts ONLY messages send on a specific server(-ip)
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
