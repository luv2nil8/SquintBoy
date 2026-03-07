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
            GbColorPalette("EN4",                 0xFF20283D.toInt(), 0xFF426E5D.toInt(), 0xFFE5B083.toInt(), 0xFFFBF7F3.toInt()),
            GbColorPalette("Eventually Morning",  0xFF2B2E25.toInt(), 0xFF258B5B.toInt(), 0xFFE5A251.toInt(), 0xFFDBDA7B.toInt()),
            // Orange & red
            GbColorPalette("Tangerine Dream",     0xFF1A0A00.toInt(), 0xFFA03010.toInt(), 0xFFF06020.toInt(), 0xFFFFD090.toInt()),
            GbColorPalette("Ember Cavern",        0xFF000000.toInt(), 0xFF7E2553.toInt(), 0xFFFF004D.toInt(), 0xFFFFA300.toInt()),
            GbColorPalette("Crimson Biome",       0xFF180006.toInt(), 0xFF780020.toInt(), 0xFFD80040.toInt(), 0xFFFF8868.toInt()),
            // Pinks
            GbColorPalette("Cherry Blossom",      0xFF1A0818.toInt(), 0xFF5A2040.toInt(), 0xFFC06080.toInt(), 0xFFF5C0D0.toInt()),
            GbColorPalette("Rustic GB",           0xFF2C2137.toInt(), 0xFF764462.toInt(), 0xFFEDB4A1.toInt(), 0xFFA96868.toInt()),
            GbColorPalette("Bubblegum",           0xFFFF85A1.toInt(), 0xFFFFA5C8.toInt(), 0xFFFFE0EC.toInt(), 0xFFC7F2FF.toInt()),
            GbColorPalette("Blushing Summit",     0xFF1D2B53.toInt(), 0xFF29ADFF.toInt(), 0xFFFF77A8.toInt(), 0xFFFFF1E8.toInt()),
            // Purples
            GbColorPalette("Dream Candy",         0xFF442D6E.toInt(), 0xFFD075B7.toInt(), 0xFFF0D063.toInt(), 0xFFFFFFFF.toInt()),
            GbColorPalette("Sunset Strip",        0xFF1A0533.toInt(), 0xFF7B1FA2.toInt(), 0xFFFF6D00.toInt(), 0xFFFFEE58.toInt()),
            GbColorPalette("Lava GB",             0xFF051F39.toInt(), 0xFF4A2480.toInt(), 0xFFC53A9D.toInt(), 0xFFFF8E80.toInt()),
            GbColorPalette("Shovel Knight",       0xFF150E22.toInt(), 0xFF2C2B52.toInt(), 0xFF8B78B0.toInt(), 0xFFF0DFFF.toInt()),
            // Multi
            GbColorPalette("Bicycle",             0xFF161616.toInt(), 0xFFAB4646.toInt(), 0xFF8F9BF6.toInt(), 0xFFF0F0F0.toInt()),
            // Blues & teals
            GbColorPalette("Arctic Dusk",         0xFF0D1825.toInt(), 0xFF1E3A5F.toInt(), 0xFF6A9FD8.toInt(), 0xFFE8F4FF.toInt()),
            GbColorPalette("Mist GB",             0xFF2D1B00.toInt(), 0xFF1E606E.toInt(), 0xFF5AB9A8.toInt(), 0xFFC4F0C2.toInt()),
            GbColorPalette("Pulse Neon",          0xFF07111E.toInt(), 0xFF0A3A5C.toInt(), 0xFF00C8E8.toInt(), 0xFFE8FC50.toInt()),
            // Greens
            GbColorPalette("Forest Glow",         0xFF071207.toInt(), 0xFF1E4C1E.toInt(), 0xFF4E9E4E.toInt(), 0xFFB8F0B8.toInt()),
            GbColorPalette("SR388",               0xFF070606.toInt(), 0xFF1B2A3B.toInt(), 0xFF5A7A5A.toInt(), 0xFFC0E080.toInt()),
            GbColorPalette("Mega Taiga",          0xFF0C1C0E.toInt(), 0xFF3D2E18.toInt(), 0xFF3A7035.toInt(), 0xFFC8DDB4.toInt()),
            GbColorPalette("Kirokaze GB",         0xFF332C50.toInt(), 0xFF46878F.toInt(), 0xFF94E344.toInt(), 0xFFE2F3E4.toInt()),
        )

        val DEFAULT_INDEX = 0  // DMG Green — the classic
    }
}
