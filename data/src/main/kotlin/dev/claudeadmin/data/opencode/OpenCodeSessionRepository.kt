package dev.claudeadmin.data.opencode

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.repository.AiSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenCodeSessionRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AiSessionRepository {

    override val provider: AiProvider = AiProvider.OPENCODE

    private val _sessions = MutableStateFlow<List<AiSession>>(emptyList())
    private val sessions = _sessions.asStateFlow()

    private fun findDb(): File? = OpenCodeDb.findDb()

    init {
        scope.launch {
            while (true) {
                try {
                    withContext(Dispatchers.IO) {
                        val db = findDb() ?: return@withContext
                        val current = querySessions(db)
                        if (current != _sessions.value) {
                            _sessions.value = current
                        }
                    }
                } catch (t: kotlinx.coroutines.CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    println("[OpenCode] poll error: ${t.message}")
                }
                delay(POLL_MS)
            }
        }
    }

    override fun observeAll(): Flow<List<AiSession>> = sessions

    private fun querySessions(dbFile: File): List<AiSession> {
        return runCatching {
            val sql = """
                SELECT id, directory, title, time_updated
                FROM session
                ORDER BY time_updated DESC;
            """.trimIndent()

            val process = ProcessBuilder("sqlite3", "-json", dbFile.absolutePath, sql)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = StringBuilder()
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        val buf = CharArray(8192)
                        while (true) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            output.append(buf, 0, n)
                        }
                    }
                } catch (_: java.io.IOException) {
                }
            }.apply { isDaemon = true }
            readerThread.start()

            try {
                readerThread.join(QUERY_TIMEOUT_SEC * 1000L)
            } catch (_: InterruptedException) {
                process.destroyForcibly()
                return emptyList()
            }

            val done = process.waitFor(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!done) {
                process.destroyForcibly()
                return emptyList()
            }

            val json = output.toString()
            if (json.isBlank()) emptyList() else parseJsonArray(json)
        }.getOrElse {
            println("[OpenCode] Query error: ${it.message}")
            emptyList()
        }
    }

    private fun parseJsonArray(json: String): List<AiSession> {
        val sessions = mutableListOf<AiSession>()
        val items = splitJsonObjects(json)
        for (item in items) {
            val id = extractJsonString(item, "id") ?: continue
            val cwd = extractJsonString(item, "directory") ?: ""
            val title = extractJsonString(item, "title") ?: ""
            val lastModified = extractJsonLong(item, "time_updated") ?: 0L
            sessions.add(
                AiSession(
                    id = id,
                    cwd = cwd,
                    preview = title,
                    lastModified = lastModified,
                    provider = AiProvider.OPENCODE,
                ),
            )
        }
        return sessions
    }

    private fun splitJsonObjects(json: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val start = StringBuilder()
        var inString = false
        var escaped = false

        for (ch in json) {
            if (escaped) {
                start.append(ch)
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                start.append(ch)
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
            }
            if (!inString) {
                if (ch == '{') {
                    if (depth == 0) start.clear()
                    depth++
                }
                if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        start.append(ch)
                        result.add(start.toString())
                        continue
                    }
                }
            }
            start.append(ch)
        }
        return result
    }

    private fun extractJsonString(obj: String, key: String): String? {
        val pattern = "\"$key\":\""
        val idx = obj.indexOf(pattern)
        if (idx < 0) return null
        val start = idx + pattern.length
        val sb = StringBuilder()
        var i = start
        var done = false
        while (i < obj.length && !done) {
            val ch = obj[i]
            if (ch == '\\') {
                val next = if (i + 1 < obj.length) obj[i + 1] else ' '
                when (next) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> { sb.append(ch); sb.append(next) }
                }
                i += 2
            } else if (ch == '"') {
                done = true
            } else {
                sb.append(ch)
                i++
            }
        }
        return if (done) sb.toString() else null
    }

    private fun extractJsonLong(obj: String, key: String): Long? {
        val pattern = "\"$key\":"
        val idx = obj.indexOf(pattern)
        if (idx < 0) return null
        val start = idx + pattern.length
        var end = start
        while (end < obj.length) {
            val ch = obj[end]
            if (ch.isWhitespace() || ch == ',' || ch == '}' || ch == '\n') break
            end++
        }
        return if (end > start) obj.substring(start, end).toLongOrNull() else null
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val QUERY_TIMEOUT_SEC = 120L
    }
}
