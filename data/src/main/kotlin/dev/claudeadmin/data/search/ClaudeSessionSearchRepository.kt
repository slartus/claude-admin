package dev.claudeadmin.data.search

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.domain.repository.SessionSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

class ClaudeSessionSearchRepository(
    private val baseDir: File = File(AppDirs.userClaudeDir, "projects"),
    private val cache: SessionTextCache = SessionTextCache(),
) : SessionSearchRepository {

    override suspend fun search(query: String): List<SessionSearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !baseDir.isDirectory) return@withContext emptyList()
        val hits = mutableListOf<SessionSearchHit>()
        val alive = HashSet<String>()
        val folders = baseDir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()
        for (folder in folders) {
            val files = folder.listFiles { f -> f.isFile && f.extension == "jsonl" } ?: continue
            for (file in files) {
                val id = file.nameWithoutExtension
                alive.add(id)
                val mtime = file.lastModified()
                val text = cache.get(id, mtime) ?: run {
                    val parsed = readSessionText(file).orEmpty()
                    cache.put(id, mtime, parsed)
                    parsed
                }
                if (text.isEmpty()) continue
                val idx = text.indexOf(query, ignoreCase = true)
                if (idx < 0) continue
                val snippet = buildSnippet(text, idx, query.length)
                hits.add(
                    SessionSearchHit(
                        sessionId = id,
                        provider = AiProvider.CLAUDE,
                        snippet = snippet.text,
                        matchOffset = snippet.matchOffset,
                        matchLength = query.length,
                    ),
                )
            }
        }
        cache.retainOnly(alive)
        hits
    }

    private fun readSessionText(file: File): String? {
        return runCatching {
            buildString {
                file.useLines { lines ->
                    for (raw in lines) {
                        if (raw.isBlank()) continue
                        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
                        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
                        if (type != "user" && type != "assistant") continue
                        val msg = obj["message"] as? JsonObject ?: continue
                        val content = msg["content"] ?: continue
                        val text = extractAllText(content) ?: continue
                        if (isCommandMeta(text)) continue
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
        }.getOrNull()
    }

    private fun isCommandMeta(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<local-command-") || trimmed.startsWith("<command-name")
    }

    private fun extractAllText(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> buildString {
            for (block in el) {
                val obj = block as? JsonObject ?: continue
                val t = (obj["type"] as? JsonPrimitive)?.contentOrNull
                if (t != "text") continue
                val text = (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                if (isNotEmpty()) append('\n')
                append(text)
            }
        }.takeIf { it.isNotEmpty() }
        else -> null
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
