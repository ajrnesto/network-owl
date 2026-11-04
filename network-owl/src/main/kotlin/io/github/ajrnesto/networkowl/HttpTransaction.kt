package io.github.ajrnesto.networkowl

internal data class HttpTransaction(
    val id: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val requestHeaders: List<Header>,
    val requestBody: Body?,
    val requestedAt: Long,
    val response: HttpResponse? = null,
    val error: String? = null,
) {
    val status: Status
        get() = when {
            error != null -> Status.Failed
            response == null -> Status.Pending
            else -> Status.Complete
        }

    val badge: String?
        get() = when (status) {
            Status.Pending -> null
            Status.Failed -> "ERR"
            Status.Complete -> response?.code.toString()
        }

    fun matches(keyword: String): Boolean {
        val needle = keyword.trim()
        if (needle.isEmpty()) return true
        return listOfNotNull(method, url, badge, error)
            .any { it.contains(needle, ignoreCase = true) }
    }

    enum class Status { Pending, Complete, Failed }
}

internal data class HttpResponse(
    val code: Int,
    val message: String,
    val protocol: String,
    val headers: List<Header>,
    val tookMs: Long,
    val body: Body?,
    val bodyComplete: Boolean,
)

internal data class Header(val name: String, val value: String)

internal sealed interface Body {
    val contentType: String?
    val byteCount: Long

    data class Text(
        override val contentType: String?,
        override val byteCount: Long,
        val text: String,
        val truncated: Boolean,
    ) : Body

    data class Binary(
        override val contentType: String?,
        override val byteCount: Long,
    ) : Body

    data class NotCaptured(
        override val contentType: String?,
        override val byteCount: Long,
    ) : Body
}
