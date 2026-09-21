package co.meritoradar.app

import org.jsoup.Jsoup

data class ProcessIdentity(val year: Int?, val sourceUrl: String, val checkedAt: String)

object ProcessIdentityParser {
    fun namedYear(name: String): Int? = Regex("\\b(?:19|20)\\d{2}\\b").findAll(name)
        .map { it.value.toInt() }.toSet().singleOrNull()

    fun parse(process: Process, html: String, sourceUrl: String, checkedAt: String): ProcessIdentity {
        val main = Jsoup.parse(html).selectFirst("main") ?: throw ParseError("Falta contenido de convocatoria")
        val heading = main.selectFirst("h1")?.text()?.trim()
        if (!heading.equals(process.name.trim(), ignoreCase = true)) throw ParseError("Identidad de convocatoria inesperada")
        val pattern = Regex(Regex.escape(process.name.trim()) + "\\s+de\\s+((?:19|20)\\d{2})\\b", RegexOption.IGNORE_CASE)
        val years = pattern.findAll(main.text()).map { it.groupValues[1].toInt() }.toSet()
        val year = if (years.size > 1) null else years.singleOrNull() ?: namedYear(process.name)
        return ProcessIdentity(year, sourceUrl, checkedAt)
    }
}

fun processYearLabel(process: Process, identity: ProcessIdentity?): String {
    val year = identity?.year ?: if (identity == null) ProcessIdentityParser.namedYear(process.name) else null
    return year?.let { "Año citado de la convocatoria: $it" } ?: "Año de convocatoria por verificar"
}
