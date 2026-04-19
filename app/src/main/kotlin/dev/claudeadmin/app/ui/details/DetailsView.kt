package dev.claudeadmin.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.Agent
import dev.claudeadmin.domain.model.AgentScope
import dev.claudeadmin.domain.model.ClaudeMd
import dev.claudeadmin.domain.model.ClaudeSettings
import dev.claudeadmin.domain.model.Command
import dev.claudeadmin.domain.model.CommandScope
import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.presentation.root.DetailsState

@Composable
fun DetailsView(
    state: DetailsState,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            DetailsState.Empty -> EmptyText("No details")
            DetailsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DetailsState.Error -> EmptyText(state.message)
            is DetailsState.Loaded -> Content(
                details = state.details,
                onOpenFile = onOpenFile,
                onRevealInFinder = onRevealInFinder,
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Content(
    details: ProjectDetails,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        ProjectInfoPanel(
            projectName = details.project.name,
            claudeMd = details.claudeMd,
            settingsLocal = details.settingsLocal,
            onOpenFile = onOpenFile,
            onRevealInFinder = onRevealInFinder,
            modifier = Modifier.weight(1f),
        )
        Divider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        AgentsAndCommandsPanel(
            agents = details.agents,
            commands = details.commands,
            onOpenFile = onOpenFile,
            onRevealInFinder = onRevealInFinder,
            modifier = Modifier.width(340.dp),
        )
    }
}

@Composable
private fun AgentsAndCommandsPanel(
    agents: List<Agent>,
    commands: List<Command>,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "Agents (${agents.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.padding(4.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(agents, key = { it.scope.name + it.path }) {
                AgentCard(agent = it, onOpenFile = onOpenFile, onRevealInFinder = onRevealInFinder)
            }
        }
        Spacer(Modifier.padding(8.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.padding(8.dp))
        Text(
            text = "Commands (${commands.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.padding(4.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(commands, key = { it.scope.name + it.path }) {
                CommandCard(command = it, onOpenFile = onOpenFile, onRevealInFinder = onRevealInFinder)
            }
        }
    }
}

@Composable
private fun ProjectInfoPanel(
    projectName: String,
    claudeMd: ClaudeMd?,
    settingsLocal: ClaudeSettings?,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(projectName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(4.dp))
        ClaudeMdSection(claudeMd = claudeMd, onOpenFile = onOpenFile, onRevealInFinder = onRevealInFinder)
        Spacer(Modifier.padding(8.dp))
        SettingsLocalSection(
            settings = settingsLocal,
            onOpenFile = onOpenFile,
            onRevealInFinder = onRevealInFinder,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    filePath: String?,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (filePath != null) {
            OpenFileButtons(path = filePath, onOpenFile = onOpenFile, onRevealInFinder = onRevealInFinder)
        }
    }
}

@Composable
private fun OpenFileButtons(
    path: String,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
    sizeDp: Int = 28,
) {
    val iconSize = (sizeDp - 12).dp
    IconButton(onClick = { onOpenFile(path) }, modifier = Modifier.size(sizeDp.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open in default app",
            modifier = Modifier.size(iconSize),
        )
    }
    IconButton(onClick = { onRevealInFinder(path) }, modifier = Modifier.size(sizeDp.dp)) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Reveal in Finder",
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ClaudeMdSection(
    claudeMd: ClaudeMd?,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    SectionHeader(
        title = "CLAUDE.md",
        filePath = claudeMd?.path,
        onOpenFile = onOpenFile,
        onRevealInFinder = onRevealInFinder,
    )
    Spacer(Modifier.padding(4.dp))
    if (claudeMd == null) {
        Text("(no CLAUDE.md in this folder)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = claudeMd.content,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
    if (claudeMd.imports.isNotEmpty()) {
        Spacer(Modifier.padding(8.dp))
        Text("Imports:", fontWeight = FontWeight.SemiBold)
        claudeMd.imports.forEach { imp ->
            Text(imp, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsLocalSection(
    settings: ClaudeSettings?,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    SectionHeader(
        title = ".claude/settings.local.json",
        filePath = settings?.path,
        onOpenFile = onOpenFile,
        onRevealInFinder = onRevealInFinder,
    )
    Spacer(Modifier.padding(4.dp))
    if (settings == null) {
        Text(
            text = "(no .claude/settings.local.json in this folder)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = settings.content,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun AgentCard(
    agent: Agent,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ScopeBadge(agent.scope)
                OpenFileButtons(
                    path = agent.path,
                    onOpenFile = onOpenFile,
                    onRevealInFinder = onRevealInFinder,
                    sizeDp = 24,
                )
            }
            val description = agent.description
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.padding(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (agent.tools.isNotEmpty()) {
                Spacer(Modifier.padding(2.dp))
                Text(
                    text = "tools: ${agent.tools.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ScopeBadge(scope: AgentScope) {
    val (label, color) = when (scope) {
        AgentScope.PROJECT -> "project" to MaterialTheme.colorScheme.primary
        AgentScope.USER -> "user" to MaterialTheme.colorScheme.secondary
    }
    ScopeBadgeContent(label, color)
}

@Composable
private fun ScopeBadge(scope: CommandScope) {
    val (label, color) = when (scope) {
        CommandScope.PROJECT -> "project" to MaterialTheme.colorScheme.primary
        CommandScope.USER -> "user" to MaterialTheme.colorScheme.secondary
    }
    ScopeBadgeContent(label, color)
}

@Composable
private fun ScopeBadgeContent(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, modifier = Modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CommandCard(
    command: Command,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "/${command.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ScopeBadge(command.scope)
                OpenFileButtons(
                    path = command.path,
                    onOpenFile = onOpenFile,
                    onRevealInFinder = onRevealInFinder,
                    sizeDp = 24,
                )
            }
            val description = command.description
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.padding(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val hint = command.argumentHint
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.padding(2.dp))
                Text(
                    text = "args: $hint",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
