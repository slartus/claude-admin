package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.presentation.root.RootState

@Composable
internal fun SidebarSearchResults(
    state: RootState,
    onResumeSession: (ProjectId, String, AiProvider) -> Unit,
    onResumeOrphanSession: (cwd: String, sessionId: String, provider: AiProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = remember(
        state.searchResults,
        state.savedSessionsByProject,
        state.orphanSessionsByCwd,
        state.projects,
    ) { resolveHits(state) }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.searchInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (resolved.isEmpty() && !state.searchInProgress) {
            EmptyResults()
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(resolved, key = { "${it.hit.provider.name}:${it.hit.sessionId}" }) { row ->
                SearchResultRow(
                    row = row,
                    onClick = {
                        if (row.projectId != null) {
                            onResumeSession(row.projectId, row.hit.sessionId, row.hit.provider)
                        } else {
                            onResumeOrphanSession(row.session.cwd, row.hit.sessionId, row.hit.provider)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyResults() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No matches",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(
    row: ResolvedHit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        SearchProviderTag(row.hit.provider)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = row.locationLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = highlightSnippet(row.hit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun SearchProviderTag(provider: AiProvider) {
    val color = when (provider) {
        AiProvider.CLAUDE -> MaterialTheme.colorScheme.primary
        AiProvider.OPENCODE -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = provider.terminalLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = color,
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private fun highlightSnippet(hit: SessionSearchHit): AnnotatedString {
    val text = hit.snippet
    if (hit.matchOffset < 0 || hit.matchOffset >= text.length || hit.matchLength <= 0) {
        return AnnotatedString(text)
    }
    val end = (hit.matchOffset + hit.matchLength).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text.substring(0, hit.matchOffset))
        pushStyle(
            SpanStyle(
                background = Color(0xFFFFE082).copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        append(text.substring(hit.matchOffset, end))
        pop()
        append(text.substring(end))
    }
}

private data class ResolvedHit(
    val hit: SessionSearchHit,
    val session: AiSession,
    val projectId: ProjectId?,
    val locationLabel: String,
)

private fun resolveHits(state: RootState): List<ResolvedHit> {
    val sessionsById = HashMap<String, Pair<AiSession, ProjectId?>>()
    state.savedSessionsByProject.forEach { (projectId, list) ->
        list.forEach { sessionsById[it.id] = it to projectId }
    }
    state.orphanSessionsByCwd.forEach { (_, list) ->
        list.forEach { sessionsById.putIfAbsent(it.id, it to null) }
    }
    val projectNameById = state.projects.associate { it.id to it.name }
    return state.searchResults.mapNotNull { hit ->
        val (session, projectId) = sessionsById[hit.sessionId] ?: return@mapNotNull null
        val label = projectId?.let { projectNameById[it] } ?: session.cwd
        ResolvedHit(hit, session, projectId, label)
    }
}
