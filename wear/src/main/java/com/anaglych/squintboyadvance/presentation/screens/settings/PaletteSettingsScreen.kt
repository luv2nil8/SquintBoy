package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PaletteSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()

    PaletteGrid(
        selectedIndex = settings.gbPaletteIndex,
        onSelected = { viewModel.setGbPaletteIndex(it) },
    )
}
