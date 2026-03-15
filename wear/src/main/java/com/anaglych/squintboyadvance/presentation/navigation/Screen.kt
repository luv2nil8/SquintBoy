package com.anaglych.squintboyadvance.presentation.navigation

sealed class Screen(val route: String) {
    data object RomLibrary : Screen("rom_library")
}
