package com.anaglych.squintboyadvance.data.cloud

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class InMemoryCache : StringCache {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }
}

class GoogleDriveProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GoogleDriveProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = provider { "test-token" }
    }

    private fun provider(tokenProvider: suspend () -> String?): GoogleDriveProvider {
        val base = server.url("/").toString().trimEnd('/')
        return GoogleDriveProvider(
            tokenProvider = tokenProvider,
            idCache = InMemoryCache(),
            apiBase = base,
            uploadBase = base,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `delete 404 counts as success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(provider.delete("gone-id") is CloudResult.Ok)
    }

    @Test
    fun `quota 403 maps to QuotaExceeded`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"code":403}}"""
            )
        )
        // ensureGameDir starts with a files.list for the root folder; the 403 hits there.
        assertEquals(CloudResult.QuotaExceeded, provider.ensureGameDir("Game-a1b2c3d4"))
    }

    @Test
    fun `plain 403 is Permanent`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"errors":[{"reason":"insufficientPermissions"}],"code":403}}"""
            )
        )
        assertTrue(provider.ensureGameDir("Game-a1b2c3d4") is CloudResult.Permanent)
    }

    @Test
    fun `500 and 429 are Retryable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(provider.delete("id") is CloudResult.Retryable)
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(provider.delete("id") is CloudResult.Retryable)
    }

    @Test
    fun `401 retries once with fresh token then fails Permanent AUTH`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val result = provider.delete("id")
        assertTrue(result is CloudResult.Permanent)
        assertEquals("AUTH", (result as CloudResult.Permanent).message)
        assertEquals(2, server.requestCount) // exactly one silent re-auth retry
    }

    @Test
    fun `401 then success recovers transparently`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(provider.delete("id") is CloudResult.Ok)
    }

    @Test
    fun `null token maps to NotConnected without any request`() = runTest {
        val disconnected = provider { null }
        assertEquals(CloudResult.NotConnected, disconnected.delete("id"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `upload adopts orphan with same name instead of duplicating`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"files":[{"id":"orphan-123"}]}""")
        )
        val result = provider.upload("dir-1", "save.sav", byteArrayOf(1, 2), "hash", "Game.gba")
        assertEquals("orphan-123", (result as CloudResult.Ok).value)
        assertEquals(1, server.requestCount) // list only, no create
    }

    @Test
    fun `upload creates when no orphan exists`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"new-456"}"""))
        val result = provider.upload("dir-1", "save.sav", byteArrayOf(1, 2), "hash", "Game.gba")
        assertEquals("new-456", (result as CloudResult.Ok).value)
        assertEquals(2, server.requestCount)
        server.takeRequest() // files.list
        val uploadRequest = server.takeRequest()
        assertTrue(uploadRequest.path!!.contains("uploadType=multipart"))
        assertEquals("Bearer test-token", uploadRequest.getHeader("Authorization"))
    }

    @Test
    fun `folder ids are cached across calls`() = runTest {
        // Root lookup + game dir lookup, both found.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[{"id":"root-1"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[{"id":"dir-1"}]}"""))
        assertEquals("dir-1", (provider.ensureGameDir("Game-x") as CloudResult.Ok).value)
        // Second call: served fully from cache.
        assertEquals("dir-1", (provider.ensureGameDir("Game-x") as CloudResult.Ok).value)
        assertEquals(2, server.requestCount)
    }
}
