package com.anaglych.squintboyadvance.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.anaglych.squintboyadvance.presentation.EntitlementRepository
import com.anaglych.squintboyadvance.presentation.RomLibrarySignal
import com.anaglych.squintboyadvance.presentation.TransferEvent
import com.anaglych.squintboyadvance.shared.model.DemoLimits
import com.anaglych.squintboyadvance.shared.model.RomMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RomLibraryScreen(
    onRomSelected: (RomMetadata) -> Unit,
    onLicenses: () -> Unit = {},
    viewModel: RomLibraryViewModel = viewModel()
) {
    val roms by viewModel.roms.collectAsState()
    val transferringNames by viewModel.transferringNames.collectAsState()
    val pickerState by viewModel.pickerState.collectAsStateWithLifecycle()
    val phoneAppInstalled by viewModel.phoneAppInstalled.collectAsStateWithLifecycle()
    val entitlementRepo = EntitlementRepository.getInstance(viewModel.getApplication())
    val isPro by entitlementRepo.isPro.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 2)
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var transferEvent by remember { mutableStateOf<TransferEvent?>(null) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.scanRoms()
        }
    }

    LaunchedEffect(Unit) {
        RomLibrarySignal.transferEvent.collect { event ->
            transferEvent = event
            delay(3000)
            transferEvent = null
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
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
                    .focusable(),
                autoCentering = AutoCenteringParams(itemIndex = 2),
            ) {
                item { Spacer(Modifier.height(48.dp)) }

                // ── Phone app install prompt ──────────────────────────────
                if (!phoneAppInstalled) {
                    item {
                        PhoneInstallCard(
                            onInstall = { viewModel.openPhonePlayStore() },
                        )
                    }
                }

                // ── Transferring ROM cards ─────────────────────────────
                items(transferringNames, key = { "transferring_$it" }) { name ->
                    TransferringRomCard(filename = name)
                }

                if (roms.isEmpty() && transferringNames.isEmpty()) {
                    item { EmptyLibraryState() }
                } else {
                    items(roms, key = { it.id }) { rom ->
                        RomCard(rom = rom, onClick = { onRomSelected(rom) })
                    }
                }

                // ── Add ROM ─────────────────────────────────────────────
                item {
                    val atDemoLimit = !isPro && roms.size >= DemoLimits.MAX_ROMS
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactChip(
                            onClick = {
                                if (atDemoLimit) {
                                    (context as? Activity)?.let { activity ->
                                        EntitlementRepository.getInstance(activity).launchPurchase(activity)
                                    }
                                } else {
                                    viewModel.sendOpenRomPicker()
                                }
                            },
                            label = {
                                Text(
                                    if (atDemoLimit) "Upgrade for more"
                                    else "Add ROM"
                                )
                            },
                            icon = {
                                Icon(
                                    if (atDemoLimit) Icons.Default.Lock else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            colors = if (atDemoLimit) ChipDefaults.secondaryChipColors()
                                else ChipDefaults.primaryChipColors(),
                        )
                    }
                }

                // ── Licenses ───────────────────────────────────────────
                item {
                    Text(
                        "Licenses",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { onLicenses() },
                    )
                }

            }
        }

        // ── Phone picker overlay ────────────────────────────────────────
        if (pickerState != RomPickerState.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { viewModel.dismissPickerOverlay() },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colors.surface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (pickerState) {
                        RomPickerState.SENDING -> {
                            Text(
                                "Opening on phone…",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.primary,
                            )
                        }
                        RomPickerState.WAITING -> {
                            Text(
                                "Pick a ROM file on your phone",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap to dismiss",
                                style = MaterialTheme.typography.caption3,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        RomPickerState.ERROR -> {
                            Text(
                                "Phone not reachable",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.error,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap to dismiss",
                                style = MaterialTheme.typography.caption3,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        RomPickerState.IDLE -> {}
                    }
                }
            }
        }

        // ── Transfer result overlay ─────────────────────────────────────
        transferEvent?.let { event ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { transferEvent = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colors.surface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (event.success) {
                        Text(
                            "ROM received!",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.primary,
                        )
                    } else {
                        Text(
                            "Transfer failed",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.error,
                        )
                        event.errorMessage?.let { msg ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                msg,
                                style = MaterialTheme.typography.caption3,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInstallCard(onInstall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Get the phone app",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
        )
        Text(
            "Transfer ROMs & sync saves",
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(6.dp))
        CompactChip(
            onClick = onInstall,
            label = { Text("Install") },
        )
    }
}
