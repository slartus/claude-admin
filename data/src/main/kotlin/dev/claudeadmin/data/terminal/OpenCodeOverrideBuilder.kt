package dev.claudeadmin.data.terminal

import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

object OpenCodeOverrideBuilder {

    const val CONFIG_CONTENT_ENV = "OPENCODE_CONFIG_CONTENT"

    /**
     * Строит содержимое для [CONFIG_CONTENT_ENV] в формате старой схемы:
     * { "provider": { "<id>": { "options": { "apiKey": "...", "baseURL": "..." } } } }.
     * Возвращает null, если нет ни одного провайдера с ключом или baseUrl.
     * Модели/name/npm остаются в глобальном конфиге (deep-merge), здесь переопределяется только ключ.
     */
    fun buildConfigContent(providers: List<OpenCodeProviderConfig>): String? {
        val configured = providers
            .filter { it.providerId.isNotBlank() && (it.apiKey.isNotBlank() || it.baseUrl.isNotBlank()) }
            .distinctBy { it.providerId }
        if (configured.isEmpty()) return null
        return buildJsonObject {
            putJsonObject("provider") {
                configured.forEach { config ->
                    putJsonObject(config.providerId) {
                        putJsonObject("options") {
                            if (config.apiKey.isNotBlank()) put("apiKey", JsonPrimitive(config.apiKey))
                            if (config.baseUrl.isNotBlank()) put("baseURL", JsonPrimitive(config.baseUrl))
                        }
                    }
                }
            }
        }.toString()
    }
}
