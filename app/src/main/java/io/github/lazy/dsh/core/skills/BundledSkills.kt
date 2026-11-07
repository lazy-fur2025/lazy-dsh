package io.github.lazy.dsh.core.skills

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.InputStream

/**
 * Skills that ship inside the APK (`assets/skills/<name>/SKILL.md`) and are seeded into the engine's
 * skills folder (`$DSH_HOME/skills`) on first launch.
 *
 * Rules, all of them about not fighting the user:
 *  - Whatever is already on disk wins. A bundled skill never overwrites an existing folder (the user
 *    may have edited it, or imported their own skill under the same name).
 *  - Deleting one sticks. The marker remembers every name ever seeded, so a deleted skill is not
 *    resurrected on the next launch — only names this app has never installed before get added.
 *  - An installed skill is never overwritten, so a later app version adding `foo` ships the new
 *    content, while a rewrite of an already-seeded `foo` does not clobber the copy on disk.
 *  - Deleted names stay recoverable by importing the same skill by hand (扩展 → 技能).
 */
object BundledSkills {
    /** The asset folder that holds one directory per skill. */
    const val ASSET_ROOT = "skills"

    /** Hidden file in the skills folder: the names already seeded, one per line. */
    const val MARKER = ".bundled-skills"

    /** Seeds the bundled skills for a real installation. Returns how many folders were copied. */
    fun seed(context: Context): Int {
        val dest = File(context.filesDir, "dsh-home/skills")
        return runCatching { seed(AndroidAssets(context.assets), dest) }
            .onFailure { android.util.Log.w("BundledSkills", "seeding failed", it) }
            .getOrDefault(0)
    }

    /** The testable core: [source] plays the APK assets, [destRoot] is `$DSH_HOME/skills`. */
    internal fun seed(source: AssetSource, destRoot: File): Int {
        val names = runCatching { source.list(ASSET_ROOT) }.getOrDefault(emptyList())
            .filter { it.isNotBlank() }
            .sorted()
        if (names.isEmpty()) return 0
        destRoot.mkdirs()

        val marker = File(destRoot, MARKER)
        val seeded = (if (marker.isFile) marker.readLines() else emptyList())
            .map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        var added = 0
        for (name in names) {
            if (name in seeded) continue
            val target = File(destRoot, name)
            if (!target.exists()) {
                if (copy(source, "$ASSET_ROOT/$name", target)) added++ else target.deleteRecursively()
            }
            // Marked only once the folder is really there: a failed copy is retried on the next launch.
            if (target.exists()) seeded += name
        }
        // The marker records names, not a version: deleting a seeded skill keeps it deleted, while a
        // name this app has never installed is added on the launch that follows an update.
        marker.writeText(seeded.sorted().joinToString("\n", postfix = "\n"))
        return added
    }

    /** Copies one skill folder recursively. Returns false when nothing could be written. */
    private fun copy(source: AssetSource, from: String, to: File): Boolean {
        val children = runCatching { source.list(from) }.getOrDefault(emptyList())
        if (children.isEmpty()) {
            return runCatching {
                to.parentFile?.mkdirs()
                source.open(from).use { input -> to.outputStream().use { input.copyTo(it) } }
                true
            }.getOrDefault(false)
        }
        if (!to.exists() && !to.mkdirs()) return false
        var ok = true
        for (child in children) ok = copy(source, "$from/$child", File(to, child)) && ok
        return ok
    }

    /** The slice of [AssetManager] this needs, so the seeding logic is a plain JVM test. */
    internal interface AssetSource {
        fun list(path: String): List<String>
        fun open(path: String): InputStream
    }

    internal class AndroidAssets(private val assets: AssetManager) : AssetSource {
        override fun list(path: String): List<String> = assets.list(path)?.toList().orEmpty()
        override fun open(path: String): InputStream = assets.open(path)
    }
}
