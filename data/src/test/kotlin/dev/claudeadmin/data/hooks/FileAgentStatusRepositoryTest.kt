package dev.claudeadmin.data.hooks

import dev.claudeadmin.domain.model.AgentStatus
import dev.claudeadmin.domain.model.AgentStatusEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileAgentStatusRepositoryTest {

    @TempDir
    lateinit var tmp: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun line(sessionId: String, status: String, event: String, tool: String? = null, message: String? = null): String =
        """{"timestamp":1,"session_id":"$sessionId","status":"$status","event":"$event","tool_name":${tool?.let { "\"$it\"" } ?: "null"},"message":${message?.let { "\"$it\"" } ?: "null"}}"""

    private fun repository(file: File) =
        FileAgentStatusRepository(file = file, scope = scope, pollIntervalMs = 30)

    private suspend fun awaitInitial(file: File, repo: FileAgentStatusRepository, emissions: MutableList<Map<String, AgentStatusEntry>>) {
        withTimeoutOrNull(2000) {
            while (emissions.isEmpty()) delay(10)
        }
    }

    @Test
    fun `reports existing statuses from file`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        file.writeText(
            line("s1", "working", "UserPromptSubmit") + "\n" +
                line("s2", "waiting", "Notification", message = "hi") + "\n",
        )
        val repo = repository(file)
        val emissions = mutableListOf<Map<String, AgentStatusEntry>>()
        val job = launch(Dispatchers.IO) { repo.observe().collect { emissions.add(it) } }
        awaitInitial(file, repo, emissions)
        job.cancel()

        val initial = emissions.first()
        assertEquals(AgentStatus.WORKING, initial["s1"]?.status)
        assertEquals("UserPromptSubmit", initial["s1"]?.event)
        assertEquals(AgentStatus.WAITING, initial["s2"]?.status)
        assertEquals("hi", initial["s2"]?.message)
        assertNull(initial["s1"]?.toolName)
    }

    @Test
    fun `updates when lines are appended`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        file.writeText(line("s1", "idle", "SessionStart") + "\n")
        val repo = repository(file)
        val emissions = mutableListOf<Map<String, AgentStatusEntry>>()
        val job = launch(Dispatchers.IO) { repo.observe().collect { emissions.add(it) } }
        awaitInitial(file, repo, emissions)

        file.appendText(line("s2", "waiting", "Notification", tool = "Bash") + "\n")
        withTimeoutOrNull(2000) {
            while (emissions.none { it.containsKey("s2") }) delay(10)
        }
        job.cancel()

        val last = emissions.last()
        assertEquals(AgentStatus.WAITING, last["s2"]?.status)
        assertEquals("Bash", last["s2"]?.toolName)
    }

    @Test
    fun `ended drops a session status`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        file.writeText(line("s1", "waiting", "Notification", message = "hi") + "\n")
        val repo = repository(file)
        val emissions = mutableListOf<Map<String, AgentStatusEntry>>()
        val job = launch(Dispatchers.IO) { repo.observe().collect { emissions.add(it) } }
        awaitInitial(file, repo, emissions)

        file.appendText(line("s1", "ended", "SessionEnd") + "\n")
        withTimeoutOrNull(2000) {
            while (emissions.none { !it.containsKey("s1") }) delay(10)
        }
        job.cancel()

        assertNull(emissions.last()["s1"])
    }

    @Test
    fun `does not swallow a partially written trailing line`() = runBlocking {
        val file = File(tmp, "status.jsonl")
        // Truncated JSON with no trailing newline: simulates the hook mid-write.
        file.writeText("""{"timestamp":1,"session_id":"s1","status":"work""")
        val repo = repository(file)
        val emissions = mutableListOf<Map<String, AgentStatusEntry>>()
        val job = launch(Dispatchers.IO) { repo.observe().collect { emissions.add(it) } }
        delay(150)
        // The partial line must not be emitted; it is rewound and retried.
        assertTrue(emissions.none { it.containsKey("s1") })
        // Finish the line now; the next poll must pick it up.
        file.appendText("""ing","event":"UserPromptSubmit"}""" + "\n")
        withTimeoutOrNull(2000) {
            while (emissions.none { it.containsKey("s1") }) delay(10)
        }
        job.cancel()

        val last = emissions.last()
        assertEquals(AgentStatus.WORKING, last["s1"]?.status)
    }
}
