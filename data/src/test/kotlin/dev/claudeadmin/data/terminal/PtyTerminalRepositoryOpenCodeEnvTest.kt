package dev.claudeadmin.data.terminal

import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import dev.claudeadmin.domain.model.ProjectApiKeys
import dev.claudeadmin.domain.repository.ProjectApiKeysRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PtyTerminalRepositoryOpenCodeEnvTest {

    private class FakeRepo(private val keys: ProjectApiKeys) : ProjectApiKeysRepository {
        override suspend fun load(projectPath: String): ProjectApiKeys = keys
        override suspend fun save(projectPath: String, keys: ProjectApiKeys): Unit = Unit
    }

    @Test
    fun `builds override env when opencode provider has key`() = runBlocking {
        val repo = PtyTerminalRepository(
            projectApiKeys = FakeRepo(
                ProjectApiKeys(
                    projectPath = "proj",
                    openCodeProviders = listOf(OpenCodeProviderConfig("gpustack", apiKey = "sec-1")),
                ),
            ),
        )
        val env = repo.openCodeOverrideEnv(FakeRepo(
            ProjectApiKeys(
                projectPath = "proj",
                openCodeProviders = listOf(OpenCodeProviderConfig("gpustack", apiKey = "sec-1")),
            ),
        ), "proj")

        val content = env[OpenCodeOverrideBuilder.CONFIG_CONTENT_ENV] ?: error("OPENCODE_CONFIG_CONTENT missing")
        assertTrue(content.contains("gpustack"))
        assertTrue(content.contains("sec-1"))
    }

    @Test
    fun `returns empty env when no configured providers`() = runBlocking {
        val repo = PtyTerminalRepository(
            projectApiKeys = FakeRepo(ProjectApiKeys(projectPath = "proj")),
        )
        val env = repo.openCodeOverrideEnv(FakeRepo(ProjectApiKeys(projectPath = "proj")), "proj")
        assertTrue(env.isEmpty())
        assertEquals(emptyMap<String, String>(), env)
    }

    @Test
    fun `returns empty env when store read fails`() = runBlocking {
        object : ProjectApiKeysRepository {
            override suspend fun load(projectPath: String): ProjectApiKeys = throw RuntimeException("corrupt")
            override suspend fun save(projectPath: String, keys: ProjectApiKeys): Unit = Unit
        }.let { failing ->
            val repo = PtyTerminalRepository(projectApiKeys = failing)
            val env = repo.openCodeOverrideEnv(failing, "proj")
            assertTrue(env.isEmpty())
        }
    }
}
