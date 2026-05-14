package io.github.loshine.andpub.di

import io.github.loshine.andpub.network.httpClient
import io.ktor.client.HttpClient
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [NetworkModule::class])
class AppModule

@Module
class NetworkModule {
    @Single
    fun httpClient(): HttpClient = httpClient()
}

@KoinApplication
class AndpubKoinApplication
