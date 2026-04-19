package dev.claudeadmin.app.ui.util

import java.awt.Desktop
import java.io.File

fun openInDefaultApp(path: String) {
    val file = File(path)
    if (!file.exists()) return
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.OPEN)) return
    Thread { runCatching { desktop.open(file) } }.start()
}

fun revealInFinder(path: String) {
    val file = File(path)
    if (!file.exists()) return
    Thread {
        runCatching {
            ProcessBuilder("/usr/bin/open", "-R", file.absolutePath)
                .redirectErrorStream(true)
                .start()
        }
    }.start()
}
