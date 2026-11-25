package com.riedel.beyondborders.data.advisory

import android.content.Context
import android.content.res.AssetManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.data.visa.update.HttpVisaFetcher
import com.riedel.beyondborders.data.visa.update.VisaFetchNotFound
import com.riedel.beyondborders.data.visa.update.VisaFetchRejected
import com.riedel.beyondborders.data.visa.update.VisaFetcher
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/*
 * Travel advisories ride the visa updater's host, schedule and constraints but nothing else:
 * their own file, store, worker and unique work name. They share no state with the visa
 * run, so a failure on either side cannot block, alter or mark failure of the other.
 */

/** Disk side: one file. */
interface AdvisoryStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

/**
 * `noBackupFilesDir/advisory/advisory.json`, for the same reason as the visa files: Auto
 * Backup must not carry an old copy onto a fresh install where it could outrank a newer
 * bundled asset. Reference data, so "Delete everything" leaves it alone.
 */
class FileAdvisoryStore(private val dir: File) : AdvisoryStore {
    private val lock = Any()

    override fun read(): ByteArray? = synchronized(lock) {
        File(dir, ADVISORY_FILE).takeIf { it.isFile }?.readBytes()
    }

    override fun write(bytes: ByteArray) { synchronized(lock) {
        dir.mkdirs()
        // A process killed between create and move leaves its temp file behind; nothing else uses these names.
        dir.listFiles { f -> f.name.startsWith("advisory-") && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        val tmp = File.createTempFile("advisory-", ".tmp", dir)
        try {
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), File(dir, ADVISORY_FILE).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    } }

    companion object {
        fun forContext(context: Context) = FileAdvisoryStore(File(context.noBackupFilesDir, "advisory"))
    }
}

/**
 * NEEDS_NEWER_APP: the published file is in a newer format than this build reads. Calm, not a
 * failure: the bundled or stored copy keeps working. NOT_PUBLISHED: the host answered 404, so there is
 * nothing to be up to date with; also calm (the worker treats it as success), but Check now says so
 * rather than claiming the data is current.
 */
enum class AdvisoryOutcome { UPDATED, UP_TO_DATE, NOT_PUBLISHED, OFFLINE, REJECTED, NEEDS_NEWER_APP }

/** Only a network failure is worth the scheduler's backoff; everything else waits for the next period. */
internal fun shouldRetryAdvisory(outcome: AdvisoryOutcome): Boolean = outcome == AdvisoryOutcome.OFFLINE

/**
 * One whole run: fetch, verify, write. Never throws for network trouble, and never deletes:
 * a failed or unusable fetch leaves whatever copy is stored exactly as it was.
 */
internal fun runAdvisoryUpdate(
    fetcher: VisaFetcher,
    store: AdvisoryStore,
    bundled: () -> AdvisoryData?,
    now: () -> Instant = ::nowInstant,
    minCountries: Int = MIN_ADVISORY_COUNTRIES,
): AdvisoryOutcome {
    val bytes = try {
        fetcher.fetch(ADVISORY_FILE, MAX_ADVISORY_BYTES)
    } catch (e: IOException) {
        return AdvisoryOutcome.OFFLINE
    } catch (e: VisaFetchNotFound) {
        // Not published yet: not the user's problem, so calm, but not "up to date" either.
        return AdvisoryOutcome.NOT_PUBLISHED
    } catch (e: VisaFetchRejected) {
        return AdvisoryOutcome.REJECTED
    }
    val t = now()
    val stored = runCatching { store.read()?.let { parseAdvisory(it.toString(Charsets.UTF_8)) } }.getOrNull()
    val inUse = chooseAdvisory(runCatching(bundled).getOrNull(), stored, t)
    val inUseSources = inUse?.let(::advisorySourceCounts) ?: emptyMap()
    return when (verifyAdvisory(bytes, inUse?.generatedAt, t, inUse?.countries?.size ?: 0, minCountries, inUseSources)) {
        null -> if (runCatching { store.write(bytes) }.isSuccess) AdvisoryOutcome.UPDATED else AdvisoryOutcome.REJECTED
        AdvisoryRejection.NOT_NEWER -> AdvisoryOutcome.UP_TO_DATE
        AdvisoryRejection.NEWER_FORMAT -> AdvisoryOutcome.NEEDS_NEWER_APP
        else -> AdvisoryOutcome.REJECTED
    }
}

/**
 * Parsed advisory data, bundled asset first and a downloaded copy when it is strictly newer.
 * A missing or unparsable asset is "no advisory data" ([current] is null), never a crash.
 */
class AdvisoryRepository(
    private val bundledLoader: () -> ByteArray?,
    private val store: AdvisoryStore,
    private val now: () -> Instant = ::nowInstant,
) {
    private var cache: AdvisoryData? = null
    private var loaded = false
    // Bumped by invalidate(); a load that began before it must not publish its stale result.
    private var generation = 0
    private val lock = Any()

    private val _dataVersion = MutableStateFlow(0)

    /** Changes on every [invalidate]; a screen holding data reloads when it does. */
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    suspend fun current(): AdvisoryData? = withContext(Dispatchers.IO) {
        var result: AdvisoryData? = null
        var done = false
        while (!done) {
            val startedAt = synchronized(lock) { if (loaded) { result = cache; done = true }; generation }
            if (done) break
            val bundled = runCatching { bundledLoader()?.let { parseAdvisory(it.toString(Charsets.UTF_8)) } }.getOrNull()
            val stored = runCatching { store.read()?.let { parseAdvisory(it.toString(Charsets.UTF_8)) } }.getOrNull()
            val chosen = chooseAdvisory(bundled, stored, now())
            // Invalidated while reading: what was read may predate the new file, so read again.
            synchronized(lock) {
                if (generation == startedAt) { cache = chosen; loaded = true; result = chosen; done = true }
            }
        }
        result
    }

    /**
     * The data if [current] has already loaded it, else null (also null for "no data"). Never
     * touches the disk, so a screen can use it as a first value without waiting for [current].
     */
    fun cached(): AdvisoryData? = synchronized(lock) { if (loaded) cache else null }

    fun invalidate() {
        synchronized(lock) { cache = null; loaded = false; generation++ }
        _dataVersion.update { it + 1 }
    }
}

internal fun readBundledAdvisory(assets: AssetManager): ByteArray? =
    runCatching { assets.open(ADVISORY_FILE).use { it.readBytes() } }.getOrNull()

suspend fun performAdvisoryUpdate(app: BeyondBordersApp): AdvisoryOutcome = withContext(Dispatchers.IO) {
    val container = app.container
    val outcome = runAdvisoryUpdate(
        fetcher = HttpVisaFetcher(),
        store = container.advisoryStore,
        bundled = { runCatching { readBundledAdvisory(app.assets)?.toString(Charsets.UTF_8)?.let(::parseAdvisory) }.getOrNull() },
    )
    if (outcome == AdvisoryOutcome.UPDATED) container.advisory.invalidate()
    outcome
}

class AdvisoryUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val outcome = performAdvisoryUpdate(applicationContext as BeyondBordersApp)
        if (shouldRetryAdvisory(outcome)) Result.retry() else Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.success()
    }
}

/** Same cadence and constraint as the visa job, under its own unique name. */
interface AdvisoryScheduling {
    fun sync(enabled: Boolean)

    /** Runs a check now, in the caller's coroutine. Never throws for network trouble. */
    suspend fun checkNow(): AdvisoryOutcome

    object None : AdvisoryScheduling {
        override fun sync(enabled: Boolean) = Unit
        override suspend fun checkNow(): AdvisoryOutcome = AdvisoryOutcome.UP_TO_DATE
    }
}

class WorkManagerAdvisoryScheduling(context: Context) : AdvisoryScheduling {
    private val appContext = context.applicationContext

    override fun sync(enabled: Boolean) {
        val wm = WorkManager.getInstance(appContext)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AdvisoryUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override suspend fun checkNow(): AdvisoryOutcome = try {
        performAdvisoryUpdate(appContext as BeyondBordersApp)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AdvisoryOutcome.REJECTED
    }

    private companion object {
        const val PERIODIC_NAME = "advisory-update-periodic"
    }
}
