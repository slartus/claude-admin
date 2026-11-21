package dev.claudeadmin.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.ClaudeApiConfig
import dev.claudeadmin.domain.model.OpenCodeProviderConfig
import dev.claudeadmin.domain.model.ProjectApiKeys
import dev.claudeadmin.domain.model.ProjectDetails
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

private data class ClaudePreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val keyEnvVar: String,
)

private val CLAUDE_PRESETS = listOf(
    ClaudePreset("anthropic", "Anthropic", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
    ClaudePreset(
        "deepseek",
        "DeepSeek",
        "https://api.deepseek.com/anthropic",
        "deepseek-chat",
        ClaudeApiConfig.AUTH_TOKEN_ENV_VAR,
    ),
    ClaudePreset(
        "openrouter",
        "OpenRouter",
        "https://openrouter.ai/api/v1",
        "",
        ClaudeApiConfig.AUTH_TOKEN_ENV_VAR,
    ),
)

private fun ClaudePreset.match(config: ClaudeApiConfig): Boolean =
    config.baseUrl == baseUrl && config.apiKeyEnvVar == keyEnvVar && config.model == model

private fun ClaudePreset.represent(config: ClaudeApiConfig): Boolean =
    config.baseUrl == baseUrl && config.apiKeyEnvVar == keyEnvVar

private fun presetFor(config: ClaudeApiConfig): ClaudePreset =
    CLAUDE_PRESETS.firstOrNull { it.match(config) }
        ?: CLAUDE_PRESETS.firstOrNull { it.represent(config) }
        ?: ClaudePreset(
            id = "manual",
            label = "Manual",
            baseUrl = config.baseUrl,
            model = config.model,
            keyEnvVar = config.apiKeyEnvVar,
        )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysCard(
    details: ProjectDetails,
    saving: Boolean,
    saveMessage: String?,
    onSave: (ProjectApiKeys) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val initial = details.apiKeys
    var presetId by remember(initial) { mutableStateOf(presetFor(initial?.claude ?: ClaudeApiConfig()).id) }
    var apiKey by remember(initial) { mutableStateOf(initial?.claude?.apiKey.orEmpty()) }
    var baseUrl by remember(initial) { mutableStateOf(initial?.claude?.baseUrl.orEmpty()) }
    var model by remember(initial) { mutableStateOf(initial?.claude?.model.orEmpty()) }

    var openCode by remember(initial) {
        mutableStateOf(initial?.openCodeProviders.orEmpty())
    }

    fun applyPreset(preset: ClaudePreset) {
        presetId = preset.id
        baseUrl = preset.baseUrl
        model = preset.model
    }

    val currentPreset = CLAUDE_PRESETS.firstOrNull { it.id == presetId }
    val keyEnvVar = currentPreset?.keyEnvVar
        ?: presetFor(ClaudeApiConfig(apiKey = apiKey, baseUrl = baseUrl, model = model)).keyEnvVar

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "API Keys",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Claude Code — .claude/settings.local.json",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PresetPicker(
            presets = CLAUDE_PRESETS,
            selectedId = presetId,
            onSelect = { applyPreset(it) },
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            placeholder = { Text("Например https://api.deepseek.com/anthropic") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model (опционально)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(
            text = "OpenCode — приватные ключи (инжектятся при запуске)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Ключ хранится приватно в приложении и передаётся в opencode через окружение. Модели и название провайдера берутся из глобального ~/.config/opencode/opencode.json.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        openCode.forEachIndexed { index, provider ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = provider.providerId,
                        onValueChange = { newId -> openCode = openCode.replaceAt(index, provider.copy(providerId = newId)) },
                        label = { Text("Provider id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = provider.apiKey,
                        onValueChange = { newKey -> openCode = openCode.replaceAt(index, provider.copy(apiKey = newKey)) },
                        label = { Text("API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = provider.baseUrl,
                        onValueChange = { newUrl -> openCode = openCode.replaceAt(index, provider.copy(baseUrl = newUrl)) },
                        label = { Text("Base URL (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { openCode = openCode.withoutAt(index) }) {
                    androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = "Remove provider")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddProviderMenu(onAdd = { preset ->
                if (openCode.none { it.providerId == preset.id }) {
                    openCode = openCode + OpenCodeProviderConfig(providerId = preset.id, baseUrl = preset.baseUrl)
                }
            })
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !saving,
                onClick = {
                    onSave(
                        ProjectApiKeys(
                            projectPath = details.project.path,
                            claude = ClaudeApiConfig(
                                apiKey = apiKey,
                                apiKeyEnvVar = keyEnvVar,
                                baseUrl = baseUrl,
                                model = model,
                            ),
                            openCodeProviders = openCode,
                        ),
                    )
                },
            ) {
                Text(if (saving) "Сохранение…" else "Сохранить")
            }
        }

        saveMessage?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissMessage) { Text("OK") }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetPicker(
    presets: List<ClaudePreset>,
    selectedId: String,
    onSelect: (ClaudePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = presets.firstOrNull { it.id == selectedId }?.label ?: "Manual"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        androidx.compose.material3.OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Провайдер") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderMenu(onAdd: (ClaudePreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "Добавить провайдера",
            onValueChange = {},
            readOnly = true,
            label = { Text("OpenCode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .width(200.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            OPENCODE_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        expanded = false
                        onAdd(preset.copy(baseUrl = ""))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Произвольный") },
                onClick = {
                    expanded = false
                    onAdd(ClaudePreset(id = "", label = "empty", baseUrl = "", model = "", keyEnvVar = ""))
                },
            )
        }
    }
}

private val OPENCODE_PRESETS = listOf(
    ClaudePreset("gpustack", "gpustack", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
    ClaudePreset("anthropic", "Anthropic", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
    ClaudePreset("openai", "OpenAI", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
    ClaudePreset("deepseek", "DeepSeek", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
    ClaudePreset("openrouter", "OpenRouter", "", "", ClaudeApiConfig.DEFAULT_KEY_ENV_VAR),
)

private fun List<OpenCodeProviderConfig>.replaceAt(index: Int, value: OpenCodeProviderConfig): List<OpenCodeProviderConfig> =
    toMutableList().apply { set(index, value) }

private fun List<OpenCodeProviderConfig>.withoutAt(index: Int): List<OpenCodeProviderConfig> =
    toMutableList().apply { removeAt(index) }
