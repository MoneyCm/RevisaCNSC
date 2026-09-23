package co.meritoradar.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Funcionalidad de prueba exclusiva del paquete QA `co.meritoradar.app.qa` (variante
 * QA). La restricción es por applicationId en tiempo de ejecución y NO por
 * `BuildConfig.DEBUG`: la producción instalada también puede provenir de un build debug.
 *
 * Reglas:
 * - Solo programa/publica cuando el paquete en ejecución es el QA (comprobación en el
 *   planificador y de nuevo en el worker como defensa en profundidad).
 * - No crea eventos CNSC, no escribe en outbox, no toca Room, following, avisos,
 *   actividad ni caché; no usa ningún concurso real como contenido de prueba.
 * - La notificación abre simplemente Mérito Radar; no se simula ningún deep link.
 * - `ExistingWorkPolicy.KEEP` garantiza como máximo una prueba retardada pendiente.
 */
object QaDelayNotifier {
    const val QA_APPLICATION_ID = "co.meritoradar.app.qa"
    const val WORK_NAME = "QaDelayedTestNotificationOnly"
    const val DELAY_MILLIS = 60_000L

    /** Política de encolado único: si ya hay una prueba retardada pendiente no se duplica. */
    val enqueuePolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun isQaApplication(applicationId: String?): Boolean = applicationId == QA_APPLICATION_ID

    /** Trabajo único retardado ~1 minuto que publica la notificación TEST QA. */
    fun buildRequest(delayMillis: Long = DELAY_MILLIS): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<QaDelayedNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

    /**
     * Encola la prueba solo cuando `applicationId` es el de QA. Devuelve true si realizó
     * la acción; false en cualquier otro paquete (incluido producción). `enqueue` es la
     * operación de encolado provista por el llamador.
     */
    fun scheduleIfQa(applicationId: String?, enqueue: () -> Unit): Boolean {
        if (!isQaApplication(applicationId)) return false
        enqueue()
        return true
    }
}

/**
 * Worker que publica la notificación TEST QA. Verifica de nuevo el paquete QA antes de
 * publicar: aunque este worker se invocara desde el paquete principal no haría nada.
 * Devuelve success con `posted` para observación; no reintenta (no hay backoff) y no
 * toca datos de negocio.
 */
class QaDelayedNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!QaDelayNotifier.isQaApplication(applicationContext.packageName)) return Result.success()
        val posted = LocalNotifier(applicationContext).showQaTestNotification()
        return Result.success(Data.Builder().putBoolean("posted", posted).build())
    }
}