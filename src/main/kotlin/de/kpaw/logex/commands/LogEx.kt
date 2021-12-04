package de.kpaw.logex.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

object LogEx : CliktCommand(
    help = "The root command of logex"
) {
    init { subcommands(Extract, CombineLogs) }
    override fun run() = Unit
}