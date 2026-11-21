package dev.claudeadmin.domain.model

data class ClaudeApiConfig(
    val apiKey: String = "",
    val apiKeyEnvVar: String = DEFAULT_KEY_ENV_VAR,
    val baseUrl: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override fun toString(): String =
        "ClaudeApiConfig(apiKey=${apiKey.masked()}, apiKeyEnvVar=$apiKeyEnvVar, baseUrl=$baseUrl, model=$model)"

    companion object {
        const val DEFAULT_KEY_ENV_VAR = "ANTHROPIC_API_KEY"
        const val AUTH_TOKEN_ENV_VAR = "ANTHROPIC_AUTH_TOKEN"
        const val BASE_URL_ENV_VAR = "ANTHROPIC_BASE_URL"
        const val MODEL_ENV_VAR = "ANTHROPIC_MODEL"
    }
}

data class OpenCodeProviderConfig(
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override fun toString(): String =
        "OpenCodeProviderConfig(providerId=$providerId, apiKey=${apiKey.masked()}, baseUrl=$baseUrl)"
}

data class ProjectApiKeys(
    val projectPath: String,
    val claude: ClaudeApiConfig = ClaudeApiConfig(),
    val openCodeProviders: List<OpenCodeProviderConfig> = emptyList(),
) {
    companion object {
        fun empty(projectPath: String): ProjectApiKeys = ProjectApiKeys(projectPath = projectPath)
    }
}

private fun String.masked(): String = if (isBlank()) "" else "<redacted>"
