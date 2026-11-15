package com.riedel.beyondborders.data.visa

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisaRepositoryTest {

    @Test
    fun `a load that started before invalidate does not cache its stale result`() = runBlocking {
        val json = File("src/main/assets/visa.json").readText()
        val loads = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = VisaRepository(
            registryLoader = {
                if (loads.incrementAndGet() == 1) {
                    started.countDown()
                    release.await()
                }
                VisaRegistry.parse(json)
            },
            passportsLoader = { emptyList() },
        )
        val first = async(Dispatchers.IO) { repo.registry("DE") }
        started.await()
        repo.invalidate()
        release.countDown()
        first.await()
        repo.registry("DE")
        assertEquals(2, loads.get())
    }

    @Test
    fun `every invalidate moves dataVersion to a new value`() {
        val repo = VisaRepository(registryLoader = { error("unused") }, passportsLoader = { emptyList() })
        val seen = mutableListOf(repo.dataVersion.value)
        repeat(3) { repo.invalidate(); seen += repo.dataVersion.value }
        assertEquals(seen.distinct(), seen)
    }
}
