package com.example.receiptapp.di

import android.content.Context
import com.example.receiptapp.BuildConfig
import com.example.receiptapp.data.local.AppDatabase
import com.example.receiptapp.data.local.ReceiptDao
import com.example.receiptapp.data.remote.AiProxyApi
import com.example.receiptapp.data.remote.ClaudeReceiptParser
import com.example.receiptapp.data.remote.ReceiptAiParser
import com.example.receiptapp.security.DbPassphraseProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DbPassphraseProvider
    ): AppDatabase = AppDatabase.build(context, passphraseProvider.getOrCreatePassphrase())

    @Provides
    fun provideReceiptDao(db: AppDatabase): ReceiptDao = db.receiptDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Bewusst BASIC statt BODY im Release, um keine Belegdaten in Logcat zu schreiben
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.AI_PROXY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAiProxyApi(retrofit: Retrofit): AiProxyApi = retrofit.create(AiProxyApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds
    abstract fun bindReceiptAiParser(impl: ClaudeReceiptParser): ReceiptAiParser
}
