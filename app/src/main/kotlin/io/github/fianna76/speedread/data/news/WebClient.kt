package io.github.fianna76.speedread.data.news

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Downloads feeds and web pages as text. */
fun interface WebClient {
    /** @throws IOException on network failure, an HTTP error status or an oversized response. */
    suspend fun fetchText(url: String): String
}

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** [WebClient] backed by OkHttp. Requests are cancelled when the calling coroutine is. */
class OkHttpWebClient(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WebClient {

    override suspend fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/rss+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        return client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, url)
            withContext(ioDispatcher) {
                val body = response.body
                if (body.contentLength() > MAX_BYTES) throw IOException("Response too large: $url")
                val source = body.source()
                // Read at most one byte past the limit to detect oversized bodies without a length.
                source.request(MAX_BYTES + 1)
                if (source.buffer.size > MAX_BYTES) throw IOException("Response too large: $url")
                body.string()
            }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) = continuation.resume(response) { _, _, _ ->
                    response.close()
                }

                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
            },
        )
    }

    companion object {
        /** Articles and feeds are well under this; anything bigger is not a news page. */
        const val MAX_BYTES = 5L * 1024 * 1024

        fun createClient(cacheDir: File? = null): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .apply { if (cacheDir != null) cache(Cache(cacheDir, HTTP_CACHE_BYTES)) }
            .build()

        private const val HTTP_CACHE_BYTES = 20L * 1024 * 1024
    }
}
