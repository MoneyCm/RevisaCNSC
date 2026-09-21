package co.meritoradar.app

import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ActivityPublication(val title: String, val summary: String, val publishedAt: String,
    val sourceUrl: String)

data class ProcessActivity(val title: String, val summary: String, val publishedAt: String,
    val sourceUrl: String, val checkedAt: String, val noticesChecked: Int,
    val publications: List<ActivityPublication>? = null)

/** Reads only notice headings and dates; never persists applicant tables or identifiers. */
object ProcessActivityParser {
    const val MAX_PAGES = 3

    /** Commit only a successful bounded traversal. Failures and cancellation propagate. */
    suspend fun parseAll(process: Process, sourceUrl: String, fetch: suspend (String) -> String,
        now: Instant, maxPages: Int = MAX_PAGES, firstPageHtml: String? = null,
        pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }): ProcessActivity {
        var url = sourceUrl
        val collected = linkedMapOf<Pair<String, String>, ActivityPublication>()
        for (page in 1..maxPages) {
            if (page > 1) pause(2000)
            val html = if (page == 1 && firstPageHtml != null) firstPageHtml else fetch(url)
            collect(process, html, url, now, collected)
            val next = nextPage(process, html, url) ?: break
            url = next
        }
        if (collected.isEmpty()) throw ParseError("Sin avisos fechados reconocibles en micrositio")
        return summarize(now, collected.values.toList())
    }
    /** Pure parse of a single official microsite page; keeps the latest as summary fields. */
    fun parse(process: Process, html: String, sourceUrl: String, now: Instant): ProcessActivity {
        val collected = linkedMapOf<Pair<String, String>, ActivityPublication>()
        collect(process, html, sourceUrl, now, collected)
        if (collected.isEmpty()) throw ParseError("Sin avisos fechados reconocibles en micrositio")
        return summarize(now, collected.values.toList())
    }

    private fun collect(process: Process, html: String, sourceUrl: String, now: Instant,
        collected: MutableMap<Pair<String, String>, ActivityPublication>) {
        val source = sourceUrl.toHttpUrl()
        require(officialLink(sourceUrl) && source.queryParameter("field_tipo_de_contenido_convocat_target_id") == "64")
        require(source.toString().substringBefore('?') == process.officialUrl)
        val main = Jsoup.parse(html).selectFirst("main") ?: throw ParseError("Falta micrositio")
        if (!main.selectFirst("h1")?.text()?.trim().equals(process.name.trim(), true))
            throw ParseError("Identidad de convocatoria inesperada")
        main.select(".view-content .card").forEach { card ->
            val title = card.selectFirst("h5 button")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val date = Regex("(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}:\\d{2})")
                .find(card.selectFirst(".views-field-created")?.text().orEmpty()) ?: return@forEach
            val instant = runCatching {
                LocalDateTime.parse(date.groupValues[1] + " " + date.groupValues[2],
                    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT))
                    .atZone(ZoneId.of("America/Bogota")).toInstant()
            }.getOrNull() ?: return@forEach
            if (instant > now) return@forEach
            collected.putIfAbsent(title to instant.toString(), ActivityPublication(title, describe(title), instant.toString(), sourceUrl))
        }
    }

    private fun summarize(now: Instant, items: List<ActivityPublication>): ProcessActivity {
        val ordered = items.sortedByDescending { Instant.parse(it.publishedAt) }
        val latest = ordered.first()
        return ProcessActivity(latest.title, latest.summary, latest.publishedAt,
            latest.sourceUrl, now.toString(), ordered.size, ordered)
    }
    /** Drupal pager on the same microsite: official host, same path, avisos category filter. */
    fun nextPage(process: Process, html: String, sourceUrl: String): String? {
        val link = Jsoup.parse(html).selectFirst("main .pager__item--next a[href]") ?: return null
        val resolved = runCatching { sourceUrl.toHttpUrl().resolve(link.attr("href")) }
            .getOrNull() ?: return null
        val official = runCatching {
            resolved.scheme == "https" && resolved.host in setOf("www.cnsc.gov.co", "cnsc.gov.co") &&
                resolved.port == 443 && resolved.username.isEmpty() && resolved.password.isEmpty()
        }.getOrDefault(false)
        if (!official) return null
        val expectedPath = runCatching { process.officialUrl.toHttpUrl().encodedPath }.getOrNull() ?: return null
        if (resolved.encodedPath != expectedPath) return null
        if (resolved.queryParameter("field_tipo_de_contenido_convocat_target_id") != "64") return null
        return resolved.toString()
    }

    fun describe(title: String): String {
        val text = Normalizer.normalize(title.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        if ("certificado de discapacidad" in text) return when {
            "reclamacion" in text -> "Reclamaciones sobre certificado de discapacidad"
            "resultados" in text -> "Resultados de verificación del certificado de discapacidad"
            else -> "Aviso sobre certificado de discapacidad"
        }
        if ("requisitos minimos" in text || Regex("\\bvrm\\b").containsMatchIn(text)) {
            if (listOf("no ", "suspension", "aplazamiento", "posteriormente", "proximamente", "requisitos para")
                    .any { it in text }) return "Aviso sobre VRM; consultar alcance oficial"
            return when {
                "reclamacion" in text -> "Reclamaciones de verificación de requisitos mínimos"
                "resultados" in text -> "Resultados de verificación de requisitos mínimos anunciados"
                "inicio" in text -> "Inicio de verificación de requisitos mínimos anunciado"
                else -> "Aviso sobre verificación de requisitos mínimos"
            }
        }
        return "Aviso oficial del concurso"
    }
}