package dev.claudeadmin.data.session

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.repository.ClaudeSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FileClaudeSessionRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val baseDir: File = File(AppDirs.userClaudeDir, "projects"),
) : ClaudeSessionRepository {

    private val metaCache = ConcurrentHashMap<String, CachedMeta>()

    private val shared: Flow<List<AiSession>> =
        watchAll()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override fun observeAll(): Flow<List<AiSession>> = shared

    private fun watchAll(): Flow<List<AiSession>> = callbackFlow {
        trySend(scanAll())

        val registered = ConcurrentHashMap.newKeySet<Path>()
        var watcher: java.nio.file.WatchService? = null

        fun registerSafely(w: java.nio.file.WatchService, path: Path, vararg kinds: java.nio.file.WatchEvent.Kind<*>): Boolean {
            if (!registered.add(path)) return true
            return runCatching { path.register(w, *kinds) }.isSuccess.also { if (!it) registered.remove(path) }
        }

        val job = scope.launch {
            try {
                while (isActive && !baseDir.isDirectory) {
                    delay(BASEDIR_POLL_MS)
                }
                if (!isActive) return@launch

                val w = FileSystems.getDefault().newWatchService().also { watcher = it }
                if (!registerSafely(w, baseDir.toPath(), ENTRY_CREATE, ENTRY_DELETE)) return@launch
                baseDir.listFiles { f -> f.isDirectory }?.forEach { sub ->
                    registerSafely(w, sub.toPath(), ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                }
                trySend(scanAll())

                while (isActive) {
                    val key = w.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    val parent = key.watchable() as? Path
                    var touched = false
                    for (ev in key.pollEvents()) {
                        val ctx = ev.context() as? Path ?: continue
                        val resolved = parent?.resolve(ctx) ?: continue
                        if (resolved.toFile().isDirectory) {
                            registerSafely(w, resolved, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                            touched = true
                        } else if (ctx.fileName.toString().endsWith(".jsonl")) {
                            touched = true
                        }
                    }
                    if (!key.reset()) {
                        registered.remove(parent)
                    }
                    if (touched) {
                        delay(150)
                        trySend(scanAll())
                    }
                }
            } catch (_: ClosedWatchServiceException) {
                // closed, exit
            }
        }

        awaitClose {
            job.cancel()
            runCatching { watcher?.close() }
        }
    }

    private fun scanAll(): List<AiSession> {
        if (!baseDir.isDirectory) return emptyList()
        val folders = baseDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
        val alive = hashSetOf<String>()
        val result = mutableListOf<AiSession>()

        for (folder in folders) {
            val files = folder.listFiles { f -> f.isFile && f.extension == "jsonl" }?.toList().orEmpty()
            for (file in files) {
                alive.add(file.absolutePath)
                val mtime = file.lastModified()
                val cached = metaCache[file.absolutePath]
                val meta = if (cached != null && cached.mtime == mtime) {
                    cached
                } else {
                    val cwd = readCwd(file) ?: decodeSlug(folder.name)
                    val preview = readPreview(file) ?: fallbackPreview(file.nameWithoutExtension)
                    CachedMeta(mtime, cwd, preview).also { metaCache[file.absolutePath] = it }
                }
                result.add(
                    AiSession(
                        id = file.nameWithoutExtension,
                        cwd = meta.cwd,
                        preview = meta.preview,
                        lastModified = mtime,
                        provider = AiProvider.CLAUDE,
                    ),
                )
            }
        }
        metaCache.keys.retainAll(alive)
        return result.sortedByDescending { it.lastModified }
    }

    private fun decodeSlug(slug: String): String = slug.replace('-', '/')

    private fun readCwd(file: File): String? {
        return runCatching {
            file.useLines { lines ->
                var scanned = 0
                for (raw in lines) {
                    if (scanned++ >= MAX_LINES_TO_SCAN) return@useLines null
                    if (raw.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
                    extractCwd(obj)?.let { return@useLines it }
                }
                null
            }
        }.getOrNull()
    }

    private fun extractCwd(obj: JsonObject): String? {
        (obj["cwd"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        val hookInfos = obj["hookInfos"] as? JsonArray
        hookInfos?.forEach { info ->
            val infoObj = info as? JsonObject ?: return@forEach
            (infoObj["cwd"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun readPreview(file: File): String? {
        return runCatching {
            file.useLines { lines ->
                var scanned = 0
                for (raw in lines) {
                    if (scanned++ >= MAX_LINES_TO_SCAN) return@useLines null
                    if (raw.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
                    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
                    if (type != "user") continue
                    val msg = obj["message"] as? JsonObject ?: continue
                    val content = msg["content"] ?: continue
                    val text = extractText(content) ?: continue
                    if (isCommandMeta(text)) continue
                    return@useLines truncate(text)
                }
                null
            }
        }.getOrNull()
    }

    private fun isCommandMeta(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<local-command-") || trimmed.startsWith("<command-name")
    }

    private fun extractText(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> el.firstNotNullOfOrNull { block ->
            val obj = block as? JsonObject ?: return@firstNotNullOfOrNull null
            val t = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (t != "text") return@firstNotNullOfOrNull null
            (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        else -> null
    }

    private fun fallbackPreview(id: String): String = "session ${id.take(8)}"

    private fun truncate(s: String): String {
        val line = s.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return s
        return if (line.length > PREVIEW_MAX_CHARS) line.take(PREVIEW_MAX_CHARS).trimEnd() + "…" else line
    }

    private data class CachedMeta(val mtime: Long, val cwd: String, val preview: String)

    private companion object {
        const val MAX_LINES_TO_SCAN = 200
        const val PREVIEW_MAX_CHARS = 80
        const val BASEDIR_POLL_MS = 2_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
