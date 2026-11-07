package io.github.lazy.dsh.core.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * 生图 (image generation): one OpenAI-compatible `POST {base}/images/generations` endpoint, chosen by
 * the user in 设置 → 模型服务 → 生图. It is independent of the chat route: the same key can drive
 * DeepSeek chat and a third-party image model, or the image model alone.
 *
 * The request carries `{model, prompt, n, size}` and no vendor-specific extras, so OpenAI
 * (`gpt-image-1`, `dall-e-3`), SiliconFlow, Zhipu and other compatible gateways all work. The
 * response may answer with `b64_json` or a `url` (downloaded here); both are normalized to bytes.
 */
@Serializable
data class ImageGenConfig(
    val enabled: Boolean = false,
    /** Base URL of an OpenAI-compatible API, e.g. `https://api.openai.com/v1`. */
    val baseUrl: String = DEFAULT_BASE,
    val model: String = DEFAULT_MODEL,
    /** `1024x1024`, `1024x1536`, `1536x1024` … whatever the model accepts. */
    val size: String = DEFAULT_SIZE,
) {
    /** Configured enough to try (the API key lives in SecretStore, not here). */
    val ready: Boolean get() = enabled && baseUrl.isNotBlank() && model.isNotBlank()

    val endpoint: String get() = imageEndpoint(baseUrl)

    companion object {
        const val DEFAULT_BASE = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-image-1"
        const val DEFAULT_SIZE = "1024x1024"
    }
}

/** SecretStore slot of the image API key. */
const val IMAGE_KEY_SECRET = "imagegen"

/** `…/v1` + `/images/generations`; a URL that already names the endpoint is kept as it is. */
fun imageEndpoint(baseUrl: String): String {
    val base = baseUrl.trim().trimEnd('/')
    return if (base.endsWith("/images/generations")) base else "$base/images/generations"
}

/** The JSON body: only the fields every OpenAI-compatible image endpoint understands. */
fun imageRequestBody(model: String, prompt: String, n: Int, size: String): String = buildJsonObject {
    put("model", model)
    put("prompt", prompt)
    put("n", n.coerceIn(1, 4))
    if (size.isNotBlank()) put("size", size.trim())
}.toString()

/** One generated image, already decoded. */
class GeneratedImage(val bytes: ByteArray, val mime: String)

class ImageGenException(message: String) : Exception(message)

private val imageJson = Json { ignoreUnknownKeys = true }

/**
 * Calls the endpoint and returns [n] images. Runs blocking IO: call it from `Dispatchers.IO`.
 * Throws [ImageGenException] with the server's own message on a non-2xx answer.
 */
fun generateImages(
    config: ImageGenConfig,
    key: String?,
    prompt: String,
    n: Int = 1,
    size: String = config.size,
    userAgent: String? = null,
): List<GeneratedImage> {
    val body = imageRequestBody(config.model, prompt, n, size)
    val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 180_000
        setRequestProperty("Content-Type", "application/json")
        if (!key.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $key")
        if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
    }
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = text.trim().take(600).ifBlank { "(empty response)" }
            throw ImageGenException("HTTP $code: $detail")
        }
        return parseImageResponse(text) { url ->
            downloadImage(url, userAgent)
        }
    } catch (e: ImageGenException) {
        throw e
    } catch (e: IOException) {
        throw ImageGenException(e.message ?: e.javaClass.simpleName)
    } finally {
        connection.disconnect()
    }
}

/**
 * Reads `{ "data": [ { "b64_json": … } | { "url": … } ] }`. A provider that answers `data: null`
 * with a top-level `error.message` is reported as such instead of "no images".
 */
fun parseImageResponse(text: String, download: (String) -> ByteArray): List<GeneratedImage> {
    val root = runCatching { imageJson.parseToJsonElement(text).jsonObject }.getOrNull()
        ?: throw ImageGenException("Unexpected response: " + text.trim().take(300))
    root["error"]?.let { err ->
        val message = runCatching { err.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (!message.isNullOrBlank()) throw ImageGenException(message)
    }
    val data = root["data"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
    val out = mutableListOf<GeneratedImage>()
    for (element in data) {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
        val b64 = obj["b64_json"]?.jsonPrimitive?.contentOrNull
        if (!b64.isNullOrBlank()) {
            val bytes = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: continue
            out += GeneratedImage(bytes, sniffMime(bytes))
            continue
        }
        val url = obj["url"]?.jsonPrimitive?.contentOrNull
        if (!url.isNullOrBlank()) {
            val bytes = runCatching { download(url) }.getOrNull() ?: continue
            out += GeneratedImage(bytes, sniffMime(bytes))
        }
    }
    if (out.isEmpty()) throw ImageGenException("The endpoint returned no image: " + text.trim().take(300))
    return out
}

private fun downloadImage(url: String, userAgent: String?): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30_000
        readTimeout = 180_000
        if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw ImageGenException("Could not download the image (HTTP $code)")
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/** PNG / JPEG / WebP by magic bytes (providers do not always label the payload). */
fun sniffMime(bytes: ByteArray): String = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "image/webp"
    else -> "image/png"
}
