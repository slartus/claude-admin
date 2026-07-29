package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.ClaudeUserSettings

@Composable
fun TerminalProviderDialog(
    claudeUserSettings: List<ClaudeUserSettings>,
    showOpenCode: Boolean = true,
    onResult: (AiProvider, claudeSettingsPath: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                ProviderRow(
                    displayName = AiProvider.CLAUDE.displayName,
                    terminalLabel = AiProvider.CLAUDE.terminalLabel,
                    onClick = { onResult(AiProvider.CLAUDE, null) },
                )
                claudeUserSettings.forEach { settings ->
                    ProviderRow(
                        displayName = "${AiProvider.CLAUDE.displayName} · ${settings.name}",
                        terminalLabel = AiProvider.CLAUDE.terminalLabel,
                        onClick = { onResult(AiProvider.CLAUDE, settings.path) },
                    )
                }
                if (showOpenCode) {
                    ProviderRow(
                        displayName = AiProvider.OPENCODE.displayName,
                        terminalLabel = AiProvider.OPENCODE.terminalLabel,
                        onClick = { onResult(AiProvider.OPENCODE, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    displayName: String,
    terminalLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = terminalLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
