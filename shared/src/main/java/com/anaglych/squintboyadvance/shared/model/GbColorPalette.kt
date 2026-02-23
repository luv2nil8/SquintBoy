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
            // Top 24 most-downloaded 4-color GB palettes from lospec.com
            GbColorPalette("Ice Cream GB",        0xFF7C3F58.toInt(), 0xFFEB6B6F.toInt(), 0xFFF9A875.toInt(), 0xFFFFF6D3.toInt()),
            GbColorPalette("Kirokaze Gameboy",    0xFF332C50.toInt(), 0xFF46878F.toInt(), 0xFF94E344.toInt(), 0xFFE2F3E4.toInt()),
            GbColorPalette("2bit Demichrome",     0xFF211E20.toInt(), 0xFF555568.toInt(), 0xFFA0A08B.toInt(), 0xFFE9EFEC.toInt()),
            GbColorPalette("Hollow",              0xFF0F0F1B.toInt(), 0xFF565A75.toInt(), 0xFFC6B7BE.toInt(), 0xFFFAFBF6.toInt()),
            GbColorPalette("Mist GB",             0xFF2D1B00.toInt(), 0xFF1E606E.toInt(), 0xFF5AB9A8.toInt(), 0xFFC4F0C2.toInt()),
            GbColorPalette("Rustic GB",           0xFF2C2137.toInt(), 0xFF764462.toInt(), 0xFFEDB4A1.toInt(), 0xFFA96868.toInt()),
            GbColorPalette("AYY4",                0xFF00303B.toInt(), 0xFFFF7777.toInt(), 0xFFFFCE96.toInt(), 0xFFF1F2DA.toInt()),
            GbColorPalette("Wish GB",             0xFF622E4C.toInt(), 0xFF7550E8.toInt(), 0xFF608FCF.toInt(), 0xFF8BE5FF.toInt()),
            GbColorPalette("BLK AQU4",            0xFF002B59.toInt(), 0xFF005F8C.toInt(), 0xFF00B9BE.toInt(), 0xFF9FF4E5.toInt()),
            GbColorPalette("Arq4",                0xFF000000.toInt(), 0xFF3A3277.toInt(), 0xFF6772A9.toInt(), 0xFFFFFFFF.toInt()),
            GbColorPalette("EN4",                 0xFF20283D.toInt(), 0xFF426E5D.toInt(), 0xFFE5B083.toInt(), 0xFFFBF7F3.toInt()),
            GbColorPalette("Blood Crow",          0xFF190000.toInt(), 0xFF560909.toInt(), 0xFFAD2020.toInt(), 0xFFF2E6E6.toInt()),
            GbColorPalette("Mokky",               0xFF332920.toInt(), 0xFF664930.toInt(), 0xFF99683D.toInt(), 0xFFCCA66E.toInt()),
            GbColorPalette("Dreamful Space",      0xFF21193C.toInt(), 0xFF932F7B.toInt(), 0xFFE67B8B.toInt(), 0xFFF5D2B8.toInt()),
            GbColorPalette("Italy-4",             0xFF100F24.toInt(), 0xFFC74634.toInt(), 0xFF5D8D60.toInt(), 0xFFEDEADA.toInt()),
            GbColorPalette("Qameboy",             0xFF353D46.toInt(), 0xFF42665A.toInt(), 0xFF739A56.toInt(), 0xFFB2C27D.toInt()),
            GbColorPalette("Technobike",          0xFF1D2938.toInt(), 0xFF2A616E.toInt(), 0xFF13B37E.toInt(), 0xFF07EF5C.toInt()),
            GbColorPalette("Crimson Blood 4",     0xFF290707.toInt(), 0xFF766161.toInt(), 0xFFBB9D9D.toInt(), 0xFFBA0000.toInt()),
            GbColorPalette("RABBIT5PM",           0xFF4C3457.toInt(), 0xFF629098.toInt(), 0xFFE4A39F.toInt(), 0xFFFFE7CD.toInt()),
            GbColorPalette("Soulscape",           0xFF051E45.toInt(), 0xFF0B586E.toInt(), 0xFF1CB099.toInt(), 0xFF42F4AF.toInt()),
            GbColorPalette("Calccurate GB",       0xFF000000.toInt(), 0xFF005500.toInt(), 0xFF55AA55.toInt(), 0xFFAAFFAA.toInt()),
            GbColorPalette("Snooker GB",          0xFF40242F.toInt(), 0xFF075040.toInt(), 0xFFE1A847.toInt(), 0xFFC9DFB1.toInt()),
            GbColorPalette("Slate",               0xFF292736.toInt(), 0xFF49556C.toInt(), 0xFF608189.toInt(), 0xFFBEB09F.toInt()),
            GbColorPalette("Nintendo GB",         0xFF081820.toInt(), 0xFF346856.toInt(), 0xFF88C070.toInt(), 0xFFE0F8D0.toInt()),
        )

        val DEFAULT_INDEX = 23  // Nintendo GB — the classic green
    }
}
