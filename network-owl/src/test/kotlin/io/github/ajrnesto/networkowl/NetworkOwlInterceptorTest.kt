package io.github.ajrnesto.networkowl

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class NetworkOwlInterceptorTest {
    private val server = MockWebServer()
    private var config = NetworkOwlConfig()
    private val store = TransactionStore(capacity = { config.capacity })
    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkOwlInterceptor(config = { config }, store = store))
        .build()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `records request and response while the caller still reads the full body`() {
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json")
                .body("""{"id":7}""")
                .build(),
        )

        val request = Request.Builder()
            .url(server.url("/api/bookings?draft=1"))
            .post("""{"leg":3}""".toRequestBody("application/json".toMediaType()))
            .build()
        val callerBody = client.newCall(request).execute().use { it.body.string() }

        assertThat(callerBody).isEqualTo("""{"id":7}""")
        val transaction = store.transactions.value.single()
        assertThat(transaction.method).isEqualTo("POST")
        assertThat(transaction.path).isEqualTo("/api/bookings?draft=1")
        assertThat(transaction.requestBody).isEqualTo(
            Body.Text("application/json; charset=utf-8", 9, """{"leg":3}""", truncated = false),
        )
        val response = transaction.response
        assertThat(response).isNotNull().prop(HttpResponse::code).isEqualTo(201)
        assertThat(response?.bodyComplete).isEqualTo(true)
        assertThat(response?.body).isEqualTo(Body.Text("application/json", 8, """{"id":7}""", truncated = false))
        assertThat(transaction.status).isEqualTo(HttpTransaction.Status.Complete)
    }

    @Test
    fun `redacts configured headers in both directions regardless of case`() {
        config = config.copy(redactedHeaders = setOf("authorization", "Set-Cookie"))
        server.enqueue(MockResponse.Builder().addHeader("set-cookie", "session=abc").body("ok").build())

        val request = Request.Builder()
            .url(server.url("/"))
            .header("Authorization", "Bearer secret")
            .header("X-Trace", "visible")
            .build()
        client.newCall(request).execute().use { it.body.string() }

        val transaction = store.transactions.value.single()
        assertThat(transaction.requestHeaders).containsExactly(
            Header("Authorization", "██"),
            Header("X-Trace", "visible"),
        )
        assertThat(transaction.response!!.headers.first { it.name.equals("set-cookie", true) }.value)
            .isEqualTo("██")
    }

    @Test
    fun `truncates captured bodies past the limit without truncating what the caller reads`() {
        config = config.copy(maxContentLength = 10)
        val payload = "a".repeat(100)
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/plain").body(payload).build())

        val callerBody = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
            .use { it.body.string() }

        assertThat(callerBody).isEqualTo(payload)
        assertThat(store.transactions.value.single().response?.body)
            .isEqualTo(Body.Text("text/plain", 100, "a".repeat(10), truncated = true))
    }

    @Test
    fun `decodes a gzip body the caller asked to receive compressed`() {
        val gzipped = Buffer().also { buffer -> GzipSink(buffer).buffer().use { it.writeUtf8("hello") } }
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/plain")
                .addHeader("Content-Encoding", "gzip")
                .body(gzipped)
                .build(),
        )

        val request = Request.Builder().url(server.url("/")).header("Accept-Encoding", "gzip").build()
        client.newCall(request).execute().use { it.body.bytes() }

        val body = store.transactions.value.single().response?.body
        assertThat(body).isNotNull().isInstanceOf<Body.Text>().prop(Body.Text::text).isEqualTo("hello")
    }

    @Test
    fun `records binary bodies without their bytes`() {
        val png = Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(png).build())

        client.newCall(Request.Builder().url(server.url("/logo.png")).build()).execute().use { it.body.bytes() }

        assertThat(store.transactions.value.single().response?.body).isEqualTo(Body.Binary("image/png", 8))
    }

    @Test
    fun `marks the transaction failed when the call throws`() {
        val url = server.url("/")
        server.close()

        assertFailure { client.newCall(Request.Builder().url(url).build()).execute() }
            .isInstanceOf<IOException>()

        val transaction = store.transactions.value.single()
        assertThat(transaction.status).isEqualTo(HttpTransaction.Status.Failed)
        assertThat(transaction.response).isNull()
    }

    @Test
    fun `keeps only the newest transactions up to capacity`() {
        config = config.copy(capacity = 2)
        repeat(3) { server.enqueue(MockResponse.Builder().body("ok").build()) }

        listOf("/one", "/two", "/three").forEach { path ->
            client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }
        }

        val paths = store.transactions.value.map { it.path }
        assertThat(paths).containsExactly("/three", "/two")
    }

    @Test
    fun `completes a bodiless response without waiting for a read`() {
        server.enqueue(MockResponse.Builder().code(204).build())

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        val response = store.transactions.value.single().response
        assertThat(response?.bodyComplete).isEqualTo(true)
        assertThat(response?.body).isNull()
    }
}
