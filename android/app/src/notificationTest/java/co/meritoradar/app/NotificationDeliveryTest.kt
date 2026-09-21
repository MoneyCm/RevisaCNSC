package co.meritoradar.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Transport tests only. Never inserts synthetic CNSC data into any database. */
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifier = LocalNotifier(context)
    private val processId = "00000000-0000-0000-0000-000000000001"

    @Before fun prepare() {
        check(context.packageName.endsWith(".qa")) { "Must run in isolated QA package" }
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant " + context.packageName + " android.permission.POST_NOTIFICATIONS"
            ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
        LocalNotifier.createNotificationChannels(context)
        manager.cancelAll()
        assertTrue(notifier.hasNotificationPermission())
    }

    @After fun clean() {
        if (context.packageName.endsWith(".qa")) manager.cancelAll()
    }

    private fun post(id: String) = notifier.showEventNotification(
        id, processId, "PRUEBA QA", "QA_ONLY",
        "PRUEBA QA - no es aviso CNSC", "Comprobación aislada de transporte", "INFO"
    )

    // Android/OEM may add an automatic group summary; count the actual event tags.
    private fun eventNotifications() = manager.activeNotifications.filter {
        it.tag in setOf("qa-repeat", "Aa", "BB", "qa-open") &&
            it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
    }

    private fun awaitCount(expected: Int) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (eventNotifications().size != expected &&
            android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertEquals(expected, eventNotifications().size)
    }

    @Test fun repeatedEventKeepsOneNotification() {
        post("qa-repeat")
        awaitCount(1)
        post("qa-repeat")
        awaitCount(1)
        assertEquals("qa-repeat", eventNotifications().single().tag)
    }

    @Test fun collidingHashesKeepSeparateNotificationsAndPendingIntents() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        post("Aa")
        post("BB")
        awaitCount(2)
        val notifications = eventNotifications()
        assertEquals(setOf("Aa", "BB"), notifications.map { it.tag }.toSet())
        assertNotEquals(notifications[0].notification.contentIntent,
            notifications[1].notification.contentIntent)
    }

    @Test fun notificationPendingIntentOpensExpectedProcessAndEvent() {
        post("qa-open")
        awaitCount(1)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            eventNotifications().single().notification.contentIntent.send()
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000)
            assertNotNull("Notification must open MainActivity", activity)
            assertEquals(processId, processLink(activity.intent.dataString))
            assertEquals("qa-open", activity.intent.getStringExtra("eventId"))
            assertEquals("qa-open", activity.intent.data?.getQueryParameter("eventId"))
            instrumentation.runOnMainSync { activity.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
