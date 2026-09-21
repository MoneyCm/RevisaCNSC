package co.meritoradar.app

import android.app.Application
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Legacy models for compatibility with existing UI code
@Entity(tableName = "processes")
data class Process(
    @PrimaryKey val id: String,
    val slug: String = "",
    val name: String,
    val officialUrl: String,
    val category: String = "IN_DEVELOPMENT",
    val status: String = "UNKNOWN",
    val confidence: String = "UNCONFIRMED",
    val firstDetectedAt: String = "",
    val lastCheckedAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "following")
data class Following(@PrimaryKey val processId: String)

data class Health(val status: String, @SerializedName("last_monitor_run") val lastRun: String?,
    @SerializedName("sources_ok") val sourcesOk: Int, @SerializedName("sources_failed") val sourcesFailed: Int, @SerializedName("review_required") val reviewRequired: Int)

data class NoticeStateSnapshot(val state: NoticeState?, val savedAt: Instant?)

data class AndroidEnvironment(
    val notificationPermission: Boolean?,
    val channelsBlocked: List<String>,
    val batteryOptimizationExempt: Boolean?
)

@Entity(tableName = "content_cache")
data class ContentCache(@PrimaryKey val cacheKey: String, val payload: String, val savedAt: Long)

data class StageInfo(val id: String, val kind: String, val modality: String, val population: String,
    @SerializedName("start_date") val startDate: String?, @SerializedName("end_date") val endDate: String?,
    val status: String, val confidence: String, @SerializedName("official_url") val officialUrl: String)
data class EvidenceInfo(val url: String?, val excerpt: String?, val old: Map<String, String?>?, val new: Map<String, String?>?)
data class EventInfo(val id: String, @SerializedName("process_id") val processId: String,
    @SerializedName("event_type") val eventType: String, val title: String, val priority: String,
    val confidence: String, @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("detected_at") val detectedAt: String, @SerializedName("notify_eligible") val notifyEligible: Boolean,
    val evidence: EvidenceInfo?)
data class DocumentInfo(val url: String, val title: String)
data class PublicationInfo(val id: String, val title: String, val confidence: String,
    @SerializedName("published_at") val publishedAt: String?, @SerializedName("official_url") val officialUrl: String,
    val paragraphs: List<String>, val documents: List<DocumentInfo>)
data class DetailContent(val stages: List<StageInfo>, val events: List<EventInfo>, val publications: List<PublicationInfo>)
data class AlertContent(val events: List<EventInfo>)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS content_cache (cacheKey TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, savedAt INTEGER NOT NULL)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add slug column to processes table
        try {
            db.execSQL("ALTER TABLE processes ADD COLUMN slug TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            // Column might already exist
        }
    }
}

@Dao
interface RadarDao {
    @Query("SELECT * FROM content_cache WHERE cacheKey LIKE 'activity_check:%'") fun activityChecks(): Flow<List<ContentCache>>
    @Query("SELECT * FROM content_cache WHERE cacheKey LIKE 'activity:%'") fun activities(): Flow<List<ContentCache>>
    @Query("SELECT * FROM content_cache WHERE cacheKey LIKE 'identity:%'") fun identities(): Flow<List<ContentCache>>
    @Query("SELECT * FROM content_cache WHERE cacheKey LIKE 'detail:%'") fun details(): Flow<List<ContentCache>>
    @Query("SELECT * FROM content_cache WHERE cacheKey = :key") fun cached(key: String): Flow<ContentCache?>
    @Upsert suspend fun cache(item: ContentCache)
    @Transaction suspend fun saveDetail(process: Process, content: ContentCache) { save(listOf(process)); cache(content) }
    @Query("SELECT * FROM processes ORDER BY name") fun observe(): Flow<List<Process>>
    @Query("SELECT processId FROM following") fun following(): Flow<List<String>>
    @Upsert suspend fun save(items: List<Process>)
    @Upsert suspend fun follow(item: Following)
    @Query("DELETE FROM following WHERE processId = :id") suspend fun unfollow(id: String)
}
@Database(entities = [Process::class, Following::class, ContentCache::class], version = 3, exportSchema = true)
abstract class LegacyRadarDatabase : RoomDatabase() {
    abstract fun dao(): RadarDao

