package co.meritoradar.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ActivityRefreshTest {
    private fun process(id: String) = Process(id, name = id, officialUrl = "https://www.cnsc.gov.co/convocatorias/" + id)
    private val activity = ProcessActivity("Aviso", "Aviso oficial", "2026-09-21T00:00:00Z", "https://www.cnsc.gov.co", "2026-09-21T00:00:00Z", 1)

    @Test fun failedSourcePreservesOldDataAndNextSourceIsSaved() = runBlocking {
        val stored = mutableMapOf("a" to activity)
        val reports = mutableMapOf<String, String?>()
        ActivityRefresh.run(listOf(process("a"), process("b")),
            read = { if (it.id == "a") throw IOException() else activity.copy(title = "Nuevo") },
            save = { p, a -> stored[p.id] = a }, report = { p, e -> reports[p.id] = e })
        assertEquals("Aviso", stored["a"]?.title)
        assertEquals("Nuevo", stored["b"]?.title)
        assertNotNull(reports["a"])
        assertTrue(reports.containsKey("b"))
        assertNull(reports["b"])
    }

    @Test fun recoveryClearsFailure() = runBlocking {
        var error: String? = "Fallo previo"
        ActivityRefresh.run(listOf(process("a")), { activity }, { _, _ -> }, { _, e -> error = e })
        assertNull(error)
    }

    @Test(expected = CancellationException::class) fun cancellationIsNotSourceFailure() = runBlocking {
        ActivityRefresh.run(listOf(process("a")), { throw CancellationException() },
            { _, _ -> fail("No guardar") }, { _, _ -> fail("No registrar fallo de fuente") })
    }

    @Test(expected = IOException::class) fun databaseFailureIsNotSwallowed() = runBlocking {
        ActivityRefresh.run(listOf(process("a")), { activity }, { _, _ -> throw IOException() }, { _, _ -> })
    }

    @Test fun recentFailedAttemptDoesNotStarveOtherFollowedSources() {
        val now = 10_000_000L
        val processes = listOf("a", "b", "c", "d", "e").map(::process)
        val selected = ActivityRefresh.candidates(processes, setOf("a", "b", "c", "d"), mapOf("a" to now), now)
        assertEquals(listOf("b", "c", "d"), selected.map { it.id })
        assertEquals(3, ActivityRefresh.candidates(processes, processes.map { it.id }.toSet(), emptyMap(), now).size)
    }
}
