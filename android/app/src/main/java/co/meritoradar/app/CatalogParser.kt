package co.meritoradar.app

import com.google.gson.Gson
import org.jsoup.Jsoup
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl

class ParseError(message: String) : Exception(message)

data class CatalogEntry(
    val name: String,
    val officialUrl: String,
    val slug: String
)

class CatalogParser(private val httpClient: CnscHttpClient) {

    companion object {
        private val gson = Gson()
    }

    /**
     * Parse CNSC catalog HTML and extract process entries
     */
    fun parseCatalog(html: String): List<CatalogEntry> {
        val doc = Jsoup.parse(html)

        // Validate heading
        val heading = doc.selectFirst("main h1")
            ?: throw ParseError("Missing catalog heading")
        val headingText = heading.text().lowercase()
        if ("desarrollo" !in headingText) {
            throw ParseError("Invalid catalog heading: $headingText")
        }

        // Extract process links
        val entries = mutableMapOf<String, CatalogEntry>()
        for (anchor in doc.select("main .views-field-name a[href]")) {
            val href = anchor.attr("href")
            val url = try {
                httpClient.normalizeUrl(href)
            } catch (e: IllegalArgumentException) {
                throw ParseError("Invalid catalog link: $href")
            }

            // Validate URL path
            val path = try {
                val parsed = url.toHttpUrl()
                parsed.encodedPath
            } catch (e: Exception) {
                ""
            }
            if (!path.startsWith("/convocatorias/")) {
                throw ParseError("Unexpected catalog link path: $path")
            }

            val name = anchor.text().trim().split(" ").joinToString(" ")
            if (name.isEmpty()) {
                throw ParseError("Process without name")
            }

            val slug = url.substringAfterLast("/")
            entries[url] = CatalogEntry(name, url, slug)
        }

        if (entries.isEmpty()) {
            throw ParseError("Catalog empty or structure changed")
        }

        // Check for pagination
        if (doc.selectFirst("main .pager__item--next a") != null) {
            throw ParseError("Catalog paginated: expand collector")
        }

        return entries.values.sortedBy { it.officialUrl }
    }

    /**
     * Generate SHA-256 hash of canonical content
     */
    fun digest(content: Any): String {
        val json = when (content) {
            is String -> content
            else -> gson.toJson(content)
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
