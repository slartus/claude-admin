package dev.claudeadmin.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.data.util.MemorySampler
import dev.claudeadmin.data.util.MemorySnapshot
import dev.claudeadmin.domain.model.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SAMPLE_INTERVAL_MS = 2000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusBar(
    terminals: List<TerminalSession>,
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

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TooltipArea(
            tooltip = { TerminalsTooltip(terminals = terminals, snapshot = snapshot) },
            delayMillis = 250,
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.TopCenter,
                alignment = Alignment.BottomCenter,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
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
    }
}

@Composable
private fun TerminalsTooltip(terminals: List<TerminalSession>, snapshot: MemorySnapshot) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.widthIn(min = 180.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (terminals.isEmpty()) {
                Text(
                    text = "No open terminals",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    terminals.forEach { session ->
                        val bytes = snapshot.perTerminalBytes[session.id] ?: 0L
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                            Text(
                                text = formatMb(bytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = (bytes + 512L * 1024L) / (1024L * 1024L)
    return "$mb MB"
}