    companion object {
        @Volatile
        private var INSTANCE: LegacyRadarDatabase? = null

        fun getInstance(context: Context): LegacyRadarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): LegacyRadarDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LegacyRadarDatabase::class.java,
                "radar.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}

class LocalRadarRepository(private val dao: RadarDao, private val context: android.content.Context) {
    private val gson = Gson()
    val activityChecks = dao.activityChecks().map { entries ->
        entries.map { gson.fromJson(it.payload, ActivityCheck::class.java) }
    }
    val activities = dao.activities().map { entries -> entries.associate {
        it.cacheKey.removePrefix("activity:") to gson.fromJson(it.payload, ProcessActivity::class.java)
    } }
    private val workManager = androidx.work.WorkManager.getInstance(context)
    val identities = dao.identities().map { entries -> entries.associate {
        it.cacheKey.removePrefix("identity:") to gson.fromJson(it.payload, ProcessIdentity::class.java)
    } }
    val stageSummaries = dao.details().map { entries -> entries.associate {
        val detail = gson.fromJson(it.payload, DetailContent::class.java)
        it.cacheKey.removePrefix("detail:") to (stageSummary(detail.stages).takeUnless { it == "Sin etapa confirmada" }
            ?: officialActivitySummary(detail.events) ?: "Sin etapa confirmada")
    } }
    val alerts = dao.cached("alerts").map { it?.let { cache -> gson.fromJson(cache.payload, AlertContent::class.java).events } ?: emptyList() }
    fun observeDetail(id: String) = dao.cached("detail:$id").map { it?.let { cache -> gson.fromJson(cache.payload, DetailContent::class.java) } }
    val processes = dao.observe()
    val following = dao.following()

    private val monitorPrefs = context.getSharedPreferences("monitor_schedule", android.content.Context.MODE_PRIVATE)
    private val _monitorInterval = MutableStateFlow(monitorPrefs.getInt("interval_minutes", 15))
    val monitorInterval: StateFlow<Int> = _monitorInterval.asStateFlow()
    private val _monitoringPaused = MutableStateFlow(monitorPrefs.getBoolean("monitoring_paused_v1", false))
    val monitoringPaused: StateFlow<Boolean> = _monitoringPaused.asStateFlow()

    val noticeState = dao.cached("notice_state").map { cache ->
        cache?.let { NoticeStateSnapshot(gson.fromJson(it.payload, NoticeState::class.java), Instant.ofEpochMilli(it.savedAt)) }
            ?: NoticeStateSnapshot(null, null)
    }

    fun observePeriodicWork(): Flow<PeriodicWorkSnapshot?> = workManager
        .getWorkInfosForUniqueWorkFlow(CnscMonitoringWorker.WORK_NAME)
        .map { infos ->
            Diagnostics.selectActivePeriodic(infos.map {
                PeriodicWorkCandidate(
                    state = it.state.name,
                    runAttemptCount = it.runAttemptCount,
                    nextRunAtMillis = it.nextScheduleTimeMillis.takeIf { t -> t > 0L }
                )
            })
        }

    private fun androidEnvironment(): AndroidEnvironment {
        val notifier = LocalNotifier(context)
        val channels = notifier.channelStatuses()
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        return AndroidEnvironment(
            notificationPermission = runCatching { notifier.hasNotificationPermission() }.getOrNull(),
            channelsBlocked = channels.filter { it.blocked }.map { it.label },
            batteryOptimizationExempt = runCatching { powerManager.isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
        )
    }

    /** Entorno Android observable. No usa polling: se re-lee solo con un trigger explícito
     *  de ciclo de vida (onResume de la Activity y al entrar en la pestaña Ajustes). */
    private val _androidEnvironment = MutableStateFlow(androidEnvironment())
    val androidEnvironment: StateFlow<AndroidEnvironment> = _androidEnvironment.asStateFlow()

    fun refreshAndroidEnvironment() {
        _androidEnvironment.value = androidEnvironment()
    }

    val diagnostics: Flow<DiagnosticsSnapshot> = combine(
        processes,
        following,
        activityChecks,
        noticeState,
        observePeriodicWork(),
        monitorInterval,
        monitoringPaused,
        androidEnvironment
    ) { values ->
        Diagnostics.build(
            now = Instant.now(),
            processes = values[0] as List<Process>,
            followedIds = (values[1] as List<String>).toSet(),
            checks = values[2] as List<ActivityCheck>,
            noticeState = (values[3] as NoticeStateSnapshot).state,
            noticeStateSavedAt = (values[3] as NoticeStateSnapshot).savedAt,
            periodic = values[4] as PeriodicWorkSnapshot?,
            periodMinutes = values[5] as Int,
            paused = values[6] as Boolean,
            notificationPermission = (values[7] as AndroidEnvironment).notificationPermission,
            channelsBlocked = (values[7] as AndroidEnvironment).channelsBlocked,
            batteryOptimizationExempt = (values[7] as AndroidEnvironment).batteryOptimizationExempt
        )
    }

    fun setMonitorInterval(minutes: Int) {
        val interval = Diagnostics.coercePeriod(minutes)
        CnscMonitoringWorker.updateInterval(context, interval)
        _monitorInterval.value = interval
    }

    fun setMonitoringPaused(paused: Boolean) {
        monitorPrefs.edit().putBoolean("monitoring_paused_v1", paused).apply()
        _monitoringPaused.value = paused
        if (paused) CnscMonitoringWorker.cancel(context)
        else CnscMonitoringWorker.resume(context)
    }

    suspend fun refresh(): Health {
        val id = CnscMonitoringWorker.triggerManualSync(context)
        val result = kotlinx.coroutines.withTimeoutOrNull(240_000) {
            workManager.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }
        } ?: throw java.io.IOException("La revisión sigue pendiente. Comprueba la conexión a internet.")
        if (result.state != androidx.work.WorkInfo.State.SUCCEEDED) {
            throw java.io.IOException(result.outputData.getString("error") ?: "No se pudo completar la revisión.")
        }
        val followed = dao.following().first().toSet()
        val failures = activityChecks.first().count { it.processId in followed && it.error != null }
        return Health(if (failures == 0) "operational" else "degraded",
            result.outputData.getString("checkedAt"), 2, failures, 0)
    }

