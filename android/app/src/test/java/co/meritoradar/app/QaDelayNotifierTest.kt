package co.meritoradar.app

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QaDelayNotifierTest {

    @Test
    fun qaGateRejectsProductionPackage() {
        assertFalse(QaDelayNotifier.isQaApplication("co.meritoradar.app"))
    }

    @Test
    fun qaGateRejectsOtherPackagesAndNull() {
        assertFalse(QaDelayNotifier.isQaApplication("co.meritoradar.app.foo"))
        assertFalse(QaDelayNotifier.isQaApplication("com.other.app.qa"))
        assertFalse(QaDelayNotifier.isQaApplication(null))
    }

    @Test
    fun qaGateAcceptsQaPackage() {
        assertTrue(QaDelayNotifier.isQaApplication("co.meritoradar.app.qa"))
    }

    @Test
    fun qaWorkNameIsExclusive() {
        assertNotEquals(QaDelayNotifier.WORK_NAME, CnscMonitoringWorker.WORK_NAME)
        assertTrue(QaDelayNotifier.WORK_NAME.startsWith("Qa"))
        assertFalse(QaDelayNotifier.WORK_NAME.contains(CnscMonitoringWorker.WORK_NAME))
    }

    @Test
    fun scheduleDoesNothingOutsideQa() {
        var enqueued = false
        assertFalse(QaDelayNotifier.scheduleIfQa("co.meritoradar.app") { enqueued = true })
        assertFalse(enqueued)
    }

    @Test
    fun schedulePerformsEnqueueOnlyForQaPackage() {
        var enqueued = false
        assertTrue(QaDelayNotifier.scheduleIfQa("co.meritoradar.app.qa") { enqueued = true })
        assertTrue(enqueued)
    }

    @Test
    fun enqueuePolicyIsKeepToAvoidDuplicates() {
        assertEquals(ExistingWorkPolicy.KEEP, QaDelayNotifier.enqueuePolicy)
    }

    @Test
    fun delayedRequestIsOneTimeWithOneMinuteDelay() {
        val request = QaDelayNotifier.buildRequest()
        assertEquals(60_000L, request.workSpec.initialDelay)
    }

    @Test
    fun customDelayIsRespected() {
        assertEquals(5_000L, QaDelayNotifier.buildRequest(5_000L).workSpec.initialDelay)
    }

    @Test
    fun qaPlannerApiDoesNotExposeBusinessDataMutations() {
        val exposesBusinessData = QaDelayNotifier::class.java.methods.any { method ->
            method.parameterTypes.any { type ->
                type.name.contains("Dao") || type.name.contains("Database") ||
                    type.name.contains("Repository") || type.name == "androidx.work.WorkManager"
            }
        }
        assertFalse("QaDelayNotifier must not accept DAO/Database/Repository/WorkManager", exposesBusinessData)
    }
}