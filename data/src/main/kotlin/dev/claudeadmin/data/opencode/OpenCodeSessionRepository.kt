package dev.claudeadmin.data.opencode

import dev.claudeadmin.data.util.AppDirs
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

class OpenCodeSessionRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AiSessionRepository {

    override val provider: AiProvider = AiProvider.OPENCODE

    private val _sessions = MutableStateFlow<List<AiSession>>(emptyList())
    private val sessions = _sessions.asStateFlow()

    private val possibleDbDirs = listOf(
        File(AppDirs.userHome, ".local/share/opencode"),
        File(AppDirs.userHome, ".opencode"),
        File(AppDirs.userHome, "Library/Application Support/opencode"),
    )

    private fun findDb(): File? {
        for (dir in possibleDbDirs) {
            val db = File(dir, "opencode.db")
            if (db.exists()) return db
        }
        return null
    }

    init {
        scope.launch {
            while (true) {
                val db = findDb()
                if (db != null) {
                    val current = querySessions(db)
                    if (current != _sessions.value) {
                        _sessions.value = current
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    override fun observeAll(): Flow<List<AiSession>> = sessions

    private suspend fun querySessions(dbFile: File): List<AiSession> = withContext(Dispatchers.IO) {
        runCatching {
            val sql = """
                SELECT s.id, s.directory, s.title, s.time_updated,
                       json_extract(p.data, '$.text') AS first_prompt
                FROM session s
                LEFT JOIN message m ON m.session_id = s.id
                                   AND json_extract(m.data, '$.role') = 'user'
                LEFT JOIN part p ON p.message_id = m.id
                WHERE p.id = (
                    SELECT p2.id FROM part p2
                    JOIN message m2 ON p2.message_id = m2.id
                    WHERE m2.session_id = s.id
                      AND json_extract(m2.data, '$.role') = 'user'
                    ORDER BY m2.time_created ASC, p2.time_created ASC
                    LIMIT 1
                )
                ORDER BY s.time_updated DESC;
            """.trimIndent()

            val process = ProcessBuilder("sqlite3", "-json", dbFile.absolutePath, sql)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()

            if (output.isBlank()) return@withContext emptyList()

            parseJsonArray(output)
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
            val prompt = extractJsonString(item, "first_prompt")
            val lastModified = extractJsonLong(item, "time_updated") ?: 0L
            val preview = if (!prompt.isNullOrBlank()) prompt else title
            sessions.add(
                AiSession(
                    id = id,
                    cwd = cwd,
                    preview = preview,
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
    }
}
