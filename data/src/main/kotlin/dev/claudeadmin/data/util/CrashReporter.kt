package dev.claudeadmin.data.util

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object CrashReporter {
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

    @Volatile
    private var emergencyReserve: ByteArray? = ByteArray(64 * 1024)

    val directory: File by lazy {
        val dir = File(AppDirs.root, "crashes")
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("CrashReporter: failed to create ${dir.absolutePath}; crash logging disabled")
        }
        dir
    }

    private val lastSeenMarker: File by lazy { File(directory, ".last-seen") }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            releaseReserve()
            runCatching { writeCrash(thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        System.setProperty("sun.awt.exception.handler", AwtExceptionHandler::class.java.name)
    }

    private fun releaseReserve() {
        emergencyReserve = null
    }

    fun snapshotUnseenCrashes(): List<File> {
        val lastSeen = readLastSeen()
        return listCrashFiles().filter { it.name > lastSeen }
    }

    fun markSeen(snapshot: List<File>) {
        val maxName = snapshot.maxOfOrNull { it.name } ?: return
        runCatching { lastSeenMarker.writeText(maxName) }
    }

    internal fun handleAwtException(throwable: Throwable) {
        releaseReserve()
        runCatching { writeCrash(Thread.currentThread().name + " (awt)", throwable) }
        throwable.printStackTrace()
    }

    private fun listCrashFiles(): List<File> =
        directory.listFiles { f ->
            f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log")
        }?.toList().orEmpty()

    private fun readLastSeen(): String =
        if (lastSeenMarker.exists()) runCatching { lastSeenMarker.readText().trim() }.getOrDefault("") else ""

    private fun writeCrash(threadName: String, throwable: Throwable) {
        val now = OffsetDateTime.now()
        val suffix = System.nanoTime().toString().takeLast(6)
        val file = File(directory, "crash-${now.toLocalDateTime().format(fileNameFormatter)}-$suffix.log")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("Time: $now")
            pw.println("Thread: $threadName")
            pw.println()
            throwable.printStackTrace(pw)
        }
        file.writeText(sw.toString())
    }
}

/**
 * Referenced by `sun.awt.exception.handler` system property — must be a public class with a
 * no-arg constructor and a `handle(Throwable)` method. Routes EDT exceptions to [CrashReporter].
 */
class AwtExceptionHandler {
    @Suppress("unused")
    fun handle(throwable: Throwable) {
        CrashReporter.handleAwtException(throwable)
    }
}
