package io.github.ajrnesto.networkowl

import okhttp3.MediaType
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.io.IOException

internal object BodyDecoder {
    private val textSubtypes = listOf("json", "xml", "html", "javascript", "x-www-form-urlencoded", "graphql")

    fun decode(captured: Buffer, byteCount: Long, contentType: MediaType?, contentEncoding: String?): Body {
        val type = contentType?.toString()
        val truncated = byteCount > captured.size
        val content = if (contentEncoding.equals("gzip", ignoreCase = true)) gunzip(captured) else captured
        if (!isText(contentType, content)) return Body.Binary(type, byteCount)
        val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return Body.Text(type, byteCount, content.readString(charset), truncated)
    }

    private fun isText(contentType: MediaType?, content: Buffer): Boolean {
        if (contentType != null) {
            if (contentType.type == "text") return true
            if (textSubtypes.any { it in contentType.subtype }) return true
            if (contentType.type == "image" || contentType.type == "audio" || contentType.type == "video") return false
        }
        return content.isProbablyUtf8()
    }

    // A truncated capture cuts the gzip stream mid-member, so read what inflates and stop at EOF.
    private fun gunzip(compressed: Buffer): Buffer {
        val out = Buffer()
        try {
            GzipSource(compressed.copy()).use(out::writeAll)
        } catch (e: EOFException) {
            return out
        } catch (e: IOException) {
            return compressed
        }
        return out
    }

    private fun Buffer.isProbablyUtf8(): Boolean {
        val prefix = Buffer()
        copyTo(prefix, 0, minOf(size, 64))
        repeat(16) {
            if (prefix.exhausted()) return true
            val codePoint = prefix.readUtf8CodePoint()
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) return false
        }
        return true
    }
}
