package io.github.ajrnesto.networkowl

import okio.Buffer
import okio.ForwardingSource
import okio.Sink
import okio.Source
import okio.Timeout
import java.io.IOException

internal class CapturingSource(
    delegate: Source,
    private val limit: Long,
    private val onDone: (captured: Buffer, byteCount: Long) -> Unit,
) : ForwardingSource(delegate) {
    private val captured = Buffer()
    private var byteCount = 0L
    private var done = false

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = try {
            super.read(sink, byteCount)
        } catch (e: IOException) {
            finish()
            throw e
        }
        if (read == -1L) {
            finish()
            return -1L
        }
        val keep = minOf(read, limit - captured.size)
        if (keep > 0) sink.copyTo(captured, sink.size - read, keep)
        this.byteCount += read
        return read
    }

    override fun close() {
        try {
            super.close()
        } finally {
            finish()
        }
    }

    private fun finish() {
        if (done) return
        done = true
        onDone(captured, byteCount)
    }
}

internal class LimitingSink(private val limit: Long) : Sink {
    val captured = Buffer()
    var byteCount = 0L
        private set

    override fun write(source: Buffer, byteCount: Long) {
        val keep = minOf(byteCount, limit - captured.size)
        if (keep > 0) captured.write(source, keep)
        source.skip(byteCount - keep)
        this.byteCount += byteCount
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}
