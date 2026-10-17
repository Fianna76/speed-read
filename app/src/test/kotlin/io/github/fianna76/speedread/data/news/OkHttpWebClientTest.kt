package io.github.fianna76.speedread.data.news

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpWebClientTest {

    private val server = MockWebServer()
    private val client = OkHttpWebClient(OkHttpWebClient.createClient(), userAgent = "SpeedRead-Test/1.0")

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `returns the body and identifies the app`() = runTest {
        server.enqueue(MockResponse.Builder().body("<rss>café</rss>").setHeader("Content-Type", "application/rss+xml; charset=utf-8").build())

        val body = client.fetchText(server.url("/feed.xml").toString())

        assertEquals("<rss>café</rss>", body)
        assertEquals("SpeedRead-Test/1.0", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun `HTTP errors throw with the status code`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("missing").build())

        val error = runCatching { client.fetchText(server.url("/gone").toString()) }.exceptionOrNull()

        assertEquals(404, (error as HttpStatusException).code)
    }

    @Test
    fun `oversized responses are refused`() = runTest {
        val big = Buffer().apply { repeat((OkHttpWebClient.MAX_BYTES / 1024 + 1).toInt()) { write(ByteArray(1024)) } }
        server.enqueue(MockResponse.Builder().body(big).build())

        val error = runCatching { client.fetchText(server.url("/huge").toString()) }.exceptionOrNull()

        assertTrue(error is IOException)
    }
}
