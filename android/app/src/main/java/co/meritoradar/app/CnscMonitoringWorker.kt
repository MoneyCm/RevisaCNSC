package co.meritoradar.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WorkManager worker for periodic CNSC monitoring.
 * Executes independent of backend, directly querying CNSC sources.
 */
class CnscMonitoringWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val legacyDatabase = LegacyRadarDatabase.getInstance(context)
    private val dao = legacyDatabase.dao()
    private val httpClient = CnscHttpClient()
    private val catalogParser = CatalogParser(httpClient)
    private val notifier = LocalNotifier(context)

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                val catalogResult = ingestionLock.withLock {
                    val catalog = monitorCatalog()
                    if (catalog.success) {
                        try {
                            NoticeMonitor(legacyDatabase, httpClient, notifier).run()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            android.util.Log.w("CnscMonitoring", "Notice check failed: " + e.javaClass.simpleName + ": " + e.message)
                            return@withLock catalog.copy(success = false,
                                errorSummary = "Catálogo actualizado, pero no se completó la revisión de avisos. Conservamos el histórico.")
                        }
                    }
                    catalog
                }

                if (catalogResult.success) {
                    Result.success(workDataOf("checkedAt" to java.time.Instant.now().toString()))
                } else {
                    if (inputData.getBoolean("manual", false))
                        Result.failure(workDataOf("error" to catalogResult.errorSummary))
                    else Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to "No se pudo completar la revisión."))
        } finally {
            httpClient.close()
        }
    }

    private suspend fun monitorCatalog(): MonitorResult {
        val url = CnscSources.CATALOG
        var sourcesChecked = 0
        var sourcesSucceeded = 0
        var sourcesFailed = 0
        var processesChecked = 0
        var eventsCreated = 0

        return try {
            sourcesChecked++

            // Fetch current state
            val fetchResult = httpClient.fetch(url, emptyMap())

            if (fetchResult.statusCode == 200) {
                // Parse catalog
                val entries = catalogParser.parseCatalog(fetchResult.html)
                processesChecked = entries.size

                // Convert to legacy Process format and detect new processes
                val existingProcesses = dao.observe().first() // Get current processes
                val existingUrls = existingProcesses.map { it.officialUrl }.toSet()

                val newProcesses = if (existingProcesses.isEmpty()) emptyList() else entries.filter { it.officialUrl !in existingUrls }
                val legacyProcesses = entries.map { entry ->
                    val previous = existingProcesses.find { it.officialUrl == entry.officialUrl }
                    Process(
                        id = previous?.id ?: UUID.nameUUIDFromBytes(entry.officialUrl.toByteArray()).toString(),
                        slug = entry.slug,
                        name = entry.name,
                        officialUrl = entry.officialUrl,
                        category = "IN_DEVELOPMENT",
                        status = "UNKNOWN",
                        confidence = "UNCONFIRMED",
                        firstDetectedAt = previous?.firstDetectedAt ?: java.time.Instant.now().toString(),
                        lastCheckedAt = java.time.Instant.now().toString(),
                        updatedAt = java.time.Instant.now().toString()
                    )
                }

                // Save to legacy database
                dao.save(legacyProcesses)

                // Notify about new processes
                for (newProcess in newProcesses) {
                    notifier.showEventNotification(
                        eventId = UUID.randomUUID().toString(),
                        processId = legacyProcesses.first { it.officialUrl == newProcess.officialUrl }.id,
                        processName = newProcess.name,
                        eventType = "PROCESS_DISCOVERED",
                        title = "Proceso añadido al catálogo",
                        message = "Detectado en el catálogo CNSC: ${newProcess.name}",
                        priority = "INFO"
                    )
                    eventsCreated++
                }

                sourcesSucceeded++
                MonitorResult(
                    success = true,
                    sourcesChecked = sourcesChecked,
                    sourcesSucceeded = sourcesSucceeded,
                    sourcesFailed = sourcesFailed,
                    processesChecked = processesChecked,
                    eventsCreated = eventsCreated,
                    errorSummary = null
                )
            } else {
                throw IOException("HTTP ${fetchResult.statusCode}")
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sourcesFailed++
            MonitorResult(
                success = false,
                sourcesChecked = sourcesChecked,
                sourcesSucceeded = sourcesSucceeded,
                sourcesFailed = sourcesFailed,
                processesChecked = processesChecked,
                eventsCreated = eventsCreated,
                errorSummary = when (e) {
                    is javax.net.ssl.SSLException -> "No se pudo validar el certificado HTTPS de CNSC. Los datos guardados se conservan."
                    is ParseError -> "El catálogo CNSC cambió de formato. No se pudieron leer los procesos."
                    else -> "No se pudo consultar CNSC. Revisa tu conexión e inténtalo de nuevo."
                }
            )
        }
    }

    private data class MonitorResult(
        val success: Boolean,
        val sourcesChecked: Int,
        val sourcesSucceeded: Int,
        val sourcesFailed: Int,
        val processesChecked: Int,
        val eventsCreated: Int,
        val errorSummary: String?
    )

    companion object {
        private val ingestionLock = Mutex()
        const val WORK_NAME = "CnscMonitoringWorker"
        const val TAG = "CnscMonitoring"

        private fun periodicRequest(context: Context, intervalMinutes: Int): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return PeriodicWorkRequestBuilder<CnscMonitoringWorker>(
                intervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(TAG)
                .build()
        }

        /**
         * Schedule periodic monitoring work. The stored preference interval_minutes
         * overrides the default so a preference change survives an app restart.
         * Uses KEEP so an already-scheduled periodic work is not recreated on a normal start.
         */
        fun schedule(context: Context, intervalMinutes: Int = 15) {
            val prefs = context.getSharedPreferences("monitor_schedule", Context.MODE_PRIVATE)
            val interval = prefs.getInt("interval_minutes", intervalMinutes).coerceAtLeast(15)

            if (!prefs.getBoolean("interval_fixed_v1", false)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                prefs.edit().putBoolean("interval_fixed_v1", true).apply()
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest(context, interval)
            )
        }

        /**
         * Persist a new review interval and replace the periodic unique work so the
         * change is effective immediately and survives restarts. WorkManager enforces
         * a 15-minute minimum period; the value is clamped before persisting.
         */
        fun updateInterval(context: Context, intervalMinutes: Int) {
            val interval = intervalMinutes.coerceAtLeast(15)
            context.getSharedPreferences("monitor_schedule", Context.MODE_PRIVATE)
                .edit().putInt("interval_minutes", interval).apply()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest(context, interval)
            )
        }

        /**
         * Resume monitoring after a pause. UPDATE updates any pending work or re-enqueues
         * after a cancellation and leaves exactly one active periodic work; it never
         * touches following nor Room. KEEP cannot guarantee re-enqueue after a cancel,
         * so resume uses UPDATE explicitly using the persisted interval.
         */
        fun resume(context: Context) {
            val prefs = context.getSharedPreferences("monitor_schedule", Context.MODE_PRIVATE)
            val interval = prefs.getInt("interval_minutes", 15).coerceAtLeast(15)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest(context, interval)
            )
        }

        /**
         * Cancel periodic monitoring
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Trigger immediate manual sync
         */
        fun triggerManualSync(context: Context): UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CnscMonitoringWorker>()
                .setInputData(workDataOf("manual" to true))
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_manual",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            return workRequest.id
        }
    }
}
