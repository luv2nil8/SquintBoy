package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.theme.GbGreen

@Composable
fun SessionExpiredOverlay(
    onUpgrade: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Session Expired",
                fontSize = 16.sp,
                color = GbGreen,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Free sessions are limited\nto 15 minutes.\nUpgrade for unlimited play!",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(44.dp),
                ) {
                    Text("Exit", fontSize = 10.sp)
                }
                Button(
                    onClick = onUpgrade,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GbGreen,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Text("Upgrade on Phone", fontSize = 11.sp)
                }
            }
        }
    }
}
