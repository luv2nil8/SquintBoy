package com.anaglych.squintboyadvance.presentation.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.anaglych.squintboyadvance.presentation.screens.emulator.EmulatorActivity
import com.anaglych.squintboyadvance.presentation.screens.library.RomLibraryScreen

@Composable
fun WearNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Screen.RomLibrary.route,
        modifier = modifier
    ) {
        composable(Screen.RomLibrary.route) {
            RomLibraryScreen(
                onRomSelected = { rom ->
                    context.startActivity(
                        Intent(context, EmulatorActivity::class.java).apply {
                            putExtra("rom_id", rom.id)
                            putExtra("rom_title", rom.title)
                        }
                    )
                },
            )
        }
    }
}
