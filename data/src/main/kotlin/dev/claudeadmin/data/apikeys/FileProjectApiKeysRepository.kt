package dev.claudeadmin.data.apikeys

import dev.claudeadmin.domain.model.ClaudeApiConfig
import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import dev.claudeadmin.domain.model.ProjectApiKeys
import dev.claudeadmin.domain.repository.ProjectApiKeysRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

class FileProjectApiKeysRepository : ProjectApiKeysRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun load(projectPath: String): ProjectApiKeys = withContext(Dispatchers.IO) {
        val claude = loadClaude(projectPath)
        val openCode = loadOpenCode(projectPath)
        ProjectApiKeys(
            projectPath = projectPath,
            claude = claude,
            openCodeProviders = openCode,
        )
    }

    override suspend fun save(projectPath: String, keys: ProjectApiKeys): Unit = withContext(Dispatchers.IO) {
        saveClaude(projectPath, keys.claude)
        saveOpenCode(projectPath, keys.openCodeProviders)
    }

    private fun loadClaude(projectPath: String): ClaudeApiConfig {
        val file = claudeSettingsFile(projectPath)
        if (!file.isFile) return ClaudeApiConfig()
        val root = parseObject(file.readText()) ?: return ClaudeApiConfig()
        val env = root["env"]?.jsonObject ?: JsonObject(emptyMap())
        val apiKey = env.contentOrBlank(ClaudeApiConfig.DEFAULT_KEY_ENV_VAR)
        val authToken = env.contentOrBlank(ClaudeApiConfig.AUTH_TOKEN_ENV_VAR)
        val apiKeyEnvVar = when {
            apiKey.isNotBlank() -> ClaudeApiConfig.DEFAULT_KEY_ENV_VAR
            authToken.isNotBlank() -> ClaudeApiConfig.AUTH_TOKEN_ENV_VAR
            else -> ClaudeApiConfig.DEFAULT_KEY_ENV_VAR
        }
        val resolvedKey = if (apiKeyEnvVar == ClaudeApiConfig.DEFAULT_KEY_ENV_VAR) apiKey else authToken
        return ClaudeApiConfig(
            apiKey = resolvedKey,
            apiKeyEnvVar = apiKeyEnvVar,
            baseUrl = env.contentOrBlank(ClaudeApiConfig.BASE_URL_ENV_VAR),
            model = env.contentOrBlank(ClaudeApiConfig.MODEL_ENV_VAR),
        )
    }

    private fun saveClaude(projectPath: String, config: ClaudeApiConfig) {
        val root = readOrNew(claudeSettingsFile(projectPath))
        val env = (root["env"]?.jsonObject ?: JsonObject(emptyMap())).toMutableMap()

        val keyEnvVar = config.apiKeyEnvVar.ifBlank { ClaudeApiConfig.DEFAULT_KEY_ENV_VAR }
        if (config.apiKey.isNotBlank()) {
            env[keyEnvVar] = JsonPrimitive(config.apiKey)
            val other = if (keyEnvVar == ClaudeApiConfig.DEFAULT_KEY_ENV_VAR) {
                ClaudeApiConfig.AUTH_TOKEN_ENV_VAR
            } else {
                ClaudeApiConfig.DEFAULT_KEY_ENV_VAR
            }
            env.remove(other)
        } else {
            env.remove(ClaudeApiConfig.DEFAULT_KEY_ENV_VAR)
            env.remove(ClaudeApiConfig.AUTH_TOKEN_ENV_VAR)
        }

        if (config.baseUrl.isNotBlank()) {
            env[ClaudeApiConfig.BASE_URL_ENV_VAR] = JsonPrimitive(config.baseUrl)
        } else {
            env.remove(ClaudeApiConfig.BASE_URL_ENV_VAR)
        }
        if (config.model.isNotBlank()) {
            env[ClaudeApiConfig.MODEL_ENV_VAR] = JsonPrimitive(config.model)
        } else {
            env.remove(ClaudeApiConfig.MODEL_ENV_VAR)
        }

        val updated = JsonObject(env)
        val newRoot = JsonObject(root.toMutableMap().apply { this["env"] = updated })
        writeJson(claudeSettingsFile(projectPath), newRoot)
    }

    private fun loadOpenCode(projectPath: String): List<OpenCodeProviderConfig> {
        val file = openCodeFile(projectPath) ?: return emptyList()
        val root = parseObject(file.readText()) ?: return emptyList()
        val providers = root["providers"]?.asObject() ?: return emptyList()
        return providers.mapNotNull { (id, value) ->
            val provider = value.asObject() ?: return@mapNotNull null
            val settings = provider["settings"]?.asObject() ?: JsonObject(emptyMap())
            OpenCodeProviderConfig(
                providerId = id,
                apiKey = settings.contentOrBlank("apiKey"),
                baseUrl = settings.contentOrBlank("baseURL"),
            )
        }
    }

    private fun saveOpenCode(projectPath: String, providers: List<OpenCodeProviderConfig>) {
        val file = openCodeFile(projectPath) ?: File(projectPath, ".opencode.json")
        val root = readOrNew(file)
        val existingProviders = (root["providers"]?.asObject() ?: JsonObject(emptyMap())).toMutableMap()

        val retainedIds = providers.mapNotNull { it.providerId.takeIf(String::isNotBlank) }.toSet()
        existingProviders.keys.retainAll(retainedIds.intersect(existingProviders.keys))

        providers.forEach { config ->
            val id = config.providerId
            if (id.isBlank()) return@forEach
            val provider = (existingProviders[id]?.asObject() ?: JsonObject(emptyMap())).toMutableMap()
            val settings = (provider["settings"]?.asObject() ?: JsonObject(emptyMap())).toMutableMap()

            if (config.apiKey.isNotBlank()) {
                settings["apiKey"] = JsonPrimitive(config.apiKey)
            } else {
                settings.remove("apiKey")
            }
            if (config.baseUrl.isNotBlank()) {
                settings["baseURL"] = JsonPrimitive(config.baseUrl)
            } else {
                settings.remove("baseURL")
            }

            provider["settings"] = JsonObject(settings)
            existingProviders[id] = JsonObject(provider)
        }

        val newRoot = root.toMutableMap().apply { this["providers"] = JsonObject(existingProviders) }
        writeJson(file, JsonObject(newRoot))
    }

    private fun claudeSettingsFile(projectPath: String): File = File(projectPath, ".claude/settings.local.json")

    private fun openCodeFile(projectPath: String): File? {
        val dot = File(projectPath, ".opencode.json")
        val canonical = File(projectPath, "opencode.json")
        return when {
            dot.isFile -> dot
            canonical.isFile -> canonical
            else -> null
        }
    }

    private fun readOrNew(file: File): JsonObject {
        if (!file.isFile) return JsonObject(emptyMap())
        return parseObject(file.readText()) ?: JsonObject(emptyMap())
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()?.jsonObject

    private fun writeJson(file: File, obj: JsonObject) {
        file.parentFile?.mkdirs()
        val text = json.encodeToString(JsonObject.serializer(), obj)
        file.writeText(text)
    }

    private fun JsonObject.contentOrBlank(key: String): String =
        when (val el = this[key]) {
            is JsonPrimitive -> if (el.isString) el.content else ""
            else -> ""
        }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
