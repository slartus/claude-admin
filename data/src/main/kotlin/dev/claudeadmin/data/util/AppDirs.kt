package dev.claudeadmin.data.util

import java.io.File

object AppDirs {
    val root: File by lazy {
        File(System.getProperty("user.home"), ".claude-admin").apply { mkdirs() }
    }

    val projectsFile: File get() = File(root, "projects.json")

    val userHome: File get() = File(System.getProperty("user.home"))

    val userClaudeDir: File get() = File(userHome, ".claude")
}
