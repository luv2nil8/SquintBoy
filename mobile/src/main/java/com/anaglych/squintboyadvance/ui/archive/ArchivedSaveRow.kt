package com.anaglych.squintboyadvance.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowTimeFormat =
    DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.getDefault())

@Composable
fun ArchivedSaveRow(
    save: ArchivedSaveEntity,
    watchConnected: Boolean,
    onTogglePin: () -> Unit,
    onRestore: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onTogglePin) {
                if (save.pinned) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Unpin",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = "Pin (exempt from retention)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    rowTimeFormat.format(
                        Instant.ofEpochMilli(save.timestampMs).atZone(ZoneId.systemDefault())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val subtitle = buildString {
                    append("${save.sizeBytes / 1024} KB")
                    save.note?.let { append("  ·  $it") }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Icon(
                Icons.Default.Smartphone,
                contentDescription = "On phone",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            when (save.driveState) {
                DriveState.UPLOADED -> CloudIcon(
                    Icons.Default.CloudDone, "Synced to Drive", MaterialTheme.colorScheme.primary
                )
                DriveState.PENDING -> CloudIcon(
                    Icons.Default.CloudUpload, "Drive upload pending",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DriveState.ERROR -> CloudIcon(
                    Icons.Default.CloudOff, "Drive sync error", MaterialTheme.colorScheme.error
                )
                else -> {}
            }

            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Restore to Watch") },
                    enabled = watchConnected,
                    onClick = {
                        menuOpen = false
                        onRestore()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (save.note == null) "Add note" else "Edit note") },
                    onClick = {
                        menuOpen = false
                        onEditNote()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun CloudIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Spacer(Modifier.width(6.dp))
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
}
