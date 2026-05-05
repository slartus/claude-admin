package dev.claudeadmin.data.util

import java.io.File

object AppDirs {
    val root: File by lazy {
        File(System.getProperty("user.home"), ".claude-admin").apply { mkdirs() }
    }

    val projectsFile: File get() = File(root, "projects.json")

    val projectGroupsFile: File get() = File(root, "project-groups.json")

    val iconsCacheDir: File get() = File(root, "cache/icons")

    val userHome: File get() = File(System.getProperty("user.home"))

    val userClaudeDir: File get() = File(userHome, ".claude")

    val userOpenCodeDir: File get() = File(userHome, ".local/share/opencode")

    val userOpenCodeConfigDir: File get() = File(userHome, ".config/opencode")

    val userOpenCodeAltDir: File get() = File(userHome, ".opencode")
}
