package io.github.ajrnesto.networkowl

import java.util.Locale

internal object TransactionExport {

    fun curl(transaction: HttpTransaction): String = buildString {
        append("curl -X ").append(transaction.method).append(' ').append(quote(transaction.url))
        transaction.requestHeaders.forEach { append(" \\\n  -H ").append(quote("${it.name}: ${it.value}")) }
        val body = transaction.requestBody
        if (body is Body.Text) append(" \\\n  --data-raw ").append(quote(body.text))
    }

    fun text(transaction: HttpTransaction): String = buildString {
        appendLine("${transaction.method} ${transaction.url}")
        val response = transaction.response
        when {
            transaction.error != null -> appendLine("Failed: ${transaction.error}")
            response != null -> appendLine("${response.code} ${response.message} · ${response.protocol} · ${response.tookMs} ms")
            else -> appendLine("Pending")
        }
        section("Request headers", transaction.requestHeaders)
        section("Request body", transaction.requestBody)
        if (response != null) {
            section("Response headers", response.headers)
            section("Response body", response.body)
        }
    }

    private fun StringBuilder.section(title: String, headers: List<Header>) {
        appendLine().appendLine("── $title ──")
        headers.forEach { appendLine("${it.name}: ${it.value}") }
    }

    private fun StringBuilder.section(title: String, body: Body?) {
        if (body == null) return
        appendLine().appendLine("── $title ──")
        when (body) {
            is Body.Text -> appendLine(body.text)
            is Body.Binary -> appendLine("(binary, ${formatBytes(body.byteCount)})")
            is Body.NotCaptured -> appendLine("(not captured)")
        }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1_000.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
}
