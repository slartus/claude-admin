package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.ClaudeUserSettings

@Composable
fun TerminalProviderDialog(
    claudeUserSettings: List<ClaudeUserSettings>,
    openCodeSessions: List<AiSession> = emptyList(),
    showOpenCode: Boolean = true,
    onResult: (AiProvider, claudeSettingsPath: String?) -> Unit,
    onContinueLastOpenCode: () -> Unit = {},
    onContinueOpenCodeSession: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var showFlagsDialog by remember { mutableStateOf(false) }
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
                    provider = AiProvider.CLAUDE,
                    displayName = AiProvider.CLAUDE.displayName,
                    onClick = { onResult(AiProvider.CLAUDE, null) },
                )
                claudeUserSettings.forEach { settings ->
                    ProviderRow(
                        provider = AiProvider.CLAUDE,
                        displayName = "${AiProvider.CLAUDE.displayName} · ${settings.name}",
                        onClick = { onResult(AiProvider.CLAUDE, settings.path) },
                    )
                }
                if (showOpenCode) {
                    OpenCodeRow(
                        onLaunch = { onResult(AiProvider.OPENCODE, null) },
                        onShowFlags = { showFlagsDialog = true },
                    )
                }
            }
        }
    }

    if (showFlagsDialog) {
        OpenCodeLaunchDialog(
            sessions = openCodeSessions,
            onContinueLast = onContinueLastOpenCode,
            onContinueSession = onContinueOpenCodeSession,
            onDismiss = { showFlagsDialog = false },
        )
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    displayName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderIcon(provider)
        Spacer(Modifier.width(10.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenCodeRow(
    onLaunch: () -> Unit,
    onShowFlags: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderIcon(AiProvider.OPENCODE)
        Spacer(Modifier.width(10.dp))
        Text(
            text = AiProvider.OPENCODE.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowFlags) {
            Text("Launch")
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ProviderIcon(provider: AiProvider) {
    val (container, content) = providerColors(provider)
    Surface(
        color = container,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = provider.terminalLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = content,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun providerColors(provider: AiProvider): Pair<Color, Color> = when (provider) {
    AiProvider.CLAUDE ->
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
    AiProvider.OPENCODE ->
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.tertiary
}

private enum class OpenCodeFlagStep { MODE, SESSIONS }

@Composable
private fun OpenCodeLaunchDialog(
    sessions: List<AiSession>,
    onContinueLast: () -> Unit,
    onContinueSession: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(OpenCodeFlagStep.MODE) }
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
                when (step) {
                    OpenCodeFlagStep.MODE -> {
                        Text(
                            text = "Launch ${AiProvider.OPENCODE.displayName}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.size(8.dp))
                        LaunchOptionRow(
                            title = "Continue last session",
                            subtitle = "${AiProvider.OPENCODE.cliCommand} --continue",
                            icon = Icons.Default.History,
                            onClick = onContinueLast,
                        )
                        LaunchOptionRow(
                            title = "Continue specific session",
                            subtitle = "${AiProvider.OPENCODE.cliCommand} --session <id>",
                            icon = Icons.Default.List,
                            onClick = { step = OpenCodeFlagStep.SESSIONS },
                        )
                        Spacer(Modifier.size(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                        }
                    }

                    OpenCodeFlagStep.SESSIONS -> {
                        Text(
                            text = "Select session",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.size(8.dp))
                        if (sessions.isEmpty()) {
                            Text(
                                text = "No ${AiProvider.OPENCODE.displayName} sessions found for this project.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                sessions.forEach { session ->
                                    SessionOptionRow(
                                        session = session,
                                        onClick = { onContinueSession(session.id) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { step = OpenCodeFlagStep.MODE }) { Text("Back") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionOptionRow(
    session: AiSession,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.preview.ifBlank { "(no title)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelative(session.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatRelative(timestampMs: Long): String {
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 30L -> "${days}d"
        else -> "${days / 30L}mo"
    }
}
