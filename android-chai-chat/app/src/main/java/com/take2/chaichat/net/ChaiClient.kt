package com.take2.chaichat.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Sends a conversation to Chai and returns the reply.
 *
 * Plain HttpURLConnection rather than a client library. One request, one
 * response, no streaming, so an HTTP stack dependency would buy nothing.
 */
class ChaiClient(
    private val baseUrl: String = ChaiApi.DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000
) {

    sealed interface Outcome {
        data class Ok(val text: String) : Outcome

        /**
         * [message] is written to be shown to a person. [detail] carries the
         * technical specifics for the error card, so a wrong field name or an
         * unexpected payload is visible rather than silently swallowed.
         */
        data class Failed(val message: String, val detail: String? = null) : Outcome
    }

    suspend fun send(
        turns: List<ChaiApi.Turn>,
        apiKey: String,
        model: String = ChaiApi.DEFAULT_MODEL
    ): Outcome = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            return@withContext Outcome.Failed(
                "No API key set. Open Settings and paste your Chai API key."
            )
        }

        val body = ChaiApi.encodeRequest(turns, model)
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(baseUrl.trimEnd('/') + ChaiApi.CHAT_PATH).openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty(ChaiApi.API_KEY_HEADER, apiKey)
            }

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val payload = connection.readBodyOrError()

            if (status !in 200..299) {
                return@withContext Outcome.Failed(
                    httpMessageFor(status),
                    payload.take(MAX_DETAIL)
                )
            }

            when (val reply = ChaiApi.parseResponse(payload)) {
                is ChaiApi.Reply.Text -> Outcome.Ok(reply.content)

                is ChaiApi.Reply.Refused -> Outcome.Failed(
                    "Chai declined that request.",
                    reply.message.take(MAX_DETAIL)
                )

                is ChaiApi.Reply.Unrecognised -> Outcome.Failed(
                    "The reply came back in a shape this app does not recognise.",
                    reply.raw.take(MAX_DETAIL)
                )
            }
        } catch (error: UnknownHostException) {
            Outcome.Failed("Cannot reach Chai. Check your connection.", error.message)
        } catch (error: SocketTimeoutException) {
            Outcome.Failed("Chai took too long to answer.", error.message)
        } catch (error: SSLException) {
            Outcome.Failed("Secure connection to Chai failed.", error.message)
        } catch (error: Exception) {
            Outcome.Failed("Something went wrong sending that message.", error.toString())
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * On an error status getInputStream() throws, and the useful part of the
     * response is on the error stream instead, which is exactly where the server
     * explains what was wrong with the request.
     */
    private fun HttpURLConnection.readBodyOrError(): String {
        val stream = try {
            inputStream
        } catch (error: Exception) {
            errorStream
        } ?: return ""

        return stream.bufferedReader().use(BufferedReader::readText)
    }

    private fun httpMessageFor(status: Int): String = when (status) {
        401, 403 -> "Chai rejected the API key. Check it in Settings."
        404 -> "The Chai endpoint was not found. Check the base URL in Settings."
        429 -> "Rate limited by Chai. Wait a moment and try again."
        in 500..599 -> "Chai had a server error ($status). Try again shortly."
        else -> "Chai returned HTTP $status."
    }

    private companion object {
        const val MAX_DETAIL = 600
    }
}
