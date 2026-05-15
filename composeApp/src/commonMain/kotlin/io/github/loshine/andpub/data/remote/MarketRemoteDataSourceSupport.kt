package io.github.loshine.andpub.data.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal val responseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal inline fun <reified T> decodeResponse(
    marketName: String,
    text: String,
): T {
    val trimmed = text.trimStart()
    require(trimmed.isNotBlank()) {
        "$marketName 返回了空响应"
    }
    require(!trimmed.startsWith("<")) {
        "$marketName 返回了非 JSON 响应：${text.take(300)}"
    }
    return try {
        responseJson.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        error("$marketName 响应解析失败：${e.message}")
    } catch (e: IllegalArgumentException) {
        error("$marketName 响应解析失败：${e.message}")
    }
}

internal suspend inline fun <reified T> HttpResponse.decodeResponse(marketName: String): T {
    val text = bodyAsText()
    val trimmed = text.trimStart()
    require(trimmed.isNotBlank()) {
        "$marketName 返回了空响应，HTTP ${status.value} ${status.description}"
    }
    require(!trimmed.startsWith("<")) {
        "$marketName 返回了非 JSON 响应，HTTP ${status.value} ${status.description}：${text.take(300)}"
    }
    return decodeResponse(marketName, text)
}

internal fun Map<String, String>.signPlain(): String =
    filterValues { it.isNotEmpty() }
        .entries
        .sortedBy { it.key }
        .joinToString("&") { "${it.key}=${it.value}" }

internal fun currentSeconds(): String =
    (Clock.System.now().toEpochMilliseconds() / 1000).toString()

internal fun currentMillis(): String =
    Clock.System.now().toEpochMilliseconds().toString()
