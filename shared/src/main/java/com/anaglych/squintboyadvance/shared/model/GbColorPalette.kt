package com.anaglych.squintboyadvance.shared.model

/**
 * A 4-color Game Boy palette.
 *
 * Colors are stored in lospec order: c0 = darkest (DMG shade 3 / "black"),
 * c3 = lightest (DMG shade 0 / "white"). When passed to the emulator core,
 * the order is reversed so that gb.pal[0] = lightest as mGBA expects.
 *
 * Each color is a packed 0xFFRRGGBB Android ARGB int.
 */
data class GbColorPalette(
    val name: String,
    val c0: Int,  // darkest
    val c1: Int,
    val c2: Int,
    val c3: Int,  // lightest
) {
    /** Colors in lospec order [darkest→lightest], for use in the emulator. */
    fun lospecColors() = intArrayOf(c0, c1, c2, c3)

    /** Colors in mGBA order [lightest→darkest], ready for the JNI call. */
    fun mgbaOrder() = intArrayOf(c3, c2, c1, c0)

    companion object {
        val ALL: List<GbColorPalette> = listOf(
            // Classics
            GbColorPalette("DMG Green",           0xFF0F380F.toInt(), 0xFF306230.toInt(), 0xFF8BAC0F.toInt(), 0xFF9BBC0F.toInt()),
            GbColorPalette("Hallownest",          0xFF0F0F1B.toInt(), 0xFF565A75.toInt(), 0xFFC6B7BE.toInt(), 0xFFFAFBF6.toInt()),
            GbColorPalette("Ice Cream GB",        0xFF7C3F58.toInt(), 0xFFEB6B6F.toInt(), 0xFFF9A875.toInt(), 0xFFFFF6D3.toInt()),
            // Warm & earthy
            GbColorPalette("Pelican Town",        0xFF2A1808.toInt(), 0xFF7A5C28.toInt(), 0xFFC8A850.toInt(), 0xFFF0E8B0.toInt()),
            GbColorPalette("The Ruins",           0xFF100808.toInt(), 0xFF5C1818.toInt(), 0xFFB04828.toInt(), 0xFFF8D8A0.toInt()),
            GbColorPalette("Plague Potion",       0xFF000000.toInt(), 0xFF422136.toInt(), 0xFFAB5236.toInt(), 0xFFFFEC27.toInt()),
            // Reds & fire
            GbColorPalette("Ember Cavern",        0xFF000000.toInt(), 0xFF7E2553.toInt(), 0xFFFF004D.toInt(), 0xFFFFA300.toInt()),
            GbColorPalette("Crimson Biome",       0xFF180006.toInt(), 0xFF780020.toInt(), 0xFFD80040.toInt(), 0xFFFF8868.toInt()),
            GbColorPalette("Neon Midnight",       0xFF000000.toInt(), 0xFF1D2B53.toInt(), 0xFFFF004D.toInt(), 0xFFFFEC27.toInt()),
            // Pinks & magentas
            GbColorPalette("Phantom Dusk",        0xFF0D0821.toInt(), 0xFF3D1C56.toInt(), 0xFFC80048.toInt(), 0xFFFF6EB4.toInt()),
            GbColorPalette("Blushing Summit",     0xFF1D2B53.toInt(), 0xFF29ADFF.toInt(), 0xFFFF77A8.toInt(), 0xFFFFF1E8.toInt()),
            GbColorPalette("Clock Tower",         0xFF0A0810.toInt(), 0xFF382868.toInt(), 0xFFE04018.toInt(), 0xFFFFD840.toInt()),
            // Purples
            GbColorPalette("Corruption",          0xFF080018.toInt(), 0xFF3A0878.toInt(), 0xFF8840E0.toInt(), 0xFFE0B0FF.toInt()),
            GbColorPalette("Black Bridge",        0xFF060408.toInt(), 0xFF301830.toInt(), 0xFF9820A8.toInt(), 0xFFF030F0.toInt()),
            GbColorPalette("Fungal Wastes",       0xFF0A0C18.toInt(), 0xFF2A1E4A.toInt(), 0xFF8030C0.toInt(), 0xFFD0F060.toInt()),
            // Blues & cyans
            GbColorPalette("Snowdin",             0xFF080C18.toInt(), 0xFF1830A0.toInt(), 0xFF80C8F8.toInt(), 0xFFF0F8FF.toInt()),
            GbColorPalette("Azure Valor",         0xFF000000.toInt(), 0xFF1D2B53.toInt(), 0xFF29ADFF.toInt(), 0xFFFFF1E8.toInt()),
            GbColorPalette("Northern Waste",      0xFF06040E.toInt(), 0xFF280868.toInt(), 0xFF6020E0.toInt(), 0xFF40E8FF.toInt()),
            // Teals & neons
            GbColorPalette("Pulse Neon",          0xFF07111E.toInt(), 0xFF0A3A5C.toInt(), 0xFF00C8E8.toInt(), 0xFFE8FC50.toInt()),
            GbColorPalette("Pixel Jungle",         0xFF1D2B53.toInt(), 0xFF7E2553.toInt(), 0xFFD4C800.toInt(), 0xFF00B84A.toInt()),
            GbColorPalette("Mega Taiga",          0xFF100F24.toInt(), 0xFFC74634.toInt(), 0xFF5D8D60.toInt(), 0xFFEDEADA.toInt()),
            // Greens
            GbColorPalette("Spring Farm",         0xFF1A3010.toInt(), 0xFF3A7830.toInt(), 0xFF88CC40.toInt(), 0xFFF0F8D0.toInt()),
            GbColorPalette("Promenade",           0xFF060C18.toInt(), 0xFF1A4830.toInt(), 0xFF28C858.toInt(), 0xFFF8E840.toInt()),
            GbColorPalette("Southern Reach",      0xFF03080E.toInt(), 0xFF0C3020.toInt(), 0xFF20A858.toInt(), 0xFFB8FF70.toInt()),
            GbColorPalette("Toxic Sewers",        0xFF080E04.toInt(), 0xFF184808.toInt(), 0xFF60D010.toInt(), 0xFFD8FF40.toInt()),
        )

        val DEFAULT_INDEX = 0  // DMG Green — the classic
    }
}
