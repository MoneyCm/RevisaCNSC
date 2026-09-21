package co.meritoradar.app

import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ProcessActivity(val title: String, val summary: String, val publishedAt: String,
    val sourceUrl: String, val checkedAt: String, val noticesChecked: Int)

/** Reads only notice headings and dates; never persists applicant tables or identifiers. */
object ProcessActivityParser {
    fun parse(process: Process, html: String, sourceUrl: String, now: Instant): ProcessActivity {
        val source = sourceUrl.toHttpUrl()
        require(officialLink(sourceUrl) && source.queryParameter("field_tipo_de_contenido_convocat_target_id") == "64")
        require(source.toString().substringBefore('?') == process.officialUrl)
        val main = Jsoup.parse(html).selectFirst("main") ?: throw ParseError("Falta micrositio")
        if (!main.selectFirst("h1")?.text()?.trim().equals(process.name.trim(), true))
            throw ParseError("Identidad de convocatoria inesperada")
        val notices = main.select(".view-content .card").mapNotNull { card ->
            val title = card.selectFirst("h5 button")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val date = Regex("(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}:\\d{2})")
                .find(card.selectFirst(".views-field-created")?.text().orEmpty()) ?: return@mapNotNull null
            val instant = runCatching {
                LocalDateTime.parse(date.groupValues[1] + " " + date.groupValues[2],
                    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT))
                    .atZone(ZoneId.of("America/Bogota")).toInstant()
            }.getOrNull() ?: return@mapNotNull null
            if (instant > now) null else title to instant
        }
        val latest = notices.maxByOrNull { it.second } ?: throw ParseError("Sin avisos fechados reconocibles en micrositio")
        return ProcessActivity(latest.first, describe(latest.first), latest.second.toString(),
            sourceUrl, now.toString(), notices.size)
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
