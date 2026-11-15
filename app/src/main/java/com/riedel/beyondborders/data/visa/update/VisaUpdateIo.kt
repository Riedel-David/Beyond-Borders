package com.riedel.beyondborders.data.visa.update

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
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.data.visa.VisaRegistry
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloaded visa files, one `<CODE>.json` each under `noBackupFilesDir/visa`.
 *
 * noBackupFilesDir, not filesDir: Auto Backup would otherwise carry these onto a fresh
 * install, where they would outrank a newer bundled asset. The directory is outside both
 * backup rule files' scope by definition, so no exclusion is needed there. These are
 * reference data, so "Delete everything" leaves them alone.
 */
class FileVisaStore(private val dir: File) : VisaStore {
    // Two workers (periodic and Check now) can overlap; one lock per store, and the
    // container hands out a single store.
    private val lock = Any()

    override fun codes(): Set<String> = synchronized(lock) {
        dir.listFiles()?.mapNotNull { f ->
            f.name.removeSuffix(".json").takeIf { f.name.endsWith(".json") && CODE_REGEX.matches(it) }
        }?.toSet() ?: emptySet()
    }

    override fun read(code: String): ByteArray? = synchronized(lock) {
        File(dir, "$code.json").takeIf { it.isFile }?.readBytes()
    }

    override fun write(code: String, bytes: ByteArray) { synchronized(lock) {
        dir.mkdirs()
        val tmp = File.createTempFile("$code-", ".tmp", dir)
        try {
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), File(dir, "$code.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    } }

    override fun delete(code: String) {
        synchronized(lock) { File(dir, "$code.json").delete() }
    }

    companion object {
        fun forContext(context: Context) = FileVisaStore(File(context.noBackupFilesDir, "visa"))
    }
}

/** Plain GET, no headers beyond the platform defaults, no query string, no cookies kept. */
class HttpVisaFetcher(private val baseUrl: String = UPDATE_BASE_URL) : VisaFetcher {
    override fun fetch(file: String, maxBytes: Int): ByteArray {
        val url = baseUrl + file
        if (!isHttps(url)) throw VisaFetchRejected("not https")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.readTimeout = NETWORK_TIMEOUT_MS
            conn.useCaches = false
            // The platform default names the device model and Android build; send nothing of that.
            conn.setRequestProperty("User-Agent", "BeyondBorders")
            val status = conn.responseCode
            // Most 4xx will not fix themselves on a retry; 408, 429, timeouts and 5xx might.
            if (status != HttpURLConnection.HTTP_OK) {
                if (status in 400..499 && !isRetryableStatus(status)) throw VisaFetchRejected("HTTP $status")
                throw IOException("HTTP $status")
            }
            if (!isHttps(conn.url.toString())) throw VisaFetchRejected("redirected off https")
            if (conn.contentLengthLong > maxBytes) throw VisaFetchRejected("too large")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw VisaFetchRejected("too large")
                }
                return out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * One whole check, shared by the weekly worker and the Check now button so the two cannot
 * drift apart: fetch, verify, apply, tell the repository to drop its parsed copies (which
 * also refreshes the running app) and record the check. Throws only what [runUpdate] throws.
 */
suspend fun performVisaUpdate(container: AppContainer, assets: AssetManager): UpdateResult = withContext(Dispatchers.IO) {
    val settings = container.settings
    val wanted = setOfNotNull(settings.nationality.value, settings.secondNationality.value)
    val result = runUpdate(
        fetcher = HttpVisaFetcher(),
        store = container.visaStore,
        wanted = wanted,
        bundledAsOf = { code -> runCatching { VisaRegistry.load(assets, code).asOf }.getOrNull() },
    )
    if (result.changed) container.visa.invalidate()
    if (result.manifestSeen) settings.setVisaLastCheckAt(System.currentTimeMillis())
    result
}

class VisaUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val container = (applicationContext as BeyondBordersApp).container
        val result = performVisaUpdate(container, applicationContext.assets)
        if (result.ioFailure) Result.retry() else Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Anything unexpected leaves the app as it was; a loop of failures helps nobody.
        Result.success()
    }
}

/** What the Settings screen drives; the no-op default keeps ViewModel tests free of WorkManager. */
interface VisaUpdateScheduling {
    fun sync(enabled: Boolean)

    /** Runs a check now, in the caller's coroutine, and says what came of it. Never throws. */
    suspend fun checkNow(): VisaCheckOutcome

    object None : VisaUpdateScheduling {
        override fun sync(enabled: Boolean) = Unit
        override suspend fun checkNow(): VisaCheckOutcome = VisaCheckOutcome.UpToDate
    }
}

class WorkManagerVisaScheduling(context: Context) : VisaUpdateScheduling {
    private val appContext = context.applicationContext

    override fun sync(enabled: Boolean) {
        val wm = WorkManager.getInstance(appContext)
        if (!enabled) {
            // The switch governs only the weekly job; Check now does not go through WorkManager.
            wm.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<VisaUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override suspend fun checkNow(): VisaCheckOutcome = try {
        val container = (appContext as BeyondBordersApp).container
        outcomeOf(performVisaUpdate(container, appContext.assets))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VisaCheckOutcome.Unavailable
    }

    private companion object {
        const val PERIODIC_NAME = "visa-update-periodic"
    }
}
