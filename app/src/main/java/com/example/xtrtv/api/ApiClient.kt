package com.example.xtrtv.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val USER_AGENT = "TiviMate"
    
    private var currentBaseUrl: String? = null
    private var currentService: XCApiService? = null
    private var githubService: GithubService? = null

    fun createService(baseUrl: String): XCApiService {
        if (currentBaseUrl == baseUrl && currentService != null) {
            return currentService!!
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

        val service = Retrofit.Builder()
            .baseUrl(baseUrl.ensureHttpPrefix())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XCApiService::class.java)
            
        currentBaseUrl = baseUrl
        currentService = service
        return service
    }

    private fun String.ensureHttpPrefix(): String {
        return if (!startsWith("http://") && !startsWith("https://")) {
            "http://$this"
        } else {
            this
        } + if (!endsWith("/")) "/" else ""
    }

    fun createGithubService(): GithubService {
        githubService?.let { return it }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "XTRTv-App")
                    .build()
                chain.proceed(request)
            }
            .build()

        val service = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubService::class.java)
            
        githubService = service
        return service
    }
}
