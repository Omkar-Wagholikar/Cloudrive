package com.example.cloudrive.playback

import com.example.cloudrive.data.local.music.TrackEntity
import kotlin.random.Random

/** Pure list-transform logic for queue mutation, kept free of ExoPlayer/Android dependencies. */
class QueueManager {

    fun addNext(queue: List<TrackEntity>, currentIndex: Int, track: TrackEntity): List<TrackEntity> {
        val insertAt = (currentIndex + 1).coerceIn(0, queue.size)
        return queue.toMutableList().apply { add(insertAt, track) }
    }

    fun remove(queue: List<TrackEntity>, index: Int): List<TrackEntity> {
        if (index !in queue.indices) return queue
        return queue.toMutableList().apply { removeAt(index) }
    }

    fun move(queue: List<TrackEntity>, from: Int, to: Int): List<TrackEntity> {
        if (from !in queue.indices || to !in queue.indices) return queue
        return queue.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Permutation of [queue] indices with [currentIndex] pinned at position 0. */
    fun shuffledOrder(queue: List<TrackEntity>, currentIndex: Int, seed: Long = System.currentTimeMillis()): List<Int> {
        if (queue.isEmpty()) return emptyList()
        val remaining = queue.indices.filter { it != currentIndex }.shuffled(Random(seed))
        if (currentIndex !in queue.indices) return remaining
        return listOf(currentIndex) + remaining
    }
}
