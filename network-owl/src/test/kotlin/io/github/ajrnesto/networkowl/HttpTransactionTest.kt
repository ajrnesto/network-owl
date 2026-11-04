package io.github.ajrnesto.networkowl

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class HttpTransactionTest {

    private val transaction = HttpTransaction(
        id = 1,
        method = "POST",
        url = "https://api.example.com/notes?draft=true",
        host = "api.example.com",
        path = "/notes",
        requestHeaders = emptyList(),
        requestBody = null,
        requestedAt = 0,
        response = HttpResponse(
            code = 404,
            message = "Not Found",
            protocol = "h2",
            headers = emptyList(),
            tookMs = 12,
            body = null,
            bodyComplete = true,
        ),
    )

    @Test
    fun `blank keyword matches everything`() {
        assertThat(transaction.matches("")).isTrue()
        assertThat(transaction.matches("   ")).isTrue()
    }

    @Test
    fun `matches method, host, path, query and status code ignoring case`() {
        assertThat(transaction.matches("post")).isTrue()
        assertThat(transaction.matches("EXAMPLE.com")).isTrue()
        assertThat(transaction.matches("/notes")).isTrue()
        assertThat(transaction.matches("draft=true")).isTrue()
        assertThat(transaction.matches("404")).isTrue()
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertThat(transaction.matches("  notes ")).isTrue()
    }

    @Test
    fun `matches the error of a failed call`() {
        val failed = transaction.copy(response = null, error = "SocketTimeoutException: timeout")

        assertThat(failed.matches("timeout")).isTrue()
    }

    @Test
    fun `failed call matches its ERR badge`() {
        val failed = transaction.copy(response = null, error = "UnknownHostException: api.example.com")

        assertThat(failed.matches("err")).isTrue()
        assertThat(transaction.matches("err")).isFalse()
    }

    @Test
    fun `does not match unrelated text`() {
        assertThat(transaction.matches("users")).isFalse()
        assertThat(transaction.matches("500")).isFalse()
    }
}
