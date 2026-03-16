package com.neocraft.game.world

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Object pool for chunk ByteArrays to avoid GC pressure.
 * Reuses freed chunk buffers instead of allocating new ones.
 */
object ChunkBufferPool {
    private val pool = ConcurrentLinkedQueue<ByteArray>()
    private val size = CHUNK_WIDTH * CHUNK_WIDTH * CHUNK_HEIGHT

    fun acquire(): ByteArray = pool.poll()?.also { it.fill(0) } ?: ByteArray(size)
    fun release(buf: ByteArray) { if (pool.size < 32) pool.add(buf) }
}
