package it.federicorapetti.recalls.data.remote

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Desktop Chrome UA. salute.gov.it (Gcore bot shield) returns 403 without it. */
const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

class HttpStatusException(val code: Int) : IOException("HTTP $code")

fun buildHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()

suspend fun <T> OkHttpClient.execute(request: Request, block: (Response) -> T): T =
    withContext(Dispatchers.IO) {
        newCall(request).execute().use(block)
    }

suspend fun OkHttpClient.downloadTo(url: String, file: File) {
    val request = Request.Builder().url(url).build()
    execute(request) { response ->
        if (!response.isSuccessful) throw HttpStatusException(response.code)
        file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
    }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
