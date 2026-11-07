package io.github.lazy.dsh.core.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class BundledSkillsTest {
    private fun tempRoot(): File = Files.createTempDirectory("dsh-skills").toFile().apply { deleteOnExit() }

    /** Assets as a flat path → text map; [failOn] paths throw on read (a half-broken package). */
    private class MapAssets(private val files: Map<String, String>, private val failOn: Set<String> = emptySet()) :
        BundledSkills.AssetSource {
        override fun list(path: String): List<String> = files.keys
            .filter { it.startsWith("$path/") }
            .map { it.removePrefix("$path/").substringBefore('/') }
            .distinct()

        override fun open(path: String): InputStream {
            if (path in failOn) throw IOException("broken asset: $path")
            return ByteArrayInputStream(files.getValue(path).toByteArray())
        }
    }

    private fun assets(vararg skills: String): MapAssets {
        val map = mutableMapOf<String, String>()
        for (s in skills) {
            map["${BundledSkills.ASSET_ROOT}/$s/SKILL.md"] = "---\nname: $s\ndescription: d\n---\nbody"
            map["${BundledSkills.ASSET_ROOT}/$s/notes/extra.md"] = "extra"
        }
        return MapAssets(map)
    }

    @Test
    fun firstRunCopiesEverySkillAndItsSubfolders() {
        val dest = tempRoot()
        val added = BundledSkills.seed(assets("alpha", "beta"), dest)

        assertEquals(2, added)
        assertEquals("extra", File(dest, "alpha/notes/extra.md").readText())
        assertTrue(File(dest, "beta/SKILL.md").readText().contains("name: beta"))
        val marker = File(dest, BundledSkills.MARKER).readText()
        assertTrue("alpha" in marker && "beta" in marker)
    }

    @Test
    fun secondRunAddsNothing() {
        val dest = tempRoot()
        BundledSkills.seed(assets("alpha"), dest)
        assertEquals(0, BundledSkills.seed(assets("alpha"), dest))
    }

    @Test
    fun aDeletedSkillIsNotResurrected() {
        val dest = tempRoot()
        BundledSkills.seed(assets("alpha"), dest)
        File(dest, "alpha").deleteRecursively()

        assertEquals(0, BundledSkills.seed(assets("alpha"), dest))
        assertFalse(File(dest, "alpha").exists())
    }

    @Test
    fun aSkillAddedByALaterVersionIsInstalledWithoutTouchingTheOthers() {
        val dest = tempRoot()
        BundledSkills.seed(assets("alpha"), dest)
        // The user edited what shipped.
        File(dest, "alpha/SKILL.md").writeText("edited by the user")

        val added = BundledSkills.seed(assets("alpha", "beta"), dest)

        assertEquals(1, added)
        assertEquals("edited by the user", File(dest, "alpha/SKILL.md").readText())
        assertTrue(File(dest, "beta/SKILL.md").exists())
    }

    @Test
    fun anExistingFolderIsAdoptedRatherThanOverwritten() {
        val dest = tempRoot()
        val mine = File(dest, "alpha").apply { mkdirs() }
        File(mine, "SKILL.md").writeText("my own alpha")

        assertEquals(0, BundledSkills.seed(assets("alpha"), dest))

        assertEquals("my own alpha", File(dest, "alpha/SKILL.md").readText())
        assertTrue("alpha" in File(dest, BundledSkills.MARKER).readText())
    }

    @Test
    fun aHalfCopiedSkillIsDroppedAndRetriedNextRun() {
        val dest = tempRoot()
        val broken = MapAssets(
            mapOf(
                "${BundledSkills.ASSET_ROOT}/alpha/SKILL.md" to "---\nname: alpha\ndescription: d\n---\n",
                "${BundledSkills.ASSET_ROOT}/alpha/broken.md" to "x",
            ),
            failOn = setOf("${BundledSkills.ASSET_ROOT}/alpha/broken.md"),
        )

        assertEquals(0, BundledSkills.seed(broken, dest))
        assertFalse("a partial folder must not be left behind", File(dest, "alpha").exists())
        assertFalse("alpha" in File(dest, BundledSkills.MARKER).readText())

        // Next launch, the asset is readable again: the skill lands.
        assertEquals(1, BundledSkills.seed(assets("alpha"), dest))
        assertTrue(File(dest, "alpha/SKILL.md").exists())
    }

    /** The shipped bundle itself: one folder per skill, frontmatter name matching the folder. */
    @Test
    fun bundledAssetsAreWellFormedSkills() {
        val dir = File("src/main/assets/skills")
        assumeTrue("run from the app module to check the shipped bundle", dir.isDirectory)
        val skills = dir.listFiles { f -> f.isDirectory }.orEmpty()
        assertTrue("expected the bundled skills", skills.size >= 20)
        for (skill in skills) {
            val md = File(skill, "SKILL.md")
            assertTrue("${skill.name} has no SKILL.md", md.isFile)
            val head = md.readLines().take(12)
            assertTrue(
                "${skill.name}: frontmatter name must match the folder",
                head.any { it.trim() == "name: ${skill.name}" },
            )
            assertTrue(
                "${skill.name}: missing description",
                head.any { it.trim().startsWith("description:") && it.trim().length > "description: ".length },
            )
        }
    }
}
