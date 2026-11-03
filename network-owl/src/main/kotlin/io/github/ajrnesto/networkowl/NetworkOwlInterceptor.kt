package io.github.ajrnesto.networkowl

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.buffer
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_NO_CONTENT

internal class NetworkOwlInterceptor(
    private val config: () -> NetworkOwlConfig,
    private val store: TransactionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = config()
        val redacted = config.redactedHeaders.mapTo(HashSet()) { it.lowercase() }
        val request = chain.request()

        val id = store.add { id ->
            HttpTransaction(
                id = id,
                method = request.method,
                url = request.url.toString(),
                host = request.url.host,
                path = request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty(),
                requestHeaders = request.headers.toHeaderList(redacted),
                requestBody = request.body?.capture(request.headers, config.maxContentLength),
                requestedAt = System.currentTimeMillis(),
            )
        }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            store.update(id) { it.copy(error = e.toString()) }
            throw e
        }

        val head = HttpResponse(
            code = response.code,
            message = response.message,
            protocol = response.protocol.toString(),
            headers = response.headers.toHeaderList(redacted),
            tookMs = response.receivedResponseAtMillis - response.sentRequestAtMillis,
            body = null,
            bodyComplete = false,
        )

        if (!response.hasBody()) {
            store.update(id) { it.copy(response = head.copy(bodyComplete = true)) }
            return response
        }

        store.update(id) { it.copy(response = head) }
        val body = response.body
        val contentType = body.contentType()
        val capturing = CapturingSource(body.source(), config.maxContentLength) { captured, byteCount ->
            val recorded = decodeBody(captured, byteCount, contentType, response.headers)
            store.update(id) { transaction ->
                transaction.copy(response = transaction.response?.copy(body = recorded, bodyComplete = true))
            }
        }
        return response.newBuilder()
            .body(capturing.buffer().asResponseBody(contentType, body.contentLength()))
            .build()
    }

    private fun RequestBody.capture(headers: Headers, limit: Long): Body {
        val contentType = contentType()
        if (isOneShot() || isDuplex()) return Body.NotCaptured(contentType?.toString(), contentLength())
        val sink = LimitingSink(limit)
        return try {
            sink.buffer().also(::writeTo).flush()
            decodeBody(sink.captured, sink.byteCount, contentType, headers)
        } catch (e: IOException) {
            Body.NotCaptured(contentType?.toString(), contentLength())
        }
    }

    private fun Headers.toHeaderList(redacted: Set<String>): List<Header> =
        map { (name, value) -> Header(name, if (name.lowercase() in redacted) REDACTED_VALUE else value) }

    private fun Response.hasBody(): Boolean {
        if (request.method == "HEAD") return false
        val noBodyCode = code in 100..199 || code == HTTP_NO_CONTENT || code == HTTP_NOT_MODIFIED
        return !noBodyCode || body.contentLength() > 0 || header("Transfer-Encoding").equals("chunked", ignoreCase = true)
    }

    private fun decodeBody(captured: Buffer, byteCount: Long, contentType: MediaType?, headers: Headers): Body =
        try {
            BodyDecoder.decode(captured, byteCount, contentType, headers["Content-Encoding"])
        } catch (e: IOException) {
            Body.NotCaptured(contentType?.toString(), byteCount)
        }
}
