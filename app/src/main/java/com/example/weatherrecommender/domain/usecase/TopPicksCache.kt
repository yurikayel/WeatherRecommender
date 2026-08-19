package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.TopPick
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory TTL cache for home-screen top picks so cold starts do not always fan out
 * concurrent forecast requests across every featured city.
 */
@Singleton
class TopPicksCache @Inject constructor() {

    private var cachedAtMillis: Long = 0L
    private var cachedPicks: List<TopPick>? = null

    /** Returns the cached list when it is still within the 45-minute TTL; otherwise null. */
    fun getIfFresh(nowMillis: Long = System.currentTimeMillis()): List<TopPick>? {
        val picks = cachedPicks ?: return null
        return if (nowMillis - cachedAtMillis <= TTL_MILLIS) picks else null
    }

    /** Stores [picks] and stamps [nowMillis] as the cache time. */
    fun put(picks: List<TopPick>, nowMillis: Long = System.currentTimeMillis()) {
        cachedPicks = picks
        cachedAtMillis = nowMillis
    }

    /** Drops the in-memory list so the next home load fetches again. */
    fun clear() {
        cachedPicks = null
        cachedAtMillis = 0L
    }

    private companion object {
        val TTL_MILLIS = 45.minutes.inWholeMilliseconds
    }
}
