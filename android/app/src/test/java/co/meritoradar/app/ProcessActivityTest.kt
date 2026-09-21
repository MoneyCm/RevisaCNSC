package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.Instant

class ProcessActivityTest {
    private val process = Process("p", name = "DIAN 2676", officialUrl = "https://www.cnsc.gov.co/convocatorias/dian-2676")
    private val url = process.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64"
    private val now = Instant.parse("2026-09-21T12:00:00Z")
    private fun card(title: String, date: String) =
        "<div class='card'><h5><button>" + title + "</button></h5><span class='views-field-created'>" + date + "</span></div>"
    private fun page(cards: String) = "<main><h1>DIAN 2676</h1><div class='view-content'>" + cards + "</div></main>"

    @Test fun latestOfficialDisabilityNoticeIsNotGeneralVrm() {
        // Sanitized heading/date observed on official DIAN microsite, 2026-09-20.
        val title = "LISTADO DE RESPUESTAS A RECLAMACIONES FRENTE A LOS RESULTADOS DE LA VERIFICACIÓN DEL CERTIFICADO DE DISCAPACIDAD."
        val result = ProcessActivityParser.parse(process, page(
            card(title, "Lun, 01/06/2026 - 12:42") +
            card("Ampliación cierre de inscripciones DIAN 2676", "06/02/2026 - 16:18")
        ), url, now)
        assertEquals(title, result.title)
        assertEquals("Reclamaciones sobre certificado de discapacidad", result.summary)
        assertEquals("2026-06-01T17:42:00Z", result.publishedAt)
        assertEquals(url, result.sourceUrl)
        assertEquals(2, result.noticesChecked)
    }

    @Test fun distinguishesVrmMilestonesWithoutClaimingCurrentStage() {
        assertEquals("Inicio de verificación de requisitos mínimos anunciado",
            ProcessActivityParser.describe("Inicio de Verificación de Requisitos Mínimos"))
        assertEquals("Resultados de verificación de requisitos mínimos anunciados",
            ProcessActivityParser.describe("Resultados de VRM"))
        assertEquals("Reclamaciones de verificación de requisitos mínimos",
            ProcessActivityParser.describe("Respuestas a reclamaciones de VRM"))
        assertEquals("Aviso sobre VRM; consultar alcance oficial",
            ProcessActivityParser.describe("Aplazamiento del inicio de VRM"))
        assertEquals("Aviso sobre verificación de requisitos mínimos",
            ProcessActivityParser.describe("Documentos para verificación de requisitos mínimos"))
    }

    @Test fun ignoresFutureDatesAndRejectsImpossibleDates() {
        val result = ProcessActivityParser.parse(process, page(
            card("Futuro", "01/01/2027 - 12:00") +
            card("Imposible", "31/02/2026 - 12:00") +
            card("Resultados de VRM", "20/09/2026 - 12:00")
        ), url, now)
        assertEquals("Resultados de VRM", result.title)
        assertEquals(1, result.noticesChecked)
    }

    @Test(expected = ParseError::class) fun wrongContestIsRejected() {
        ProcessActivityParser.parse(process, page(card("Aviso", "20/09/2026 - 12:00"))
            .replace("<h1>DIAN 2676</h1>", "<h1>DIAN 2022</h1>"), url, now)
    }

    @Test fun keepsOrderedHistoryWithSourceUrlAndNoRetroactiveAlerts() {
        val result = ProcessActivityParser.parse(process, page(
            card("Ampliación cierre de inscripciones DIAN 2676", "06/02/2026 - 16:18") +
            card("LISTADO DE RESPUESTAS A RECLAMACIONES", "01/06/2026 - 12:42") +
            card("Recuerdo. Cierre de inscripciones", "04/02/2026 - 18:31")
        ), url, now)
        assertEquals(3, result.noticesChecked)
        assertEquals("LISTADO DE RESPUESTAS A RECLAMACIONES", result.title) // newer first
        val ordered = result.publications.orEmpty().map { it.publishedAt }
        assertEquals(listOf("2026-06-01T17:42:00Z", "2026-02-06T21:18:00Z", "2026-02-04T23:31:00Z"), ordered)
        assertTrue(result.publications.orEmpty().all { it.sourceUrl == url })
        assertEquals("Aviso oficial del concurso", result.publications?.last()?.summary)
    }

    @Test fun nextPageRequiresSameMicrositeAndAvisoCategory() {
        val next = "<main><h1>DIAN 2676</h1><nav class='pager'><ul>" +
            "<li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        assertEquals(url + "&page=1", ProcessActivityParser.nextPage(process, next, url))
        val foreign = next.replace(url + "&page=1", "https://www.cnsc.gov.co/convocatorias/otro")
        assertNull(ProcessActivityParser.nextPage(process, foreign, url))
        val otherCategory = next.replace("&page=1", "&page=1#aviso").replace(url, url.replace("=64", "=65"))
        assertNull(ProcessActivityParser.nextPage(process, otherCategory, url))
        assertNull(ProcessActivityParser.nextPage(process, "<main><h1>DIAN 2676</h1></main>", url))
    }

