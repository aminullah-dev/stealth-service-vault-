package com.kabulsignal.news.data.remote

import com.kabulsignal.news.BuildConfig
import com.kabulsignal.news.data.NewsRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Tiny manual dependency container. The app has a single backend and no auth,
 * so a full DI framework would be overkill — these singletons are created lazily
 * and shared process-wide.
 */
object ServiceLocator {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Some WordPress hosts (and CDNs like Cloudflare) reject the default
            // OkHttp user-agent, so present a conventional browser identity.
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android) KabulSignalApp/${BuildConfig.VERSION_NAME}",
                    )
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }

    private val api: WordPressApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.WP_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WordPressApi::class.java)
    }

    val repository: NewsRepository by lazy { NewsRepository(api) }
}
