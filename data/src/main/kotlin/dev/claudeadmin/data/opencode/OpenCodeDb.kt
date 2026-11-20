package dev.claudeadmin.data.opencode

import dev.claudeadmin.data.util.AppDirs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

internal object OpenCodeDb {
    private val possibleDbDirs = listOf(
        File(AppDirs.userHome, ".local/share/opencode"),
        File(AppDirs.userHome, ".opencode"),
        File(AppDirs.userHome, "Library/Application Support/opencode"),
    )

    fun findDb(): File? {
        for (dir in possibleDbDirs) {
            val db = File(dir, "opencode.db")
            if (db.exists()) return db
        }
        return null
    }

    fun findSessionIds(db: File, cwd: String, afterTs: Long, excludedIds: Set<String>): List<String> {
        val escapedCwd = cwd.replace("'", "''")
        val escapedExcluded = excludedIds.joinToString("','") { it.replace("'", "''") }
        val sql = """
            SELECT id FROM session
            WHERE directory = '$escapedCwd'
              AND time_created >= $afterTs
              ${if (excludedIds.isEmpty()) "" else "AND id NOT IN ('$escapedExcluded')"}
            ORDER BY time_created ASC
            LIMIT $RESOLVE_CANDIDATES;
        """.trimIndent()
        return runCatching {
            val process = ProcessBuilder("sqlite3", db.absolutePath, sql)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val done = process.waitFor(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!done) {
                process.destroyForcibly()
                return@runCatching emptyList()
            }
            output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())
    }

    private const val RESOLVE_CANDIDATES = 10
    private const val QUERY_TIMEOUT_SEC = 10L
}
