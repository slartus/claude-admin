package dev.claudeadmin.data.opencode

import dev.claudeadmin.domain.model.AiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OpenCodeSessionRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `opencode sessions populate from real database`() {
        assumeTrue(sqliteUsable(), "sqlite3 not available")
        val db = OpenCodeDb.findDb()
        assumeTrue(db != null, "opencode DB not found; skipping test")

        val repo = OpenCodeSessionRepository(scope)
        val sessions = runBlocking {
            withTimeoutOrNull(30_000) { repo.observeAll().first { it.isNotEmpty() } }
        }

        assertTrue(sessions != null, "opencode sessions did not populate within 30s")
        val result = sessions ?: return
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.provider == AiProvider.OPENCODE })
        assertTrue(result.any { it.cwd.contains("claude-admin") })
    }

    private fun sqliteUsable(): Boolean = runCatching {
        val process = ProcessBuilder("sqlite3", "--version").redirectErrorStream(true).start()
        process.waitFor(5, TimeUnit.SECONDS)
    }.getOrDefault(false)
}
