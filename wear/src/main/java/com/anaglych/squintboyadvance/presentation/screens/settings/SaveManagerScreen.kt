package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.anaglych.squintboyadvance.presentation.theme.OnSurfaceDim

@Composable
fun SaveManagerScreen() {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text("Save Manager", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                }
            }
            items(5) { index ->
                Chip(
                    onClick = { /* TODO: restore save slot */ },
                    label = { Text("Slot ${index + 1}") },
                    secondaryLabel = {
                        Text(
                            if (index < 2) "Save data available" else "Empty",
                            color = OnSurfaceDim
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            item {
                Chip(
                    onClick = { /* TODO: import save */ },
                    label = { Text("Import Save") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            item {
                Chip(
                    onClick = { /* TODO: export save */ },
                    label = { Text("Export Save") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
}
