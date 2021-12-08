package de.kpaw.logex

/**
 * This object holds the regex patterns to
 * match the given patterns.
 */

object LogExPatterns {
    private val mcName = Regex("""(\w{3,16})""")
    val time = Regex("""([012]\d:[0-5]\d:[0-5]\d)""")
    val connecting = Regex(""": (?i)connecting to [a-zA-Z\d.-]*""")
    val chatMessage = Regex(""": \[CHAT] (.*)""")
    val hgLaborChatMessage = Regex(""": \[CHAT] (\? )?(\(\w*\) )?(\.)?$mcName [»?] (.*)""")
    val privateChatMessage = Regex(""": \[CHAT] (MSG )?(\? )?$mcName \? ($mcName|(to you)) \?  (.*)""")
    val vanillaChatMessage = Regex(""": \[CHAT] <$mcName> (.*)""")
}

/**
 * These are chat messsage patterns in Minecraft-Logs.
 * DEF: Default/Vanilla
 * BLC: Badlion Client
 * IDK: Possibly Optifine
 */

// DEF  [23:36:11] [main/INFO]: [CHAT] <msg>
// BLC  [11:07:24] [Client thread/INFO]: [CHAT] <msg>
// IDK  [26Jun2021 18:22:21.883] [Render thread/INFO] [net.minecraft.client.gui.NewChatGui/]: [CHAT] <msg>

/**
 * These are connecting messsage patterns in Minecraft-Logs.
 * DEF: Default/Vanilla
 * BLC: Badlion Client
 * IDK: Possibly Optifine
 */

// DEF  [12:07:12] [main/INFO]: Connecting to tcpshield.hglabor.de., 25565
// IDK  [29Jun2021 21:10:20.240] [Render thread/INFO] [net.minecraft.client.gui.screen.ConnectingScreen/]: Connecting to hgbuild.gq, 25565
// BLC  [15:59:15] [Render thread/INFO]: Connecting to hgbuild.gq, 25565 // these are attempts
// BLC  [15:59:17] [Render thread/INFO]: Worker done, connecting to hgbuild.gq, 25565 // this is the final connection
// BLC  [10:43:08] [Client thread/INFO]: Connecting to mc.hypixel.net, 25565


/**
 * These are patterns for the public HGLabor chat.
 * They are matched with the following regex pattern:
 * : \[CHAT] (\? )?(\(\w*\) )?(\w{3,16}) [»?] (.*)
 * The sequence ": \\[CHAT] " is used to match at the
 * beginning of the chat message in a log.
 */

// : [CHAT] $minecraftName » (.*)
// : [CHAT] $minecraftName ? (.*)
// : [CHAT] ? $minecraftName » (.*)
// : [CHAT] ? $minecraftName ? (.*)
// : [CHAT] ? \((\w)\) $minecraftName » (.*)

/**
 * These are three patterns for the private HGLabor chat.
 * They are unfortunately matched too,
 * so we have to sort them out later.
 */

// : [CHAT] $minecraftName ? to you ?  (.*)
// : [CHAT] MSG ? $minecraftName ? $minecraftName ?  (.*)
// : [CHAT] ? $minecraftName ? $minecraftName ?  (.*)

/**
 * These are three more patterns for the private HGLabor chat.
 * They won't get matched, so we can forget them.
 */

// : [CHAT] you to ? $minecraftName ?  (.*)
// : [CHAT] You -> $minecraftName: (.*)
// : [CHAT] $minecraftName -> You: (.*)

/**
 * This is the vanilla message pattern.
 * These messages get matched because they
 * could be sent on HGLabor.
 */

// <$minecraftName> (.*)



// Easy copy-paste for validating regex patterns:

/*
// Öffentlicher HGLabor Chat
: [CHAT] minec4ft_nam____ ? EY WARUM ANTWORTET MIR KEINER
: [CHAT] cm123_name » wollt ihr gegeneinander fighten?
: [CHAT] ? 125Name » @X_kacke_SUS hat den namen erfunden
: [CHAT] ? NAZUDHBVD ? kommt jemand ne runde hg? C:
: [CHAT] ? (ffa) mcnMAEMD34o » okay >.<
: [CHAT] <mcname> nachricht

// Private Nachrichten
: [CHAT] minec4ft_name ? to you ?  Das ist eine private Nachricht
: [CHAT] you to ? minec4ft_name ?  Lass das niemandem erzählen!111
: [CHAT] You -> minec4ft_name: Kannst du HG kommen? Ist grad voll leer :(
: [CHAT] minec4ft_name -> You: Ähm...ich spiele lieber FFA hahaa
: [CHAT] MSG ? minec4ft_name ? minec4ft_name ?  bist du ein mädchen?????
: [CHAT] ? minec4ft_name ? minec4ft_name ?  0174------ meine Telefonnummer
: [CHAT] ? minec4ft_name ? to you ?  0174------ meine Telefonnummer
*/
