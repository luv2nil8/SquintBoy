package com.anaglych.squintboyadvance.shared.util

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class HashUtilsTest {

    // Well-known SHA-256 test vectors.
    @Test
    fun `empty input matches known digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashUtils.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `abc matches known digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtils.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `stream and byte array digests agree`() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        assertEquals(
            HashUtils.sha256Hex(data),
            HashUtils.sha256Hex(ByteArrayInputStream(data)),
        )
    }

    @Test
    fun `stream larger than buffer is fully consumed`() {
        val data = ByteArray(8192 * 3 + 17) { it.toByte() }
        assertEquals(
            HashUtils.sha256Hex(data),
            HashUtils.sha256Hex(ByteArrayInputStream(data)),
        )
    }
}
