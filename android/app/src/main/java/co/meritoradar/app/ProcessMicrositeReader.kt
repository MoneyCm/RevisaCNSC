package co.meritoradar.app

import java.time.Instant

/** Shares the first response between identity and history; nothing is saved on partial failure. */
object ProcessMicrositeReader {
    suspend fun read(process: Process, sourceUrl: String, fetch: suspend (String) -> String,
        now: Instant, pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
    ): Pair<ProcessIdentity, ProcessActivity> {
        val firstPage = fetch(sourceUrl)
        val identity = ProcessIdentityParser.parse(process, firstPage, sourceUrl, now.toString())
        val activity = ProcessActivityParser.parseAll(process, sourceUrl, fetch, now,
            firstPageHtml = firstPage, pause = pause)
        return identity to activity
    }
}