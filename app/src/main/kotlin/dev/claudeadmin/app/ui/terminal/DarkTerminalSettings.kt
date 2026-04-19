package dev.claudeadmin.app.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider

class DarkTerminalSettings : DefaultSettingsProvider() {

    override fun getDefaultStyle(): TextStyle =
        TextStyle(
            TerminalColor.rgb(0xD4, 0xD4, 0xD4),
            TerminalColor.rgb(0x1E, 0x1E, 0x1E),
        )

    override fun getSelectionColor(): TextStyle =
        TextStyle(
            TerminalColor.rgb(0xD4, 0xD4, 0xD4),
            TerminalColor.rgb(0x26, 0x4F, 0x78),
        )

    override fun getFoundPatternColor(): TextStyle =
        TextStyle(
            TerminalColor.rgb(0x1E, 0x1E, 0x1E),
            TerminalColor.rgb(0xE5, 0xC0, 0x7B),
        )

    override fun useInverseSelectionColor(): Boolean = false
}
