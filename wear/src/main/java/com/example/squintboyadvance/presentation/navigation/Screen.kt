package com.example.squintboyadvance.presentation.navigation

sealed class Screen(val route: String) {
    data object RomLibrary : Screen("rom_library")
    data object Settings : Screen("settings")
    data object AudioSettings : Screen("settings/audio")
    data object VideoSettings : Screen("settings/video")
    data object ControllerSettings : Screen("settings/controller")
    data object PaletteSettings : Screen("settings/palette")
    data object SaveManager : Screen("settings/saves")
    data object ScaleEditor : Screen("settings/video/scale_editor/{isGba}") {
        fun createRoute(isGba: Boolean) = "settings/video/scale_editor/$isGba"
    }
}
