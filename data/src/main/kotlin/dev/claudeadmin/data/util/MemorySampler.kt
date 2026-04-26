package dev.claudeadmin.data.util

import dev.claudeadmin.domain.model.TerminalSessionId
import java.util.concurrent.TimeUnit

data class MemorySnapshot(
    val appBytes: Long,
    val terminalsBytes: Long,
    val perTerminalBytes: Map<TerminalSessionId, Long>,
) {
    companion object {
        val EMPTY = MemorySnapshot(0L, 0L, emptyMap())
    }
}

object MemorySampler {

    fun sample(terminalPids: Map<TerminalSessionId, Long>): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val appBytes = runtime.totalMemory() - runtime.freeMemory()

        if (terminalPids.isEmpty()) {
            return MemorySnapshot(appBytes = appBytes, terminalsBytes = 0L, perTerminalBytes = emptyMap())
        }

        val table = readPsTable() ?: return MemorySnapshot(
            appBytes = appBytes,
            terminalsBytes = 0L,
            perTerminalBytes = emptyMap(),
        )
        val childrenByParent = table.values.groupBy { it.ppid }
        val perTerminal = terminalPids.mapValues { (_, pid) ->
            subtreeRssBytes(pid, table, childrenByParent)
        }
        return MemorySnapshot(
            appBytes = appBytes,
            terminalsBytes = perTerminal.values.sum(),
            perTerminalBytes = perTerminal,
        )
    }

    private data class PsRow(val pid: Long, val ppid: Long, val rssKb: Long)

    private fun subtreeRssBytes(
        rootPid: Long,
        table: Map<Long, PsRow>,
        childrenByParent: Map<Long, List<PsRow>>,
    ): Long {
        val root = table[rootPid] ?: return 0L
        var totalKb = 0L
        val stack = ArrayDeque<PsRow>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val row = stack.removeLast()
            totalKb += row.rssKb
            childrenByParent[row.pid]?.forEach { stack.addLast(it) }
        }
        return totalKb * 1024L
    }

    private fun readPsTable(): Map<Long, PsRow>? {
        val process = runCatching {
            ProcessBuilder("/bin/ps", "-ax", "-o", "pid=,ppid=,rss=")
                .redirectErrorStream(true)
                .start()
        }.getOrElse { return null }
        val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrElse {
            process.destroyForcibly()
            return null
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null

        val map = HashMap<Long, PsRow>(256)
        output.lineSequence().forEach { line ->
            val parts = line.trim().split(WHITESPACE)
            if (parts.size < 3) return@forEach
            val pid = parts[0].toLongOrNull() ?: return@forEach
            val ppid = parts[1].toLongOrNull() ?: return@forEach
            val rss = parts[2].toLongOrNull() ?: return@forEach
            map[pid] = PsRow(pid, ppid, rss)
        }
        return map
    }

    private val WHITESPACE = Regex("\\s+")
}
