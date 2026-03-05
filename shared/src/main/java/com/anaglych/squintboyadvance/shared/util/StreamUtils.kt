package com.anaglych.squintboyadvance.shared.util

import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Reads a single '\n'-terminated line from [input] without buffering beyond the delimiter.
 * Returns null if the stream is at EOF and no bytes were read.
 */
fun readLine(input: InputStream): String? {
    val bytes = mutableListOf<Byte>()
    while (true) {
        val b = input.read()
        if (b == -1) {
            return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.UTF_8)
        }
        if (b == '\n'.code) break
        bytes.add(b.toByte())
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}
