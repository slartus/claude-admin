package dev.claudeadmin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.claudeadmin.app.ui.util.CenteredDialog
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.data.util.MemorySampler
import dev.claudeadmin.data.util.MemorySnapshot
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val SAMPLE_INTERVAL_MS = 2000L

@Composable
fun StatusBar(
    terminals: List<TerminalSession>,
    projects: List<Project>,
    ptyRepo: PtyTerminalRepository,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(MemorySnapshot.EMPTY) }
    LaunchedEffect(Unit) {
        while (true) {
            val pids = ptyRepo.livePids()
            snapshot = withContext(Dispatchers.IO) { MemorySampler.sample(pids) }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    var showBreakdown by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { showBreakdown = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "App: ${formatMb(snapshot.appBytes)} · Terminals: ${formatMb(snapshot.terminalsBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showBreakdown) {
        val labels = remember(terminals, projects) { buildLabels(terminals, projects) }
        TerminalsBreakdownDialog(
            terminals = terminals,
            labels = labels,
            snapshot = snapshot,
            onDismiss = { showBreakdown = false },
        )
    }
}

@Composable
private fun TerminalsBreakdownDialog(
    terminals: List<TerminalSession>,
    labels: Map<TerminalSessionId, String>,
    snapshot: MemorySnapshot,
    onDismiss: () -> Unit,
) {
    CenteredDialog(
        title = "Memory by terminal",
        size = DpSize(420.dp, 360.dp),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = "Total: ${formatMb(snapshot.terminalsBytes)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (terminals.isEmpty()) {
                Text(
                    text = "No open terminals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    terminals.forEach { session ->
                        val bytes = snapshot.perTerminalBytes[session.id] ?: 0L
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = labels[session.id] ?: session.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                            Text(
                                text = formatMb(bytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private fun buildLabels(
    terminals: List<TerminalSession>,
    projects: List<Project>,
): Map<TerminalSessionId, String> {
    val projectsById = projects.associateBy { it.id }
    return terminals.associate { session ->
        val projectLabel = session.projectId
            ?.let { projectsById[it]?.name }
            ?: File(session.cwd).name.ifBlank { session.cwd }
        session.id to "$projectLabel · ${session.title}"
    }
}

private fun formatMb(bytes: Long): String {
    val mb = (bytes + 512L * 1024L) / (1024L * 1024L)
    return "$mb MB"
}
