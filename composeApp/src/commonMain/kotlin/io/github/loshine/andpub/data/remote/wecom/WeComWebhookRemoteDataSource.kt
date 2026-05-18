package io.github.loshine.andpub.data.remote.wecom

import io.github.loshine.andpub.data.remote.decodeResponse
import io.github.loshine.andpub.data.remote.responseJson
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.koin.core.annotation.Single

@Single
class WeComWebhookRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun sendMarkdown(
        webhookUrl: String,
        content: String,
    ): WeComWebhookResult {
        val response = client.post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(responseJson.encodeToString(WeComWebhookMessage.markdown(content)))
        }.decodeResponse<WeComWebhookResponse>("企业微信 WebHook")
        response.requireSuccess()
        return WeComWebhookResult(response.errorCode, response.errorMessage)
    }
}

data class WeComWebhookResult(
    val errorCode: Int?,
    val errorMessage: String?,
)

@Serializable
private data class WeComWebhookMessage(
    val msgtype: String,
    val markdown: WeComMarkdown,
) {
    companion object {
        fun markdown(content: String): WeComWebhookMessage =
            WeComWebhookMessage(
                msgtype = "markdown",
                markdown = WeComMarkdown(content),
            )
    }
}

@Serializable
private data class WeComMarkdown(
    val content: String,
)

@Serializable
private data class WeComWebhookResponse(
    val errcode: Int? = null,
    val errmsg: String? = null,
) {
    val errorCode: Int?
        get() = errcode

    val errorMessage: String?
        get() = errmsg

    fun requireSuccess() {
        val code = errcode ?: return
        require(code == 0) {
            "企业微信 WebHook errcode=$code: ${errmsg.orEmpty()}"
        }
    }
}
