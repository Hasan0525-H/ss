package com.vibe.app.presentation.ui.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibe.app.feature.project.snapshot.Snapshot
import com.vibe.app.feature.project.snapshot.SnapshotType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom-sheet content showing the full snapshot history for the current
 * project. Most recent first. Each row has a restore button that restores
 * the workspace to that snapshot (via a backup snapshot first, so the
 * restore itself is undoable).
 */
@Composable
fun SnapshotHistoryPanel(
    snapshots: List<Snapshot>,
    onRestoreClick: (Snapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(
            text = "Snapshot History", // بدلاً من snapshot_history_title
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        if (snapshots.isEmpty()) {
            Text(
                text = "No snapshots available.", // بدلاً من snapshot_history_empty
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
            return
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(snapshots) { snap ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = when (snap.type) {
                                SnapshotType.TURN -> "Turn ${snap.turnIndex ?: "?"}: ${snap.label}"
                                SnapshotType.MANUAL -> "Manual: ${snap.label}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "${formatRelativeTime(context, snap.createdAtEpochMs)} • ${snap.affectedFiles.size} files affected",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { onRestoreClick(snap) }) {
                            Text("Restore") // بدلاً من snapshot_history_restore
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun formatRelativeTime(context: android.content.Context, epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - epochMs
    return when {
        diffMs < 60_000 -> "Just now"
        diffMs < 3_600_000 -> "${(diffMs / 60_000).toInt()}m ago"
        diffMs < 86_400_000 -> "${(diffMs / 3_600_000).toInt()}h ago"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}
