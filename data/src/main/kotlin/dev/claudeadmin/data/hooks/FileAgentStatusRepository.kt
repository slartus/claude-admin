package dev.claudeadmin.data.hooks

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.AgentStatus
import dev.claudeadmin.domain.model.AgentStatusEntry
import dev.claudeadmin.domain.repository.AgentStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileAgentStatusRepository(
    private val file: File = AppDirs.statusLogFile,
    scope: CoroutineScope,
    private val pollIntervalMs: Long = 500,
    private val maxLines: Int = 1000,
    private val rotateThreshold: Int = 1500,
) : AgentStatusRepository {

    private val state = MutableStateFlow<Map<String, AgentStatusEntry>>(emptyMap())

    @Volatile
    private var lastOffset: Long = 0
    private val tailBuffer = StringBuilder()

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { rotateIfNeeded() }
            while (isActive) {
                runCatching { readNewLines() }
                delay(pollIntervalMs)
            }
        }
    }

    override fun observe(): Flow<Map<String, AgentStatusEntry>> = state.asStateFlow()

    private fun readNewLines() {
        if (!file.isFile) {
            resetPosition()
            return
        }
        val size = file.length()
        if (size < lastOffset) resetPosition()
        if (size == lastOffset) return

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(lastOffset)
            val bytes = ByteArray((size - lastOffset).toInt())
            raf.readFully(bytes)
            lastOffset = size
            tailBuffer.append(String(bytes, Charsets.UTF_8))
        }

        val updates = mutableMapOf<String, AgentStatusEntry>()
        while (true) {
            val nl = tailBuffer.indexOf('\n')
            if (nl == -1) break
            val line = tailBuffer.substring(0, nl)
            tailBuffer.delete(0, nl + 1)
            val parsed = parseLine(line) ?: continue
            updates[parsed.first] = parsed.second
        }
        if (updates.isNotEmpty()) {
            state.value = state.value + updates
        }
    }

    private fun resetPosition() {
        lastOffset = 0
        tailBuffer.clear()
        if (state.value.isNotEmpty()) state.value = emptyMap()
    }

    private fun rotateIfNeeded() {
        if (!file.isFile) return
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.size <= rotateThreshold) return
        val kept = lines.takeLast(maxLines)
        val tmp = File(file.parentFile, file.name + ".rot")
        tmp.writeText(kept.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun parseLine(raw: String): Pair<String, AgentStatusEntry>? {
        if (raw.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val sid = (obj["session_id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val statusStr = (obj["status"] as? JsonPrimitive)?.contentOrNull ?: return null
        val event = (obj["event"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val ts = (obj["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L
        val status = when (statusStr) {
            "working" -> AgentStatus.WORKING
            "waiting" -> AgentStatus.WAITING
            "idle" -> AgentStatus.IDLE
            else -> return null
        }
        return sid to AgentStatusEntry(status, event, ts)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
