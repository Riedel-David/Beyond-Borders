package com.riedel.beyondborders.data.visa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Where the UI goes to ask "what does this place want from my passport".
 *
 * Thinner than [com.riedel.beyondborders.data.place.PlaceRepository] because
 * there is nothing to join: visa answers are entirely reference data, with no
 * user decisions layered on top and therefore no database, no writes and no
 * Flow. The only reason this class exists rather than the ViewModel touching
 * [VisaRegistry] directly is to keep the ~26-46 kB parse off the main thread.
 *
 * One registry per passport code, cached the first time it is asked for. A
 * passport switch is rare enough that re-parsing on the way back to a
 * passport already seen this session would be wasted work, not a correctness
 * risk — so past registries stay cached rather than being dropped.
 */
class VisaRepository(
    private val registryLoader: (String) -> VisaRegistry,
    private val passportsLoader: () -> List<String>,
) {

    private val cache = mutableMapOf<String, VisaRegistry>()

    // Bumped by [invalidate]; a load that began before it must not cache its stale result.
    private var generation = 0

    suspend fun registry(passport: String): VisaRegistry = withContext(Dispatchers.IO) {
        val startedAt = synchronized(cache) { cache[passport]?.let { return@withContext it }; generation }
        // Parsed outside the lock. Holding it across the read made a passport switch
        // wait out any other passport's parse — and wait by blocking an IO thread
        // rather than suspending, since this is a monitor and not a Mutex. Two
        // callers racing the same cold passport now both parse it: one redundant
        // ~26-46 kB parse, and getOrPut still hands both of them the same instance.
        val loaded = registryLoader(passport)
        synchronized(cache) { if (generation == startedAt) cache.getOrPut(passport) { loaded } else loaded }
    }

    // A counter rather than a Boolean so every invalidate is a new value: a StateFlow
    // swallows an assignment equal to the current one, and a repeat update must still reload.
    private val _dataVersion = MutableStateFlow(0)

    /** Changes on every [invalidate]; holders of parsed registries reload when it does. */
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /**
     * Forget every parsed registry, so the next [registry] call re-reads (and so picks up
     * freshly downloaded data), and tell [dataVersion] collectors to reload what they hold.
     * The cache is cleared and the generation bumped before the version moves, so a
     * reload triggered by it can never be served the old copy.
     */
    fun invalidate() {
        synchronized(cache) { cache.clear(); generation++ }
        _dataVersion.update { it + 1 }
    }

    /** Every passport the picker can offer, sorted. */
    suspend fun availablePassports(): List<String> = withContext(Dispatchers.IO) { passportsLoader() }
}
