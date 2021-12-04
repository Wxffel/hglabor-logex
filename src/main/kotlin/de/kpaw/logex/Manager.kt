package de.kpaw.logex

import de.kpaw.logex.commands.LogEx
import java.lang.Exception

fun main(args: Array<String>) = try {
    LogEx.main(args)
} catch (exception: Exception) {
    exception.printStackTrace()
    println("An error occurred!")
}