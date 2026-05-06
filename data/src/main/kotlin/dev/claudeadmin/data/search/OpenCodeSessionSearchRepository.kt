package dev.claudeadmin.data.search

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.domain.repository.SessionSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class OpenCodeSessionSearchRepository(
    private val possibleDbDirs: List<File> = listOf(
        File(AppDirs.userHome, ".local/share/opencode"),
        File(AppDirs.userHome, ".opencode"),
        File(AppDirs.userHome, "Library/Application Support/opencode"),
    ),
) : SessionSearchRepository {

    override suspend fun search(query: String): List<SessionSearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val db = findDb() ?: return@withContext emptyList()
        runCatching { runQuery(db, query) }.getOrElse { emptyList() }
    }

    private fun findDb(): File? {
        for (dir in possibleDbDirs) {
            val db = File(dir, "opencode.db")
            if (db.exists()) return db
        }
        return null
    }

    private fun runQuery(db: File, query: String): List<SessionSearchHit> {
        val escapedForLike = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
            .replace("'", "''")
        val likePattern = "%$escapedForLike%"
        val sql = """
            SELECT m.session_id AS session_id,
                   json_extract(p.data, '${'$'}.text') AS text
            FROM part p
            JOIN message m ON p.message_id = m.id
            WHERE json_extract(p.data, '${'$'}.type') = 'text'
              AND lower(json_extract(p.data, '${'$'}.text')) LIKE lower('$likePattern') ESCAPE '\'
            LIMIT $MAX_ROWS;
        """.trimIndent()

        val process = ProcessBuilder("sqlite3", "-json", db.absolutePath, sql)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.waitFor()

        if (output.isBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(output) as? JsonArray }.getOrNull() ?: return emptyList()

        val seen = LinkedHashMap<String, SessionSearchHit>()
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val sessionId = (obj["session_id"] as? JsonPrimitive)?.contentOrNull ?: continue
            if (seen.containsKey(sessionId)) continue
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull ?: continue
            val idx = text.indexOf(query, ignoreCase = true)
            if (idx < 0) continue
            val snippet = buildSnippet(text, idx, query.length)
            seen[sessionId] = SessionSearchHit(
                sessionId = sessionId,
                provider = AiProvider.OPENCODE,
                snippet = snippet.text,
                matchOffset = snippet.matchOffset,
                matchLength = query.length,
            )
        }
        return seen.values.toList()
    }

    private companion object {
        const val MAX_ROWS = 2_000
        val json = Json { ignoreUnknownKeys = true }
    }
}
