package com.riedel.aeroglobe.data.visa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the UI goes to ask "what does this place want from my passport".
 *
 * Thinner than [com.riedel.aeroglobe.data.place.PlaceRepository] because
 * there is nothing to join: visa answers are entirely reference data, with no
 * user decisions layered on top and therefore no database, no writes and no
 * Flow. The only reason this class exists rather than the ViewModel touching
 * [VisaRegistry] directly is to keep the ~29 kB parse off the main thread.
 */
class VisaRepository(registryLoader: () -> VisaRegistry) {

    private val registry: VisaRegistry by lazy(registryLoader)

    suspend fun registry(): VisaRegistry = withContext(Dispatchers.IO) { registry }
}
