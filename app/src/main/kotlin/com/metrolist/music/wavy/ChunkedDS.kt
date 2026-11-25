package com.metrolist.music.wavy

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Reads a googlevideo URL in bounded range chunks (YouTube rejects open-ended ranges on several clients). */
class ChunkedDS(private val factory: DataSource.Factory, private val chunk: Long = 2L * 1024 * 1024) : DataSource {
    private var inner: DataSource? = null
    private var spec: DataSpec? = null
    private var next = 0L
    private var end = Long.MAX_VALUE
    private var total = C.LENGTH_UNSET.toLong()
    private var headers: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(l: TransferListener) {}

    private fun openChunk() {
        inner?.close()
        val s = spec!!
        val len = minOf(chunk, end - next)
        val d = factory.createDataSource()
        inner = d
        d.open(s.buildUpon().setPosition(next).setLength(len).build())
        headers = d.responseHeaders
        val cr = headers.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull()
        val t = cr?.substringAfter('/', "")?.toLongOrNull()
        if (t != null) total = t
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        next = dataSpec.position
        end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else dataSpec.position + dataSpec.length
        openChunk()
        if (total != C.LENGTH_UNSET.toLong()) end = minOf(end, total)
        return if (end == Long.MAX_VALUE) C.LENGTH_UNSET.toLong() else end - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val d = inner ?: return C.RESULT_END_OF_INPUT
            val n = d.read(buffer, offset, length)
            if (n != C.RESULT_END_OF_INPUT) { next += n; return n }
            if (next >= end) return C.RESULT_END_OF_INPUT
            openChunk()
        }
    }

    override fun getUri(): Uri? = inner?.uri ?: spec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = headers
    override fun close() { inner?.close(); inner = null }
}
