package io.github.ajrnesto.networkowl

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class TransactionExportTest {

    @Test
    fun `curl escapes single quotes in headers and body`() {
        val transaction = HttpTransaction(
            id = 1,
            method = "POST",
            url = "https://api.example.com/notes",
            host = "api.example.com",
            path = "/notes",
            requestHeaders = listOf(Header("X-Name", "O'Brien")),
            requestBody = Body.Text("application/json", 15, """{"note":"it's"}""", truncated = false),
            requestedAt = 0,
        )

        assertThat(TransactionExport.curl(transaction)).isEqualTo(
            """
            curl -X POST 'https://api.example.com/notes' \
              -H 'X-Name: O'\''Brien' \
              --data-raw '{"note":"it'\''s"}'
            """.trimIndent(),
        )
    }
}
