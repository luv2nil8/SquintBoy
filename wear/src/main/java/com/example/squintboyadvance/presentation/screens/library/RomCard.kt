package com.example.squintboyadvance.presentation.screens.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.squintboyadvance.presentation.theme.GbBadge
import com.example.squintboyadvance.presentation.theme.GbaBadge
import com.example.squintboyadvance.presentation.theme.GbcBadge
import com.example.squintboyadvance.presentation.theme.OnSurface
import com.example.squintboyadvance.presentation.theme.OnSurfaceDim
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SystemType
import java.io.File

@Composable
fun RomCard(
    rom: RomMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnail = remember(rom.thumbnailPath, rom.lastPlayed) {
        rom.thumbnailPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                } else null
            } catch (_: Exception) { null }
        }
    }

    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(cardShape)
            .background(MaterialTheme.colors.surface)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.matchParentSize()
        ) {
            // Screenshot flush against card edge — no padding
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(
                        if (rom.systemType == SystemType.GBA) 1.5f else 10f / 9f
                    )
                    .background(Color(0xFF1A1A1A))
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = rom.title,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    SystemBadge(systemType = rom.systemType)
                }
            }

            // Text with padding
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = rom.title,
                    style = MaterialTheme.typography.body1,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatPlayInfo(rom),
                    style = MaterialTheme.typography.caption3,
                    color = OnSurfaceDim,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SystemBadge(systemType: SystemType) {
    val badgeColor = when (systemType) {
        SystemType.GB -> GbBadge
        SystemType.GBC -> GbcBadge
        SystemType.GBA -> GbaBadge
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor)
    ) {
        Text(
            text = systemType.name,
            fontSize = 9.sp,
            color = Color.White
        )
    }
}

private fun formatPlayInfo(rom: RomMetadata): String {
    val lastPlayedTime = rom.lastPlayed ?: return "Never played"
    val hoursPlayed = rom.totalPlayTimeMs / 3_600_000
    val minutesPlayed = (rom.totalPlayTimeMs % 3_600_000) / 60_000
    val playTime = if (hoursPlayed > 0) "${hoursPlayed}h ${minutesPlayed}m" else "${minutesPlayed}m"
    val elapsed = System.currentTimeMillis() - lastPlayedTime
    val lastPlayed = when {
        elapsed < 3_600_000 -> "Just now"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000}h ago"
        else -> "${elapsed / 86_400_000}d ago"
    }
    return "$playTime · $lastPlayed"
}
