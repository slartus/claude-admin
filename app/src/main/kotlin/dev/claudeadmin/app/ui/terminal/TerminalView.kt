package dev.claudeadmin.app.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.jediterm.terminal.ui.JediTermWidget
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.model.TerminalSessionId
import javax.swing.SwingUtilities

@Composable
fun TerminalView(
    sessionId: TerminalSessionId,
    ptyRepo: PtyTerminalRepository,
) {
    val connector = remember(sessionId) { ptyRepo.connectorFor(sessionId) }
    if (connector == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Terminal not available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    key(sessionId.value) {
        val widget = remember(sessionId) {
            TerminalWidgetCache.getOrCreate(sessionId) {
                JediTermWidget(DarkTerminalSettings()).apply {
                    createTerminalSession(connector).start()
                }
            }
        }
        LaunchedEffect(sessionId) {
            SwingUtilities.invokeLater {
                widget.terminalPanel.requestFocusInWindow()
            }
        }
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { widget },
            update = { it.terminalPanel.requestFocusInWindow() },
        )
    }
}
