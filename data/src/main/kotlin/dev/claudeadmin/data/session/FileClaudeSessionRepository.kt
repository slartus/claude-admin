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
                    val parsed = parseMeta(file)
                    val cwd = parsed.cwd ?: decodeSlug(folder.name)
                    val preview = parsed.title
                        ?: parsed.firstUserText
                        ?: fallbackPreview(file.nameWithoutExtension)
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

    private fun parseMeta(file: File): ParsedMeta {
        var cwd: String? = null
        var title: String? = null
        var firstUserText: String? = null
        runCatching {
            file.useLines { lines ->
                for (raw in lines) {
                    if (raw.isBlank()) continue
                    // Быстрый префильтр, чтобы не парсить JSON для нерелевантных строк.
                    val mayHaveCwd = cwd == null && raw.contains("\"cwd\"")
                    val mayHaveTitle = raw.contains("\"ai-title\"")
                    val mayHaveUser = firstUserText == null && raw.contains("\"user\"")
                    if (!mayHaveCwd && !mayHaveTitle && !mayHaveUser) continue
                    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
                    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
                    when {
                        mayHaveTitle && type == "ai-title" ->
                            (obj["aiTitle"] as? JsonPrimitive)?.contentOrNull
                                ?.takeIf { it.isNotBlank() }
                                ?.let { title = firstLine(it) }
                        mayHaveUser && type == "user" ->
                            extractUserText(obj)?.let { firstUserText = firstLine(it) }
                    }
                    if (cwd == null) extractCwd(obj)?.let { cwd = it }
                }
            }
        }
        return ParsedMeta(cwd = cwd, title = title, firstUserText = firstUserText)
    }

    private fun extractUserText(obj: JsonObject): String? {
        val msg = obj["message"] as? JsonObject ?: return null
        val content = msg["content"] ?: return null
        val text = extractText(content) ?: return null
        return if (isCommandMeta(text)) null else text
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

    private fun firstLine(s: String): String =
        s.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: s

    private data class CachedMeta(val mtime: Long, val cwd: String, val preview: String)

    private data class ParsedMeta(val cwd: String?, val title: String?, val firstUserText: String?)

    private companion object {
        const val BASEDIR_POLL_MS = 2_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
