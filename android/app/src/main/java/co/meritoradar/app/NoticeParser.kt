package co.meritoradar.app

import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl

data class LocalNotice(val url: String, val processUrl: String?, val title: String,
    val publishedAt: String?, val paragraphs: List<String>, val documents: List<DocumentInfo>) {
    fun semanticKey(): String = hash(listOf(processUrl.orEmpty(), title, publishedAt.orEmpty(),
        paragraphs.sorted().joinToString("\n"), documents.sortedBy { it.url }.joinToString { it.url + " " + it.title }).joinToString("\n"))
}
fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

class NoticeParser(private val http: CnscHttpClient) {
    private fun clean(s: String) = s.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    fun index(html: String, url: String): Pair<List<String>, String?> {
        val doc = Jsoup.parse(html)
        if (doc.selectFirst("main h1")?.text()?.lowercase()?.contains("avisos informativos") != true)
            throw ParseError("Índice de avisos inesperado")
        val links = doc.select("main .view-content h2 a[href]").map { http.normalizeUrl(it.attr("href"), url) }.distinct()
        if (links.isEmpty() || links.any { !Regex("/node/\\d+").matches(it.toHttpUrl().encodedPath) })
            throw ParseError("Índice de avisos vacío o inesperado")
        val next = doc.selectFirst("main .pager__item--next a[href]")?.let {
            val resolved = url.toHttpUrl().resolve(it.attr("href")) ?: throw ParseError("Paginación inválida")
            val page = resolved.queryParameter("page")
            if (!http.validateOfficialUrl(resolved.toString()) || resolved.encodedPath != "/avisos-informativos" ||
                page == null || !page.matches(Regex("\\d+"))) throw ParseError("Paginación inválida")
            CnscSources.NOTICES + "?page=" + page
        }
        return links to next
    }
    fun parse(html: String, url: String): LocalNotice {
        val canonical = http.normalizeUrl(url)
        val doc = Jsoup.parse(html)
        val article = doc.selectFirst("main article[data-history-node-id]") ?: throw ParseError("Falta artículo")
        if (canonical.toHttpUrl().encodedPath != "/node/" + article.attr("data-history-node-id"))
            throw ParseError("Identidad del aviso inesperada")
        val title = doc.selectFirst("main h1")?.text()?.let(::clean)?.takeIf { it.isNotEmpty() }
            ?: throw ParseError("Falta título")
        val associations = article.select(".field--name-field-convocatoria-asociada a[href]").map {
            http.normalizeUrl(it.attr("href"), canonical).also { link ->
                if (!link.toHttpUrl().encodedPath.startsWith("/convocatorias/")) throw ParseError("Asociación inesperada")
            }
        }.distinct()
        val body = article.selectFirst(".field--name-body") ?: throw ParseError("Falta cuerpo")
        body.select("script,style,nav").remove()
        val paragraphs = body.select("p,li,tr").filter { element ->
            element.parents().none { it != body && it.tagName() in listOf("p", "li", "tr") }
        }.map { clean(it.text()) }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { listOf(clean(body.text())).filter { it.isNotEmpty() } }
        if (paragraphs.isEmpty()) throw ParseError("Aviso vacío")
        val date = Regex("(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}:\\d{2})")
            .find(article.selectFirst(".field--name-created")?.text().orEmpty())
        val published = date?.let { runCatching {
            LocalDateTime.parse(it.groupValues[1] + " " + it.groupValues[2],
                DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT))
                .atZone(ZoneId.of("America/Bogota")).toInstant().toString()
        }.getOrNull() }
        val documents = body.select("a[href]").mapNotNull {
            runCatching {
                val link = http.normalizeUrl(it.attr("href"), canonical)
                if (link.toHttpUrl().encodedPath.endsWith(".pdf", ignoreCase = true))
                    DocumentInfo(link, clean(it.text()).ifEmpty { "Documento oficial" }) else null
            }.getOrNull()
        }.distinctBy { it.url }.sortedBy { it.url }
        return LocalNotice(canonical, associations.singleOrNull(), title, published, paragraphs, documents)
    }
}
