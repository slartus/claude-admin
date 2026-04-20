package dev.claudeadmin.data.hooks

import dev.claudeadmin.domain.model.AgentStatus
import dev.claudeadmin.domain.model.AgentStatusEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileAgentStatusRepositoryTest {

    @TempDir
    lateinit var tmp: File

    @Test
    fun `parses new lines and emits map keyed by session_id`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = FileAgentStatusRepository(file = file, scope = scope, pollIntervalMs = 50)

        file.appendText("""{"timestamp":100,"session_id":"s1","status":"working","event":"PreToolUse"}""" + "\n")
        val working = awaitMap(repo) { it["s1"]?.status == AgentStatus.WORKING }
        assertEquals(AgentStatus.WORKING, working["s1"]?.status)
        assertEquals("PreToolUse", working["s1"]?.event)

        file.appendText("""{"timestamp":200,"session_id":"s1","status":"waiting","event":"Notification"}""" + "\n")
        val waiting = awaitMap(repo) { it["s1"]?.status == AgentStatus.WAITING }
        assertEquals(AgentStatus.WAITING, waiting["s1"]?.status)

        file.appendText("""{"timestamp":300,"session_id":"s2","status":"idle","event":"Stop"}""" + "\n")
        val both = awaitMap(repo) {
            it["s1"]?.status == AgentStatus.WAITING && it["s2"]?.status == AgentStatus.IDLE
        }
        assertEquals(AgentStatus.WAITING, both["s1"]?.status)
        assertEquals(AgentStatus.IDLE, both["s2"]?.status)

        scope.cancel()
    }

    @Test
    fun `skips malformed lines`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        file.writeText("not-json\n" +
            """{"timestamp":1,"session_id":"ok","status":"idle","event":"Stop"}""" + "\n" +
            "{\"bad\":\n"
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = FileAgentStatusRepository(file = file, scope = scope, pollIntervalMs = 50)

        val map = awaitMap(repo) { it["ok"]?.status == AgentStatus.IDLE }
        assertEquals(1, map.size)
        assertEquals(AgentStatus.IDLE, map["ok"]?.status)

        scope.cancel()
    }

    @Test
    fun `rotates file when threshold exceeded`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        val builder = StringBuilder()
        repeat(2500) { i ->
            builder.append("""{"timestamp":$i,"session_id":"s","status":"idle","event":"Stop"}""").append('\n')
        }
        file.writeText(builder.toString())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = FileAgentStatusRepository(
            file = file,
            scope = scope,
            pollIntervalMs = 50,
            maxLines = 100,
            rotateThreshold = 200,
        )

        val map = awaitMap(repo) { it["s"]?.status == AgentStatus.IDLE }
        assertEquals(AgentStatus.IDLE, map["s"]?.status)
        assertEquals(100, file.readLines().size)
        assertEquals(2499L, map["s"]?.updatedAt)

        scope.cancel()
    }

    @Test
    fun `handles missing file gracefully`() = runBlocking {
        val file = File(tmp, "nope.jsonl")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = FileAgentStatusRepository(file = file, scope = scope, pollIntervalMs = 50)

        delay(150)
        val map = repo.observe().first()
        assertTrue(map.isEmpty())

        scope.cancel()
    }

    private suspend fun awaitMap(
        repo: FileAgentStatusRepository,
        predicate: (Map<String, AgentStatusEntry>) -> Boolean,
    ): Map<String, AgentStatusEntry> {
        return withTimeoutOrNull(2000) {
            repo.observe().first { predicate(it) }
        } ?: error("Timed out waiting for map predicate")
    }
}
