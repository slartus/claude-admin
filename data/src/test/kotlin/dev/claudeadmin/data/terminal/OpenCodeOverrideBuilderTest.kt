package dev.claudeadmin.data.terminal

import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenCodeOverrideBuilderTest {

    @Test
    fun `returns null when no providers`() {
        assertNull(OpenCodeOverrideBuilder.buildConfigContent(emptyList()))
    }

    @Test
    fun `returns null when no provider has key or base url`() {
        val providers = listOf(
            OpenCodeProviderConfig(providerId = "gpustack"),
            OpenCodeProviderConfig(providerId = "empty", baseUrl = ""),
        )
        assertNull(OpenCodeOverrideBuilder.buildConfigContent(providers))
    }

    @Test
    fun `builds provider options with apiKey only`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(OpenCodeProviderConfig(providerId = "gpustack", apiKey = "secret-1")),
        )
        assertEqualsJson("""{"provider":{"gpustack":{"options":{"apiKey":"secret-1"}}}}""", content)
    }

    @Test
    fun `builds provider options with baseURL when apiKey blank`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(OpenCodeProviderConfig(providerId = "x", baseUrl = "https://b.com")),
        )
        assertEqualsJson("""{"provider":{"x":{"options":{"baseURL":"https://b.com"}}}}""", content)
    }

    @Test
    fun `builds provider options with both apiKey and baseURL`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(OpenCodeProviderConfig(providerId = "gpustack", apiKey = "k", baseUrl = "https://api.example.com/v1")),
        )
        assertEqualsJson(
            """{"provider":{"gpustack":{"options":{"apiKey":"k","baseURL":"https://api.example.com/v1"}}}}""",
            content,
        )
    }

    @Test
    fun `skips providers with blank id`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(
                OpenCodeProviderConfig(providerId = "", apiKey = "k"),
                OpenCodeProviderConfig(providerId = "gpustack", apiKey = "ok"),
            ),
        )
        assertEqualsJson("""{"provider":{"gpustack":{"options":{"apiKey":"ok"}}}}""", content)
    }

    @Test
    fun `keeps multiple providers and ignores unconfigured ones`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(
                OpenCodeProviderConfig(providerId = "gpustack", apiKey = "key-1"),
                OpenCodeProviderConfig(providerId = "empty"),
                OpenCodeProviderConfig(providerId = "anthropic", baseUrl = "https://api.anthropic.com"),
            ),
        )
        assertEqualsJson(
            """{"provider":{"gpustack":{"options":{"apiKey":"key-1"}},"anthropic":{"options":{"baseURL":"https://api.anthropic.com"}}}}""",
            content,
        )
    }

    @Test
    fun `deduplicates providers preferring first occurrence`() {
        val content = OpenCodeOverrideBuilder.buildConfigContent(
            listOf(
                OpenCodeProviderConfig(providerId = "gpustack", apiKey = "first"),
                OpenCodeProviderConfig(providerId = "gpustack", apiKey = "second"),
            ),
        )
        assertEqualsJson("""{"provider":{"gpustack":{"options":{"apiKey":"first"}}}}""", content)
    }

    private fun assertEqualsJson(expected: String, actual: String?) {
        assertTrue(actual != null)
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual!!))
    }
}
