package com.anaglych.squintboyadvance.data.archive

import com.anaglych.squintboyadvance.shared.util.HashUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Naming for the user-visible archive folder: human-readable but collision-proof.
 * Directory example: "Pokemon Crystal-a1b2c3d4"; file: "20260702_143501_a1b2c3d4.sav".
 */
object GameDirNaming {

    private const val MAX_BASE_LEN = 40
    private val fileStampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun gameDirName(romId: String): String {
        val base = romId.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 ._-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_BASE_LEN)
            .ifEmpty { "game" }
        val suffix = HashUtils.sha256Hex(romId.toByteArray(Charsets.UTF_8)).take(8)
        return "$base-$suffix"
    }

    fun saveFileName(timestampMs: Long, sha256: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = fileStampFormat.format(Instant.ofEpochMilli(timestampMs).atZone(zone))
        return "${stamp}_${sha256.take(8)}.sav"
    }

    /** Local date as a yyyymmdd int, the calendar bucketing key. */
    fun dayKey(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
        return date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
    }
}
