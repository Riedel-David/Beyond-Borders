package com.riedel.beyondborders.data.visa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the UI goes to ask "what does this place want from my passport".
 *
 * Thinner than [com.riedel.beyondborders.data.place.PlaceRepository] because
 * there is nothing to join: visa answers are entirely reference data, with no
 * user decisions layered on top and therefore no database, no writes and no
 * Flow. The only reason this class exists rather than the ViewModel touching
 * [VisaRegistry] directly is to keep the ~29 kB parse off the main thread.
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

    suspend fun registry(passport: String): VisaRegistry = withContext(Dispatchers.IO) {
        synchronized(cache) { cache.getOrPut(passport) { registryLoader(passport) } }
    }

    /** Every passport the picker can offer, sorted. */
    suspend fun availablePassports(): List<String> = withContext(Dispatchers.IO) { passportsLoader() }
}
