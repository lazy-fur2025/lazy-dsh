package io.github.lazy.dsh.core.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ImageGenTest {

    // ── endpoint ──

    @Test fun endpointAppendsTheGenerationsPath() {
        assertEquals("https://api.openai.com/v1/images/generations", imageEndpoint("https://api.openai.com/v1"))
        assertEquals("https://api.openai.com/v1/images/generations", imageEndpoint("https://api.openai.com/v1/"))
        assertEquals("https://api.siliconflow.cn/v1/images/generations", imageEndpoint("  https://api.siliconflow.cn/v1  "))
    }

    @Test fun endpointKeepsAFullUrl() {
        assertEquals("https://x.test/v1/images/generations", imageEndpoint("https://x.test/v1/images/generations"))
        assertEquals("https://x.test/v1/images/generations", imageEndpoint("https://x.test/v1/images/generations/"))
    }

    // ── request body ──

    @Test fun requestCarriesOnlyTheCommonFields() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            imageRequestBody("gpt-image-1", "a red fox in snow", 2, "1024x1536")
        ).jsonObject
        assertEquals("gpt-image-1", json["model"]?.jsonPrimitive?.content)
        assertEquals("a red fox in snow", json["prompt"]?.jsonPrimitive?.content)
        assertEquals("2", json["n"]?.jsonPrimitive?.content)
        assertEquals("1024x1536", json["size"]?.jsonPrimitive?.content)
        // No vendor-specific extras (response_format breaks some gateways).
        assertEquals(setOf("model", "prompt", "n", "size"), json.keys)
    }

    @Test fun requestClampsTheCount() {
        val body = imageRequestBody("m", "p", 9, "1024x1024")
        assertTrue(body.contains("\"n\":4"))
    }

    // ── response ──

    @Test fun parsesBase64Images() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val b64 = Base64.getEncoder().encodeToString(png)
        val out = parseImageResponse("""{"created":1,"data":[{"b64_json":"$b64"}]}""") { error("no download expected") }
        assertEquals(1, out.size)
        assertArrayEquals(png, out[0].bytes)
        assertEquals("image/png", out[0].mime)
    }

    @Test fun downloadsUrlAnswers() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9, 9)
        var asked: String? = null
        val out = parseImageResponse("""{"data":[{"url":"https://cdn.test/a.jpg"}]}""") { url -> asked = url; jpeg }
        assertEquals("https://cdn.test/a.jpg", asked)
        assertArrayEquals(jpeg, out[0].bytes)
        assertEquals("image/jpeg", out[0].mime)
    }

    @Test fun surfacesProviderErrorMessages() {
        val e = assertThrows(ImageGenException::class.java) {
            parseImageResponse("""{"error":{"message":"invalid api key"}}""") { error("unused") }
        }
        assertEquals("invalid api key", e.message)
    }

    @Test fun emptyDataIsAnError() {
        val e = assertThrows(ImageGenException::class.java) {
            parseImageResponse("""{"data":[]}""") { error("unused") }
        }
        assertTrue(e.message!!.contains("no image"))
    }

    @Test fun mimeSniffing() {
        assertEquals("image/png", sniffMime(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)))
        assertEquals("image/jpeg", sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }
}
