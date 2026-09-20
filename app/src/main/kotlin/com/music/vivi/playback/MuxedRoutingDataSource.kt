package com.music.vivi.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** Pins one route per progressive period, including extractor range reopens. */
internal class MuxedRoutingDataSource(
    private val audioFactory: DataSource.Factory,
    private val muxedFactory: DataSource.Factory,
    private val resolve: () -> DataSpec?,
    private val onMuxedError: (IOException) -> Unit,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    private var muxedSpec: DataSpec? = null
    private var resolved = false

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        if (!resolved) {
            muxedSpec = resolve()
            resolved = true
        }
        val muxed = muxedSpec
        val source = (if (muxed == null) audioFactory else muxedFactory).createDataSource()
        delegate = source
        listeners.forEach(source::addTransferListener)
        return try {
            source.open(if (muxed == null) dataSpec else dataSpec.buildUpon()
                .setUri(muxed.uri).setKey(muxed.key).setHttpRequestHeaders(muxed.httpRequestHeaders).build())
        } catch (error: IOException) {
            if (muxed != null) onMuxedError(error)
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        checkNotNull(delegate).read(buffer, offset, length)
    } catch (error: IOException) {
        if (muxedSpec != null) onMuxedError(error)
        throw error
    }

    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
    override fun close() {
        try { delegate?.close() } finally { delegate = null }
    }
}
