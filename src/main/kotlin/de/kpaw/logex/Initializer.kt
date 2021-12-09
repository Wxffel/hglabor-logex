package de.kpaw.logex

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import de.kpaw.logex.commands.LogEx
import java.lang.Exception

fun main(args: Array<String>) = try {
    LogEx.main(args)
} catch (exception: Exception) {
    exception.printStackTrace()
    terminal.println(TextColors.brightRed("An error occurred!"))
}

val terminal = Terminal()