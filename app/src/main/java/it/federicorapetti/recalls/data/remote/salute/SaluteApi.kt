package it.federicorapetti.recalls.data.remote.salute

import it.federicorapetti.recalls.data.remote.AppJson
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.execute
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface OperatorFetch {
    data object NotModified : OperatorFetch
    data class Data(val nodes: List<SaluteOperatorNode>, val etag: String?) : OperatorFetch
}

class SaluteApi(private val client: OkHttpClient) {

    suspend fun fetchOperatorRecalls(etag: String?): OperatorFetch {
        val requestBuilder = Request.Builder().url(OPERATOR_URL)
        if (etag != null) requestBuilder.header("If-None-Match", etag)
        return client.execute(requestBuilder.build()) { response ->
            if (response.code == 304) return@execute OperatorFetch.NotModified
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val page: SalutePageData = AppJson.decodeFromStream(response.body.byteStream())
            OperatorFetch.Data(
                nodes = page.result.data.extSicurezzaAlimentare.nodes,
                etag = response.header("ETag")
            )
        }
    }

    suspend fun fetchMinistryRss(): List<MinistryRssItem> {
        val request = Request.Builder().url(MINISTRY_RSS_URL).get().build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            MinistryRssParser.parse(response.body.byteStream())
        }
    }

    suspend fun fetchMinistryDetail(slug: String): MinistryPageData {
        val request = Request.Builder()
            .url("$MINISTRY_PAGE_DATA_URL/$slug/page-data.json")
            .get()
            .build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            AppJson.decodeFromStream(response.body.byteStream())
        }
    }

    companion object {
        private const val BASE = "https://www.salute.gov.it/new"
        private const val OPERATOR_URL =
            "$BASE/page-data/it/avvisi/avvisi-e-richiami-di-prodotti-alimentari/page-data.json"
        private const val MINISTRY_RSS_URL = "$BASE/rss/RSS_avvisi_sicurezza_alimentare.xml"
        private const val MINISTRY_PAGE_DATA_URL = "$BASE/page-data/it/avvisi-sicurezza-alimentare"
    }
}
