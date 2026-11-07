package io.github.lazy.dsh.core.capability.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lazy.dsh.BuildConfig
import io.github.lazy.dsh.core.capability.CallContext
import io.github.lazy.dsh.core.capability.Content
import io.github.lazy.dsh.core.capability.InvalidArgsException
import io.github.lazy.dsh.core.capability.Tool
import io.github.lazy.dsh.core.capability.ToolResult
import io.github.lazy.dsh.core.capability.ToolSet
import io.github.lazy.dsh.core.capability.int
import io.github.lazy.dsh.core.capability.reqStr
import io.github.lazy.dsh.core.capability.str
import io.github.lazy.dsh.core.data.SecretStore
import io.github.lazy.dsh.core.data.SettingsRepository
import io.github.lazy.dsh.core.model.Risk
import io.github.lazy.dsh.core.model.ToolGroup
import io.github.lazy.dsh.core.provider.IMAGE_KEY_SECRET
import io.github.lazy.dsh.core.provider.ImageGenException
import io.github.lazy.dsh.core.provider.generateImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生图: one device tool the agent calls when the user asks for a picture. The endpoint, model and
 * key come from 设置 → 模型服务 → 生图 (an OpenAI-compatible `/images/generations` API), so the chat
 * model and the image model stay independent.
 *
 * Generated files are saved to `Pictures/DSH/` (visible in the gallery) and the chat view shows them
 * inline: the capability bridge stores every [Content.Image] of a call (ShotStore) and ToolRows
 * renders them, like screenshots.
 */
@Singleton
class ImageTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) : ToolSet {

    override val tools = listOf(
        object : Tool("image_generate", ToolGroup.Media, Risk.write, false, DESC, {
            string("prompt", "What the image should show. Be specific about subject, style, colors and composition.", required = true)
            int("n", "How many images to generate (1–4, default 1).")
            string("size", "Pixel size the endpoint understands, e.g. 1024x1024 (default: the size in settings).")
        }) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val config = settings.current().imageGen
                if (!config.ready) throw InvalidArgsException(
                    "Image generation is not set up. Ask the user to enable it in 设置 → 模型服务 → 生图 and add an API key.")
                val key = secrets.read(IMAGE_KEY_SECRET).orEmpty()
                if (key.isBlank()) throw InvalidArgsException(
                    "No image API key stored. Ask the user to add one in 设置 → 模型服务 → 生图.")
                val prompt = args.reqStr("prompt")
                val count = (args.int("n") ?: 1).coerceIn(1, 4)
                val size = args.str("size")?.trim()?.takeIf { it.isNotEmpty() } ?: config.size
                val images = try {
                    withContext(Dispatchers.IO) {
                        generateImages(config, key, prompt, count, size, "DSH-Android/" + BuildConfig.VERSION_NAME)
                    }
                } catch (e: ImageGenException) {
                    return ToolResult.error("IMAGE_API", e.message ?: "image generation failed")
                }
                val saved = images.mapIndexedNotNull { i, image -> saveToGallery(image.bytes, image.mime, i) }
                val caption = buildString {
                    append("Generated ${images.size} image${if (images.size == 1) "" else "s"} with ${config.model}")
                    if (size.isNotBlank()) append(" ($size)")
                    append(".")
                    if (saved.isNotEmpty()) append(" Saved to " + saved.joinToString(", ") + ".")
                    append(" They are shown in the conversation; tell the user where the files are.")
                }
                val shown = images.mapNotNull { image -> preview(image.bytes)?.let { Content.Image(Images.MIME, Base64.encodeToString(it, Base64.NO_WRAP)) } }
                return ToolResult(true, listOf(Content.Text(caption)) + shown)
            }
        },
    )

    /** Full-quality file in the gallery (`Pictures/DSH`), so it survives the session and can be shared. */
    private fun saveToGallery(bytes: ByteArray, mime: String, index: Int): String? = runCatching {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val ext = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val name = "dsh-$stamp-$index.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DSH")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        "Pictures/DSH/$name"
    }.getOrNull()

    /** The chat view's copy: JPEG ≤ 1280 px (the model and the event log stay small). */
    private fun preview(bytes: ByteArray): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        Images.encode(bitmap).bytes
    }.getOrNull()

    private companion object {
        val DESC = """
Generate an image from a text prompt with the user's own image API (设置 → 模型服务 → 生图; OpenAI-compatible
/images/generations). Files are saved to Pictures/DSH and shown in the conversation. Use it when the user asks you
to draw, generate, make or design a picture, logo, poster, illustration or wallpaper.
If it is not configured, say so and point at 设置 → 模型服务 → 生图 — do not pretend an image was made.
        """.trimIndent()
    }
}
