package com.example.squintboyadvance.presentation.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.squintboyadvance.presentation.screens.emulator.EmulatorActivity
import com.example.squintboyadvance.presentation.screens.library.RomLibraryScreen
import com.example.squintboyadvance.presentation.screens.settings.AudioSettingsScreen
import com.example.squintboyadvance.presentation.screens.settings.ControllerSettingsScreen
import com.example.squintboyadvance.presentation.screens.settings.PaletteSettingsScreen
import com.example.squintboyadvance.presentation.screens.settings.SaveManagerScreen
import com.example.squintboyadvance.presentation.screens.settings.SettingsScreen
import com.example.squintboyadvance.presentation.screens.settings.VideoSettingsScreen

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
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigate = { screen -> navController.navigate(screen.route) }
            )
        }
        composable(Screen.AudioSettings.route) {
            AudioSettingsScreen()
        }
        composable(Screen.VideoSettings.route) {
            VideoSettingsScreen()
        }
        composable(Screen.ControllerSettings.route) {
            ControllerSettingsScreen()
        }
        composable(Screen.PaletteSettings.route) {
            PaletteSettingsScreen()
        }
        composable(Screen.SaveManager.route) {
            SaveManagerScreen()
        }
    }
}
