package com.example.xtrtv.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val USER_AGENT = "TiviMate"

    fun createService(baseUrl: String): XCApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureHttpPrefix())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XCApiService::class.java)
    }

    private fun String.ensureHttpPrefix(): String {
        return if (!startsWith("http://") && !startsWith("https://")) {
            "http://$this"
        } else {
            this
        } + if (!endsWith("/")) "/" else ""
    }
}
