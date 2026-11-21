package dev.claudeadmin.data.apikeys

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.ClaudeApiConfig
import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import dev.claudeadmin.domain.model.ProjectApiKeys
import dev.claudeadmin.domain.repository.ProjectApiKeysRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class FileProjectApiKeysRepository(
    private val keysFile: File = AppDirs.openCodeKeysFile,
) : ProjectApiKeysRepository {

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
        val map = readKeysMap() ?: return emptyList()
        val list = map[projectPath]?.asArray() ?: return emptyList()
        return list.mapNotNull { el ->
            val obj = el.asObject() ?: return@mapNotNull null
            OpenCodeProviderConfig(
                providerId = obj.contentOrBlank("providerId"),
                apiKey = obj.contentOrBlank("apiKey"),
                baseUrl = obj.contentOrBlank("baseUrl"),
            )
        }
    }

    private fun saveOpenCode(projectPath: String, providers: List<OpenCodeProviderConfig>) {
        val map = (readKeysMap() ?: JsonObject(emptyMap())).toMutableMap()
        val normalized = providers
            .filter { it.providerId.isNotBlank() }
            .map { config ->
                buildJsonObject {
                    put("providerId", JsonPrimitive(config.providerId))
                    put("apiKey", JsonPrimitive(config.apiKey))
                    put("baseUrl", JsonPrimitive(config.baseUrl))
                }
            }
        if (normalized.isEmpty()) {
            map.remove(projectPath)
        } else {
            map[projectPath] = JsonArray(normalized)
        }
        writePrivate(keysFile, JsonObject(map))
    }

    private fun claudeSettingsFile(projectPath: String): File = File(projectPath, ".claude/settings.local.json")

    private fun readKeysMap(): JsonObject? {
        if (!keysFile.isFile) return null
        return parseObject(keysFile.readText())
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

    private fun writePrivate(file: File, obj: JsonObject) {
        writeJson(file, obj)
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
    }

    private fun JsonObject.contentOrBlank(key: String): String =
        when (val el = this[key]) {
            is JsonPrimitive -> if (el.isString) el.content else ""
            else -> ""
        }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray
