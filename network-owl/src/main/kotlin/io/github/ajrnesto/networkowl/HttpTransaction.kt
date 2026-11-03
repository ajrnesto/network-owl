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
