package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.squintboyadvance.presentation.theme.GbGreen
import com.example.squintboyadvance.shared.emulator.EmulatorState

@Composable
fun EmulatorScreen(
    romId: String,
    romTitle: String,
    onExit: () -> Unit,
    viewModel: EmulatorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // Load ROM on first composition
    androidx.compose.runtime.LaunchedEffect(romId) {
        viewModel.loadRom(romId, romTitle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = romTitle,
                style = MaterialTheme.typography.title3,
                color = GbGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (state) {
                    EmulatorState.IDLE -> "Ready"
                    EmulatorState.LOADING -> "Loading..."
                    EmulatorState.RUNNING -> "Emulator Running"
                    EmulatorState.PAUSED -> "Paused"
                    EmulatorState.ERROR -> "Error"
                },
                style = MaterialTheme.typography.body2,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onExit) {
                Text("Exit")
            }
        }
    }
}
