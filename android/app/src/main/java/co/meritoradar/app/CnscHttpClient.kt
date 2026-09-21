package co.meritoradar.app

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl

// Constantes de fuentes CNSC
object CnscSources {
    const val CATALOG = "https://www.cnsc.gov.co/convocatorias/en-desarrollo"
    const val NOTICES = "https://www.cnsc.gov.co/avisos-informativos"
    const val PROXIMATE = "https://www.cnsc.gov.co/convocatorias/proximas-convocatorias"
    const val TERRITORIAL_12 = "https://www.cnsc.gov.co/convocatorias/territorial-12"
}

class CnscHttpClient {
    private val client: OkHttpClient

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val trustManager = CnscTrustManager()
        val tls = javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Merito Radar/0.1.0 (+https://github.com/meritoradar)")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-CO,es;q=0.9")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    data class FetchResult(
        val statusCode: Int,
        val headers: Map<String, String>,
        val html: String,
        val etag: String?,
        val lastModified: String?
    )

    /**
     * Fetch HTML from CNSC source with conditional request support
     */
    suspend fun fetch(url: String, conditional: Map<String, String> = emptyMap()): FetchResult {
        var current = url
        repeat(6) {
            require(validateOfficialUrl(current)) { "URL not from official CNSC domain" }
            val builder = Request.Builder().url(current)
            conditional["If-None-Match"]?.let { builder.header("If-None-Match", it) }
            conditional["If-Modified-Since"]?.let { builder.header("If-Modified-Since", it) }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    current = response.header("Location")?.let { current.toHttpUrl().resolve(it)?.toString() }
                        ?: throw IOException("Invalid redirect")
                } else {
                    if (response.code != 200 && response.code != 304) throw IOException("HTTP " + response.code)
                    val html = if (response.code == 304) "" else {
                        val body = response.body ?: throw IOException("Empty response body")
                        val source = body.source()
                        if (source.request(4L * 1024 * 1024 + 1)) throw IOException("Response too large")
                        source.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                    }
                    return FetchResult(response.code, response.headers.toMap(), html,
                        response.header("etag"), response.header("last-modified"))
                }
            }
        }
        throw IOException("Too many redirects")
    }

    /**
     * Validate URL is from official CNSC domain
     */
    fun validateOfficialUrl(url: String): Boolean {
        return try {
            val parsed = url.toHttpUrl()
            parsed.scheme == "https" &&
            (parsed.host == "www.cnsc.gov.co" || parsed.host == "cnsc.gov.co") &&
            parsed.port == 443 && parsed.username.isEmpty() && parsed.password.isEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalize URL to official CNSC format
     */
    fun normalizeUrl(url: String, base: String = CnscSources.CATALOG): String {
        val resolved = base.toHttpUrl().resolve(url) ?: throw IllegalArgumentException("Cannot resolve URL")
        if (!validateOfficialUrl(resolved.toString())) {
            throw IllegalArgumentException("URL not from official CNSC domain: $url")
        }

        return resolved.newBuilder()
            .host("www.cnsc.gov.co")
            .port(443)
            .query(null)
            .fragment(null)
            .encodedPath(resolved.encodedPath.removeSuffix("/"))
            .build()
            .toString()
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        client.cache?.close()
    }
}
