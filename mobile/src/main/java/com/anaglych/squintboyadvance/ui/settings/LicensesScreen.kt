package com.anaglych.squintboyadvance.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val licenseType: String,
    val sourceUrl: String,
    val notes: String? = null,
)

private val LICENSES = listOf(
    LicenseEntry(
        name = "mGBA",
        copyright = "© 2013–2024 Jeffrey Pfau",
        licenseType = "Mozilla Public License 2.0",
        sourceUrl = "https://github.com/mgba-emu/mgba",
        notes = "Used unmodified as the emulation core. Source available at the link above.",
    ),
    LicenseEntry(
        name = "zlib (bundled in mGBA)",
        copyright = "© 1995–2024 Jean-loup Gailly & Mark Adler",
        licenseType = "zlib License",
        sourceUrl = "https://zlib.net",
    ),
    LicenseEntry(
        name = "inih (bundled in mGBA)",
        copyright = "© 2009 Ben Hoyt",
        licenseType = "BSD 3-Clause License",
        sourceUrl = "https://github.com/benhoyt/inih",
    ),
    LicenseEntry(
        name = "Android Jetpack / AndroidX",
        copyright = "© Google LLC and the Android Open Source Project",
        licenseType = "Apache License 2.0",
        sourceUrl = "https://github.com/androidx/androidx",
        notes = "Includes: Core KTX, AppCompat, Compose, Material3, Wear Compose, " +
                "Navigation, Lifecycle, RecyclerView, Splashscreen.",
    ),
    LicenseEntry(
        name = "Kotlin & kotlinx",
        copyright = "© JetBrains s.r.o.",
        licenseType = "Apache License 2.0",
        sourceUrl = "https://github.com/Kotlin/kotlin",
        notes = "Includes: Kotlin Standard Library, kotlinx.serialization, kotlinx.coroutines.",
    ),
    LicenseEntry(
        name = "Google Play Services — Wearable",
        copyright = "© Google LLC",
        licenseType = "Google APIs Terms of Service",
        sourceUrl = "https://developers.google.com/terms",
    ),
)

@Composable
fun LicensesScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Open Source Licenses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Squint Boy Advance is built on the following open source components.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
        }

        items(LICENSES) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.copyright,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        entry.licenseType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (entry.notes != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        entry.sourceUrl,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(entry.sourceUrl))
                            )
                        },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text(
                "Full license texts are included in THIRD_PARTY_LICENSES.md in the project repository.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}
