package dev.claudeadmin.data.hooks

import dev.claudeadmin.domain.model.AgentStatus
import dev.claudeadmin.domain.model.AgentStatusEntry
import dev.claudeadmin.domain.repository.AgentStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile

/**
 * Tails {AppDirs.statusLogFile} (a JSONL written by the claude-admin-status hook
 * plugin) and exposes the latest status per session id.
 */
class FileAgentStatusRepository(
    private val file: File = File(System.getProperty("user.home"), ".claude-admin/status.jsonl"),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollIntervalMs: Long = 500,
    private val maxLines: Int = 1000,
    private val rotateThreshold: Int = 1500,
) : AgentStatusRepository {

    override fun observe(): Flow<Map<String, AgentStatusEntry>> = callbackFlow {
        val map = linkedMapOf<String, AgentStatusEntry>()
        var raf: RandomAccessFile? = null
        var lineCount = 0
        var lastOffset = 0L

        fun parseLine(line: String) {
            val obj = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
            val sid = (obj["session_id"] as? JsonPrimitive)?.contentOrNull ?: return
            val statusStr = (obj["status"] as? JsonPrimitive)?.contentOrNull ?: return
            val status = when (statusStr) {
                "working" -> AgentStatus.WORKING
                "waiting" -> AgentStatus.WAITING
                "idle" -> AgentStatus.IDLE
                "ended" -> {
                    map.remove(sid)
                    return
                }
                else -> return
            }
            val event = (obj["event"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val toolName = (obj["tool_name"] as? JsonPrimitive)?.contentOrNull
            val message = (obj["message"] as? JsonPrimitive)?.contentOrNull
            val ts = (obj["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L
            map[sid] = AgentStatusEntry(status, event, toolName, message, ts)
        }

        fun rotate(raf: RandomAccessFile) {
            if (lineCount <= rotateThreshold) return
            val all = file.readLines().takeLast(maxLines)
            raf.setLength(0)
            raf.seek(0)
            raf.write(all.joinToString("\n").toByteArray())
            if (all.isNotEmpty()) raf.write("\n".toByteArray())
            map.clear()
            all.forEach(::parseLine)
            lineCount = all.size
            lastOffset = raf.filePointer
        }

        try {
            val r = RandomAccessFile(file, "rw")
            raf = r
            var consumed = false

            fun readAvailable(fromOffset: Long) {
                val reader = raf ?: return
                var startOfLastLine = fromOffset
                reader.seek(fromOffset)
                while (true) {
                    val lineStart = reader.filePointer
                    val line = reader.readLine() ?: break
                    startOfLastLine = lineStart
                    lineCount++
                    parseLine(line)
                    consumed = true
                    rotate(reader)
                    if (lineCount > rotateThreshold) break
                }
                // Avoid swallowing a trailing line that has no newline yet (the hook
                // may still be writing it). Only advance the offset past the last line
                // terminated by '\n'; otherwise rewind so the next poll re-reads the
                // partial line once it completes.
                val endPos = reader.filePointer
                lastOffset = if (endPos > fromOffset) {
                    reader.seek(endPos - 1)
                    val lastByte = reader.readByte()
                    reader.seek(endPos)
                    if (lastByte == '\n'.code.toByte()) endPos else startOfLastLine
                } else {
                    fromOffset
                }
            }

            readAvailable(0)
            trySend(map.toMap())

            val job = scope.launch {
                while (isActive) {
                    delay(pollIntervalMs)
                    consumed = false
                    readAvailable(lastOffset)
                    if (consumed) trySend(map.toMap())
                }
            }

            awaitClose {
                job.cancel()
                runCatching { raf?.close() }
            }
        } catch (t: Throwable) {
            close(t)
            runCatching { raf?.close() }
        }
    }
}
