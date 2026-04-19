package dev.claudeadmin.app.ui.terminal

import com.jediterm.terminal.ui.JediTermWidget
import dev.claudeadmin.domain.model.TerminalSessionId

object TerminalWidgetCache {

    private val widgets = mutableMapOf<TerminalSessionId, JediTermWidget>()

    fun getOrCreate(id: TerminalSessionId, factory: () -> JediTermWidget): JediTermWidget =
        widgets.getOrPut(id, factory)

    fun dispose(id: TerminalSessionId) {
        widgets.remove(id)?.close()
    }

    fun retainOnly(aliveIds: Set<TerminalSessionId>) {
        val toRemove = widgets.keys - aliveIds
        toRemove.forEach { dispose(it) }
    }
}