    @Test fun parseAllFollowsBoundedPagesAndMergesHistory() = runBlocking {
        val page1 = "<main><h1>DIAN 2676</h1><div class='view-content'>" +
            card("Nuevo aviso reciente", "20/09/2026 - 12:00") +
"</div><nav class='pager'><ul><li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        val page2 = page(card("Resultados de VRM", "20/09/2025 - 12:00"))
        val requests = mutableListOf<String>()
        val result = ProcessActivityParser.parseAll(process, url,
            { u -> requests.add(u); if (u.endsWith("&page=1")) page2 else page1 }, now, pause = {})
        assertEquals(listOf(url, url + "&page=1"), requests)
        assertEquals(2, result.noticesChecked)
        assertEquals("Nuevo aviso reciente", result.title)
        assertEquals("Resultados de VRM", result.publications?.last()?.title)
assertEquals(url + "&page=1", result.publications?.last()?.sourceUrl)
    }

    @Test fun parseAllRejectsLatePageFailure() = runBlocking {
        var calls = 0
        val page1 = "<main><h1>DIAN 2676</h1><div class='view-content'>" +
            card("Aviso", "20/09/2026 - 12:00") +
            "</div><nav class='pager'><ul><li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        try {
            ProcessActivityParser.parseAll(process, url,
                {
                    calls++
                    when (calls) {
                        1 -> page1
                        2 -> throw java.io.IOException("caída de página")
                        else -> throw IllegalStateException("no debe seguir")
                    }
                }, now, maxPages = 2, pause = {})
            fail("Un fallo en una página posterior debe propagarse, no reducirse a éxito parcial")
        } catch (expected: java.io.IOException) {
            assertEquals(2, calls)
        }
    }

    @Test(expected = ParseError::class) fun unknownStructureIsNotEmptySuccess() {
        ProcessActivityParser.parse(process, page("<p>Sin estructura reconocida</p>"), url, now)
    }

    @Test(expected = IllegalArgumentException::class) fun nonNoticeCategoryIsRejected() {
        ProcessActivityParser.parse(process, page(card("Aviso", "20/09/2026 - 12:00")),
url.replace("=64", "=65"), now)
    }

    private fun paged(title: String, next: String? = null): String =
        page(card(title, "20/09/2026 - 12:00")).replace("</main>",
            (next?.let { "<li class='pager__item--next'><a href='$it'>Next</a></li>" } ?: "") + "</main>")

    @Test fun partialRefreshPreservesCompleteHistoryAndReportsFailure() = runBlocking {
        val previous = ProcessActivityParser.parse(process,
            page(card("Uno", "20/09/2026 - 12:00") + card("Dos", "19/09/2026 - 12:00") +
                card("Tres", "18/09/2026 - 12:00")), url, now)
        var stored = previous
        var error: String? = null
        ActivityRefresh.run(listOf(process),
            read = { ProcessActivityParser.parseAll(it, url,
                { u -> if (u == url) paged("Nuevo", url + "&page=1") else throw java.io.IOException("offline") }, now, pause = {}) },
            save = { _, value -> stored = value }, report = { _, value -> error = value })
        assertEquals(previous, stored)
        assertNotNull(error)
    }

    @Test fun paginationCancellationPropagatesWithoutSavingOrReportingSuccess() = runBlocking {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        try {
            ActivityRefresh.run(listOf(process),
                read = { ProcessActivityParser.parseAll(it, url,
                    { u -> if (u == url) paged("Uno", url + "&page=1") else throw cancellation }, now, pause = {}) },
                save = { _, _ -> fail("Must not replace history") },
                report = { _, _ -> fail("Cancellation is not a source result") })
            fail("Must propagate cancellation")
        } catch (actual: kotlinx.coroutines.CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun threePagesKeepTheirEvidenceUrlsAndBoundRequests() = runBlocking {
        val requests = mutableListOf<String>()
        val pages = listOf(url, url + "&page=1", url + "&page=2")
        val result = ProcessActivityParser.parseAll(process, url, { u ->
            requests.add(u)
            val index = pages.indexOf(u)
            paged("Aviso $index", url + "&page=" + (index + 1))
        }, now, pause = {})
        assertEquals(pages, requests)
        assertEquals(pages, result.publications!!.map { it.sourceUrl })
    }

    @Test fun manualQueryDownloadsFirstPageOnceAndSharesIt() = runBlocking {
        val requests = mutableListOf<String>()
        val pauses = mutableListOf<Long>()
        val (identity, activity) = ProcessMicrositeReader.read(process, url, { u ->
            requests.add(u)
            if (u == url) paged("Manual", url + "&page=1") else paged("Histórico")
        }, now, pause = { pauses.add(it) })
        assertEquals(listOf(url, url + "&page=1"), requests)
        assertEquals(1, requests.count { it == url }) // primera página descargada una sola vez
        assertEquals(listOf(2000L), pauses) // pausa prudente entre solicitudes sucesivas
        assertEquals(url, identity.sourceUrl)
        assertEquals(listOf(url, url + "&page=1"), activity.publications!!.map { it.sourceUrl })
        assertEquals("Manual", activity.title)
        assertEquals("Histórico", activity.publications?.last()?.title)
    }

    @Test fun pauseSeparatesSuccessiveRequestsToTheMicrosite() = runBlocking {
        val requests = mutableListOf<String>()
        val pauses = mutableListOf<Long>()
        val pages = listOf(url, url + "&page=1", url + "&page=2")
        ProcessActivityParser.parseAll(process, url, { u ->
            requests.add(u)
            val index = pages.indexOf(u)
            paged("Aviso $index", url + "&page=" + (index + 1))
        }, now, pause = { pauses.add(it) })
        assertEquals(listOf(2000L, 2000L), pauses)
        assertEquals(3, requests.size)
    }
}
