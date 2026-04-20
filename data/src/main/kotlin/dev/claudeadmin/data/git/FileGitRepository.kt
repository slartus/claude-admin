package dev.claudeadmin.data.git

import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.repository.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FileGitRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GitRepository {

    private val cache = ConcurrentHashMap<String, Flow<GitStatus?>>()

    override fun observe(path: String): Flow<GitStatus?> =
        cache.getOrPut(path) { buildFlow(path) }

    private fun buildFlow(path: String): Flow<GitStatus?> =
        watchHeadFile(path)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    private fun watchHeadFile(projectPath: String): Flow<GitStatus?> = callbackFlow {
        val headFile = resolveHeadFile(File(projectPath))
        if (headFile == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        trySend(readStatus(headFile))

        val watchDir: Path = headFile.parentFile.toPath()
        val watcher = FileSystems.getDefault().newWatchService()
        runCatching { watchDir.register(watcher, ENTRY_MODIFY, ENTRY_CREATE) }
            .onFailure {
                watcher.close()
                awaitClose { }
                return@callbackFlow
            }

        val job = scope.launch {
            try {
                while (isActive) {
                    val key = watcher.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    val touched = key.pollEvents().any { ev ->
                        (ev.context() as? Path)?.fileName?.toString() == headFile.name
                    }
                    if (!key.reset()) break
                    if (touched) {
                        // small debounce to coalesce rapid writes by git
                        kotlinx.coroutines.delay(120)
                        trySend(readStatus(headFile))
                    }
                }
            } catch (_: ClosedWatchServiceException) {
                // watcher closed, exit
            }
        }

        awaitClose {
            job.cancel()
            runCatching { watcher.close() }
        }
    }

    private fun resolveHeadFile(startDir: File): File? {
        var dir: File? = startDir
        while (dir != null) {
            val gitEntry = File(dir, ".git")
            if (gitEntry.exists()) {
                val gitDir = when {
                    gitEntry.isDirectory -> gitEntry
                    gitEntry.isFile -> readGitDirRedirect(gitEntry, dir)
                    else -> null
                }
                val head = gitDir?.let { File(it, "HEAD") }
                if (head?.isFile == true) return head
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun readGitDirRedirect(gitFile: File, baseDir: File): File? {
        val line = runCatching { gitFile.readText() }.getOrNull() ?: return null
        val target = line.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("gitdir:") }
            ?.removePrefix("gitdir:")
            ?.trim()
            ?: return null
        val resolved = File(target).let { if (it.isAbsolute) it else File(baseDir, target) }
        return resolved.takeIf { it.isDirectory }
    }

    private fun readStatus(headFile: File): GitStatus? {
        val text = runCatching { headFile.readText().trim() }.getOrNull() ?: return null
        if (text.startsWith("ref:")) {
            val ref = text.removePrefix("ref:").trim()
            val branch = ref.removePrefix("refs/heads/").takeIf { it.isNotBlank() } ?: ref
            return GitStatus(branch = branch, headSha = null, isDetached = false)
        }
        // detached HEAD: line is a sha
        if (text.matches(Regex("^[0-9a-fA-F]{4,64}$"))) {
            return GitStatus(branch = null, headSha = text, isDetached = true)
        }
        return null
    }
}
