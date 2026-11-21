package dev.claudeadmin.data.apikeys

import dev.claudeadmin.domain.model.ClaudeApiConfig
import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import dev.claudeadmin.domain.model.ProjectApiKeys
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileProjectApiKeysRepositoryTest {

    @TempDir
    lateinit var tmp: File

    private val repo = FileProjectApiKeysRepository()

    @Test
    fun `load returns empty config when no files exist`() = runBlocking {
        val keys = repo.load(tmp.path)
        assertTrue(keys.claude.apiKey.isBlank())
        assertTrue(keys.openCodeProviders.isEmpty())
    }

    @Test
    fun `load reads claude env and opencode providers`() = runBlocking {
        File(tmp, ".claude").mkdirs()
        File(tmp, ".claude/settings.local.json").writeText(
            """
            {
              "env": {
                "ANTHROPIC_API_KEY": "sk-ant-123",
                "ANTHROPIC_BASE_URL": "https://api.example.com",
                "ANTHROPIC_MODEL": "my-model"
              }
            }
            """.trimIndent(),
        )
        File(tmp, "opencode.json").writeText(
            """
            {
              "providers": {
                "anthropic": {
                  "settings": { "apiKey": "sk-ant-456", "baseURL": "https://api.anthropic.com" }
                },
                "deepseek": {
                  "models": { "deepseek-chat": {} },
                  "settings": { "apiKey": "sk-789" }
                }
              }
            }
            """.trimIndent(),
        )

        val keys = repo.load(tmp.path)

        assertEquals("sk-ant-123", keys.claude.apiKey)
        assertEquals(ClaudeApiConfig.DEFAULT_KEY_ENV_VAR, keys.claude.apiKeyEnvVar)
        assertEquals("https://api.example.com", keys.claude.baseUrl)
        assertEquals("my-model", keys.claude.model)

        assertEquals(2, keys.openCodeProviders.size)
        val anthropic = keys.openCodeProviders.first { it.providerId == "anthropic" }
        assertEquals("sk-ant-456", anthropic.apiKey)
        assertEquals("https://api.anthropic.com", anthropic.baseUrl)
        val deepseek = keys.openCodeProviders.first { it.providerId == "deepseek" }
        assertEquals("sk-789", deepseek.apiKey)
    }

    @Test
    fun `detects auth token env var for claude`() = runBlocking {
        File(tmp, ".claude").mkdirs()
        File(tmp, ".claude/settings.local.json").writeText(
            """{ "env": { "ANTHROPIC_AUTH_TOKEN": "tok-1", "ANTHROPIC_BASE_URL": "https://api.deepseek.com/anthropic" } }""",
        )
        val keys = repo.load(tmp.path)
        assertEquals("tok-1", keys.claude.apiKey)
        assertEquals(ClaudeApiConfig.AUTH_TOKEN_ENV_VAR, keys.claude.apiKeyEnvVar)
        assertEquals("https://api.deepseek.com/anthropic", keys.claude.baseUrl)
    }

    @Test
    fun `save writes claude env preserving other keys`() = runBlocking {
        File(tmp, ".claude").mkdirs()
        File(tmp, ".claude/settings.local.json").writeText(
            """{ "env": { "SOME_OTHER_VAR": "keep", "ANTHROPIC_API_KEY": "old" } }""",
        )

        val keys = ProjectApiKeys(
            projectPath = tmp.path,
            claude = ClaudeApiConfig(
                apiKey = "sk-new",
                apiKeyEnvVar = ClaudeApiConfig.DEFAULT_KEY_ENV_VAR,
                baseUrl = "https://api.deepseek.com/anthropic",
                model = "deepseek-chat",
            ),
        )
        repo.save(tmp.path, keys)

        val text = File(tmp, ".claude/settings.local.json").readText()
        assertTrue(text.contains("sk-new"))
        assertTrue(text.contains("SOME_OTHER_VAR"))
        assertTrue(text.contains("keep"))
        assertTrue(text.contains("https://api.deepseek.com/anthropic"))
        assertTrue(text.contains("deepseek-chat"))
        assertTrue(!text.contains("old"))
    }

    @Test
    fun `save switches key env var when preset changes`() = runBlocking {
        File(tmp, ".claude").mkdirs()
        File(tmp, ".claude/settings.local.json").writeText(
            """{ "env": { "ANTHROPIC_API_KEY": "old-key", "ANTHROPIC_BASE_URL": "" } }""",
        )

        val keys = ProjectApiKeys(
            projectPath = tmp.path,
            claude = ClaudeApiConfig(
                apiKey = "tok-new",
                apiKeyEnvVar = ClaudeApiConfig.AUTH_TOKEN_ENV_VAR,
                baseUrl = "https://api.deepseek.com/anthropic",
            ),
        )
        repo.save(tmp.path, keys)

        val text = File(tmp, ".claude/settings.local.json").readText()
        assertTrue(text.contains("ANTHROPIC_AUTH_TOKEN"))
        assertTrue(text.contains("tok-new"))
        assertTrue(text.contains("https://api.deepseek.com/anthropic"))
        assertTrue(!text.contains("ANTHROPIC_API_KEY"))
    }

    @Test
    fun `save creates files when missing and round-trips`() = runBlocking {
        val keys = ProjectApiKeys(
            projectPath = tmp.path,
            claude = ClaudeApiConfig(apiKey = "sk-1", baseUrl = "https://x.com", model = "m"),
            openCodeProviders = listOf(
                OpenCodeProviderConfig(providerId = "anthropic", apiKey = "sk-2", baseUrl = "https://api.anthropic.com"),
                OpenCodeProviderConfig(providerId = "deepseek", apiKey = "sk-3"),
            ),
        )
        repo.save(tmp.path, keys)

        assertTrue(File(tmp, ".claude/settings.local.json").isFile)
        assertTrue(File(tmp, ".opencode.json").isFile)

        val loaded = repo.load(tmp.path)
        assertEquals("sk-1", loaded.claude.apiKey)
        assertEquals(2, loaded.openCodeProviders.size)
        assertEquals("sk-2", loaded.openCodeProviders.first { it.providerId == "anthropic" }.apiKey)
    }

    @Test
    fun `save opencode preserves unrelated fields and removes absent providers`() = runBlocking {
        File(tmp, "opencode.json").writeText(
            """
            {
              "customField": 1,
              "providers": {
                "anthropic": { "settings": { "apiKey": "keep", "baseURL": "https://a.com" } },
                "openai": { "models": { "gpt-4o": {} }, "settings": { "apiKey": "will-be-removed" } }
              }
            }
            """.trimIndent(),
        )

        val keys = ProjectApiKeys(
            projectPath = tmp.path,
            openCodeProviders = listOf(
                OpenCodeProviderConfig(providerId = "anthropic", apiKey = "sk-updated", baseUrl = "https://b.com"),
            ),
        )
        repo.save(tmp.path, keys)

        val text = File(tmp, "opencode.json").readText()
        assertTrue(text.contains("customField"))
        assertTrue(text.contains("sk-updated"))
        assertTrue(text.contains("anthropic"))
        assertTrue(text.contains("https://b.com"))
        assertTrue(!text.contains("openai"))
        assertTrue(!text.contains("will-be-removed"))
        assertTrue(!text.contains("keep"))
    }

    @Test
    fun `clearing claude env removes key vars`() = runBlocking {
        File(tmp, ".claude").mkdirs()
        File(tmp, ".claude/settings.local.json").writeText(
            """{ "env": { "ANTHROPIC_API_KEY": "sk", "ANTHROPIC_BASE_URL": "https://x.com" } }""",
        )

        repo.save(tmp.path, ProjectApiKeys(projectPath = tmp.path, claude = ClaudeApiConfig())) // empty

        val text = File(tmp, ".claude/settings.local.json").readText()
        assertTrue(!text.contains("ANTHROPIC_API_KEY"))
        assertTrue(!text.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun `skip blank provider id on save`() = runBlocking {
        val keys = ProjectApiKeys(
            projectPath = tmp.path,
            openCodeProviders = listOf(
                OpenCodeProviderConfig(providerId = "", apiKey = "sk"),
                OpenCodeProviderConfig(providerId = "anthropic", apiKey = "sk-ok"),
            ),
        )
        repo.save(tmp.path, keys)
        val text = File(tmp, ".opencode.json").readText()
        assertTrue(!text.contains("anthropic\n"))
        assertTrue(text.contains("sk-ok"))
    }

    @Test
    fun `keyless custom provider survives save round-trip`() = runBlocking {
        File(tmp, "opencode.json").writeText(
            """
            {
              "providers": {
                "my-custom": { "npm": "@ai-sdk/custom", "models": { "m1": {} } },
                "anthropic": { "settings": { "apiKey": "sk-1" } }
              }
            }
            """.trimIndent(),
        )

        val loaded = repo.load(tmp.path)
        val myCustom = loaded.openCodeProviders.first { it.providerId == "my-custom" }
        assertTrue(myCustom.apiKey.isBlank())
        assertTrue(myCustom.baseUrl.isBlank())

        repo.save(tmp.path, loaded)

        val text = File(tmp, "opencode.json").readText()
        assertTrue(text.contains("my-custom"))
        assertTrue(text.contains("@ai-sdk/custom"))
        assertTrue(text.contains("m1"))
        assertTrue(text.contains("sk-1"))
    }

    @Test
    fun `prefers dot opencode json over plain when both exist`() = runBlocking {
        File(tmp, "opencode.json").writeText(
            """{ "providers": { "anthropic": { "settings": { "apiKey": "sk-plain" } } } }""",
        )
        File(tmp, ".opencode.json").writeText(
            """{ "providers": { "anthropic": { "settings": { "apiKey": "sk-dot" } } } }""",
        )

        val loaded = repo.load(tmp.path)
        assertEquals("sk-dot", loaded.openCodeProviders.first { it.providerId == "anthropic" }.apiKey)

        repo.save(tmp.path, ProjectApiKeys(projectPath = tmp.path, openCodeProviders = listOf(
            OpenCodeProviderConfig(providerId = "anthropic", apiKey = "sk-new"),
        )))

        val text = File(tmp, ".opencode.json").readText()
        assertTrue(text.contains("sk-new"))
        assertTrue(!text.contains("sk-dot"))
    }
}
