package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.ReviewState
import com.anaglych.squintboyadvance.presentation.ReviewTracker

@Composable
fun ReviewPromptDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Enjoying Squint Boy?",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
            Text(
                "Leave a review to help others find the app.",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    ReviewTracker.setState(context, ReviewState.DONE)
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.anaglych.squintboyadvance"),
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(0.78f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF9BBC0F)),
            ) {
                Text("Rate", color = Color.Black)
            }
            Button(
                onClick = {
                    ReviewTracker.setState(context, ReviewState.LATER)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(0.78f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF16213E)),
            ) {
                Text("Later")
            }
            Button(
                onClick = {
                    ReviewTracker.setState(context, ReviewState.NEVER)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(0.78f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
            ) {
                Text("Never", color = Color.White.copy(alpha = 0.45f))
            }
        }
    }
}
