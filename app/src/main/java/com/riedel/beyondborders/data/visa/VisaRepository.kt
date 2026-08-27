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
 * One registry per [Nationality], cached the first time it is asked for. A
 * nationality switch is rare enough that re-parsing on the way back to a
 * passport already seen this session would be wasted work, not a correctness
 * risk — so past registries stay cached rather than being dropped.
 */
class VisaRepository(private val registryLoader: (Nationality) -> VisaRegistry) {

    private val cache = mutableMapOf<Nationality, VisaRegistry>()

    suspend fun registry(nationality: Nationality): VisaRegistry = withContext(Dispatchers.IO) {
        synchronized(cache) { cache.getOrPut(nationality) { registryLoader(nationality) } }
    }
}
