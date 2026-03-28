package com.anaglych.squintboyadvance.presentation.screens.licenses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.launch

private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val license: String,
    val source: String,
    val notes: String? = null,
)

private val LICENSES = listOf(
    LicenseEntry(
        name = "mGBA",
        copyright = "Jeffrey Pfau",
        license = "MPL-2.0",
        source = "https://github.com/mgba-emu/mgba",
    ),
    LicenseEntry(
        name = "AndroidX / Compose",
        copyright = "Google LLC / AOSP",
        license = "Apache-2.0",
        source = "https://github.com/androidx/androidx",
    ),
    LicenseEntry(
        name = "Kotlin / kotlinx",
        copyright = "JetBrains s.r.o.",
        license = "Apache-2.0",
        source = "https://github.com/Kotlin/kotlin",
    ),
)

@Composable
fun WearLicensesScreen() {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "Licenses",
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            items(LICENSES.size) { i ->
                val entry = LICENSES[i]
                Text(
                    buildString {
                        append(entry.name)
                        append("\n")
                        append(entry.copyright)
                        append(" \u2022 ")
                        append(entry.license)
                        if (entry.notes != null) {
                            append("\n")
                            append(entry.notes)
                        }
                        append("\n")
                        append(entry.source)
                    },
                    style = MaterialTheme.typography.caption3,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(entry.source))
                            )
                        },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Full license texts bundled in APK.",
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
