package io.github.loshine.andpub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class CapturingHttpClient(
    private val responder: suspend (HttpRequestData) -> String,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client = HttpClient(
        MockEngine { request ->
            requests += request
            respond(
                content = responder(request),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    )
}

internal suspend fun HttpRequestData.bodyText(): String =
    when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        is TextContent -> content.text
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readText()
        is OutgoingContent.WriteChannelContent -> coroutineScope {
            val channel = ByteChannel()
            launch {
                content.writeTo(channel)
                channel.close()
            }
            channel.readRemaining().readText()
        }
        else -> ""
    }
