package it.federicorapetti.recalls.data.remote.safetygate

import it.federicorapetti.recalls.data.remote.AppJson
import it.federicorapetti.recalls.data.remote.HttpStatusException
import it.federicorapetti.recalls.data.remote.execute
import java.util.Locale
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SafetyGateApi(private val client: OkHttpClient) {

    suspend fun search(years: List<Int>, publishedFromMs: Long, page: Int, pageSize: Int, lang: String): SgPage {
        val body = buildJsonObject {
            putJsonObject("criteria") {
                putJsonArray("year") { years.forEach { add(it) } }
                // Epoch millis as a JSON string (inclusive); a JSON number makes the endpoint return 405.
                put("publicationDateFrom", publishedFromMs.toString())
            }
            put("searchCriteriaForNotification", false)
            put("isLaunched", true)
            put("isLaunchSearch", true)
            putJsonObject("pagination") {
                put("sortField", "PUBLICATION_DATE")
                put("sortOrder", "DESC")
                put("totalElements", 0)
                put("numberElements", pageSize)
                put("page", page)
            }
            put("fullTextSearch", "")
            put("language", lang)
            put("displayDefaultResults", false)
            put("isForMostRecent", false)
        }
        val request = Request.Builder()
            .url(SEARCH_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            AppJson.decodeFromStream(response.body.byteStream())
        }
    }

    suspend fun detail(id: Long, lang: String): SgDetail {
        val request = Request.Builder()
            .url("$NOTIFICATION_URL/$id?language=$lang")
            .get()
            .build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            AppJson.decodeFromStream(response.body.byteStream())
        }
    }

    suspend fun weeklyReportList(): List<SgWeeklyReportRef> {
        val request = Request.Builder().url(WEEKLY_REPORT_LIST_URL).get().build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            SafetyGateWeeklyReportParser.parseList(response.body.byteStream())
        }
    }

    suspend fun weeklyReportBarcodes(url: String): Map<String, String> {
        val request = Request.Builder().url(url).get().build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            SafetyGateWeeklyReportParser.parseBarcodes(response.body.byteStream())
        }
    }

    companion object {
        private const val BASE_URL = "https://ec.europa.eu/safety-gate-alerts/public/api"
        private const val SEARCH_URL = "$BASE_URL/search"
        private const val NOTIFICATION_URL = "$BASE_URL/notification"
        private const val WEEKLY_REPORT_LIST_URL =
            "https://ec.europa.eu/safety-gate-alerts/api/download/weeklyReport/list/xml/en"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun thumbnailUrl(photoId: Long): String = "$NOTIFICATION_URL/thumbnail/$photoId"
        fun imageUrl(photoId: Long): String = "$NOTIFICATION_URL/image/$photoId"
        fun webUrl(id: Long, lang: String): String =
            "https://ec.europa.eu/safety-gate-alerts/screen/webReport/alertDetail/$id?lang=$lang"

        fun contentLanguage(): String =
            if (Locale.getDefault().language == "it") "it" else "en"
    }
}
