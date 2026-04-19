package dev.claudeadmin.app.ui.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import javax.swing.JFileChooser

@Composable
fun FolderPickerDialog(onResult: (String?) -> Unit) {
    LaunchedEffect(Unit) {
        val path = withContext(kotlinx.coroutines.Dispatchers.Swing) { pickFolder() }
        onResult(path)
    }
}

private fun pickFolder(): String? {
    val os = System.getProperty("os.name").lowercase()
    return if ("mac" in os) pickMac() else pickSwing()
}

private fun pickMac(): String? {
    val previous = System.getProperty("apple.awt.fileDialogForDirectories")
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
        val dialog = FileDialog(null as java.awt.Frame?, "Select project folder", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return File(dir, file).absolutePath
    } finally {
        if (previous == null) System.clearProperty("apple.awt.fileDialogForDirectories")
        else System.setProperty("apple.awt.fileDialogForDirectories", previous)
    }
}

private fun pickSwing(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select project folder"
        isAcceptAllFileFilterUsed = false
    }
    val code = chooser.showOpenDialog(null)
    return if (code == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}
