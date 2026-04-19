package dev.claudeadmin.data.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.Charset

class Pty4jTtyConnector(
    private val process: PtyProcess,
    charset: Charset,
    private val displayName: String = "Local",
) : ProcessTtyConnector(process, charset) {

    override fun getName(): String = displayName

    override fun resize(termSize: TermSize) {
        process.winSize = WinSize(termSize.columns, termSize.rows)
    }
}