    fun observeWorkManagerStatus(): Flow<String> {
        return workManager.getWorkInfosByTagFlow(CnscMonitoringWorker.TAG)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when {
                    workInfo == null -> "idle"
                    workInfo.state == androidx.work.WorkInfo.State.RUNNING -> "running"
                    workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED -> "succeeded"
                    workInfo.state == androidx.work.WorkInfo.State.FAILED -> "failed"
                    workInfo.state == androidx.work.WorkInfo.State.ENQUEUED -> "enqueued"
                    workInfo.state == androidx.work.WorkInfo.State.BLOCKED -> "blocked"
                    workInfo.state == androidx.work.WorkInfo.State.CANCELLED -> "cancelled"
                    else -> "unknown"
                }
            }
    }

    suspend fun getLocalHealth(): Health {
        val currentProcesses = emptyList<Process>()
        return Health(
            status = "operational",
            lastRun = java.time.Instant.now().toString(),
            sourcesOk = 1,
            sourcesFailed = 0,
            reviewRequired = currentProcesses.size
        )
    }

    suspend fun detail(id: String, force: Boolean = false) {
        val identity = dao.cached("identity:" + id).first()
        val activity = dao.cached("activity:" + id).first()
        if (force || identity == null || activity == null || System.currentTimeMillis() - activity.savedAt > 1_800_000) {
            val process = dao.observe().first().find { it.id == id } ?: return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val http = CnscHttpClient()
                try {
                    val url = process.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64"
                    val (parsed, activityParsed) = ProcessMicrositeReader.read(process, url,
                        { http.fetch(it).html }, java.time.Instant.now())
                    dao.cache(ContentCache("activity:" + id, gson.toJson(activityParsed), System.currentTimeMillis()))
                    dao.cache(ContentCache("activity_check:" + id,
                        gson.toJson(ActivityCheck(id, process.name, java.time.Instant.now().toString(), null)),
                        System.currentTimeMillis()))
                    dao.cache(ContentCache("identity:" + id, gson.toJson(parsed), System.currentTimeMillis()))
                } finally { http.close() }
            }
        }
        // Details come from the same verified local ingestion as alerts.
        // Opening a detail must never overwrite saved publications with empty data.
        if (dao.cached("detail:$id").first() == null) refresh()
    }

    suspend fun follow(id: String, enabled: Boolean) {
        if (enabled) {
            dao.follow(Following(id))
            CnscMonitoringWorker.triggerManualSync(context)
        } else dao.unfollow(id)
    }
}

class RadarApp : Application() {
    val repository by lazy {
        val database = LegacyRadarDatabase.getInstance(this)
        LocalRadarRepository(database.dao(), this)
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("monitor_schedule", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("monitoring_paused_v1", false)) {
            CnscMonitoringWorker.cancel(this)
        } else {
            CnscMonitoringWorker.schedule(this)
        }
    }
}
