package io.github.lazy.dsh.core.data

import io.github.lazy.dsh.core.model.PermissionPreset
import io.github.lazy.dsh.core.model.SessionModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceModeTest {
    @Test fun `an unknown or missing id is 电荷, never a crash`() {
        assertEquals(PerformanceMode.Electric, PerformanceMode.of(null))
        assertEquals(PerformanceMode.Electric, PerformanceMode.of(""))
        assertEquals(PerformanceMode.Electric, PerformanceMode.of("nonsense"))
        assertEquals(PerformanceMode.Leaf, PerformanceMode.of("leaf"))
        assertEquals(PerformanceMode.Electric, Settings().performance)
    }

    @Test fun `电荷 keeps every skill and asks for one-pass work`() {
        val leafMode = SessionModes.Code
        val applied = PerformanceMode.Electric.applyTo(leafMode)
        assertEquals(leafMode.skills, applied.skills)
        assertTrue(applied.prompt.startsWith(leafMode.prompt))
        assertTrue(applied.prompt.contains("电荷"))
        // The mode's own limits are untouched: only the prompt and the skill switch are ours.
        assertEquals(leafMode.deviceGroups, applied.deviceGroups)
        assertEquals(leafMode.engineTools, applied.engineTools)
        assertEquals(leafMode.preset, applied.preset)
    }

    @Test fun `叶绿 drops the skill catalogue and asks for short answers`() {
        val applied = PerformanceMode.Leaf.applyTo(SessionModes.Default)
        assertFalse(applied.skills)
        assertTrue(applied.prompt.contains("叶绿"))
        assertTrue(applied.prompt.contains("tokens"))
    }

    @Test fun `a mode with skills already off stays off in 电荷`() {
        val chat = SessionModes.Chat // skills = false
        assertFalse(PerformanceMode.Electric.applyTo(chat).skills)
    }

    @Test fun `the empty prompt of a user mode does not leave a blank line`() {
        val applied = PerformanceMode.Leaf.applyTo(SessionModes.Default)
        assertFalse(applied.prompt.contains("\n\n\n"))
        assertFalse(applied.prompt.startsWith("\n"))
    }

    @Test fun `the route the modes write follows effort and reason`() {
        assertEquals(Effort.High, PerformanceMode.Electric.effort)
        assertEquals(Effort.Low, PerformanceMode.Leaf.effort)
        assertTrue(PerformanceMode.Electric.reflect)
        assertFalse(PerformanceMode.Leaf.reflect)
        // Both pick a level only from the vocabulary a model can offer.
        assertTrue(PerformanceMode.Electric.reasoning.all { it in io.github.lazy.dsh.core.provider.ReasoningLevels.EFFORT + "enabled" })
        assertTrue(PerformanceMode.Leaf.reasoning.all { it in io.github.lazy.dsh.core.provider.ReasoningLevels.EFFORT + "disabled" })
    }

    @Test fun `reflection is off in 叶绿 even when the user left the setting on`() {
        val on = Settings(reflectExperience = true)
        assertTrue(on.copy(performance = PerformanceMode.Electric).reflects)
        assertFalse(on.copy(performance = PerformanceMode.Leaf).reflects)
        assertFalse(Settings(reflectExperience = false, performance = PerformanceMode.Electric).reflects)
    }

    @Test fun `the mode is not a permission preset`() {
        // Sanity: switching effort does not quietly loosen or tighten what the agent may do.
        val m = PerformanceMode.Leaf.applyTo(SessionModes.Phone)
        assertEquals(PermissionPreset.Ask.id, m.preset)
        assertTrue(m.allowsGroup(io.github.lazy.dsh.core.model.ToolGroup.Files))
    }
}
