package io.github.lazy.dsh.core.data

import io.github.lazy.dsh.core.model.SessionMode

/**
 * 能耗模式 — the two characters of the brand sheet, each a way of spending the model.
 *
 * - [Electric] 电荷: finish fast without giving up quality. Full reasoning effort, every skill in
 *   the prompt, and the one extra model call that learns App 经验 after a device task. The
 *   assistant is told to decide, act and answer in one pass.
 * - [Leaf] 叶绿: spend as few tokens as possible. Low effort, no skill catalogue in the prompt, no
 *   extra model calls, and the assistant is told to answer short and skip optional steps.
 *
 * The mode never changes the model: that stays whatever the user picked (a third-party route
 * especially). Everything it does set is here, so the two modes stay honest and testable.
 */
enum class PerformanceMode(val id: String) {
    Electric("electric"),
    Leaf("leaf");

    /** The DeepSeek reasoning effort this mode sends. */
    val effort: Effort get() = when (this) {
        Electric -> Effort.High
        Leaf -> Effort.Low
    }

    /** Whether the skill catalogue stays in the system prompt (20 skills' descriptions cost tokens every turn). */
    val skills: Boolean get() = this == Electric

    /** Whether a device task gets its post-turn reflection call (叶绿 spends nothing extra). */
    val reflect: Boolean get() = this == Electric

    /** The model-facing instruction appended to the conversation's session-mode prompt. */
    val prompt: String get() = when (this) {
        Electric ->
            "You are in 电荷 mode: finish fast and finish well. Decide, act and answer in one pass — do not restate the request, " +
                "do not narrate a plan the user can already see, and do not ask for confirmation you were not asked to get. " +
                "Batch independent tool calls instead of making them one at a time, and take the shortest path that fully solves " +
                "the task. Check your work once, not repeatedly; when the intent is clear, execute it instead of describing it."
        Leaf ->
            "You are in 叶绿 mode: spend as few tokens as possible. Answer with the shortest correct response — no preamble, " +
                "no restating the request, no summary of what you just did, and no follow-up question unless you are truly blocked. " +
                "Reuse what is already in the context instead of re-reading files or repeating a tool call. Skip optional steps " +
                "(extra searches, extra verification passes, subagents) unless the task cannot be done without them. " +
                "Prefer compact answers, code and data over prose."
    }

    /** Third-party reasoning level ids to prefer, best first (where the model offers them). */
    val reasoning: List<String> get() = when (this) {
        Electric -> listOf("high", "xhigh", "max", "medium", "ultra", "enabled")
        Leaf -> listOf("minimal", "low", "medium", "disabled")
    }

    /** [mode] as the engine should see it under this mode: the extra instruction, and skills in 叶绿 off. */
    fun applyTo(mode: SessionMode): SessionMode = mode.copy(
        prompt = listOf(mode.prompt.trim(), prompt).filter { it.isNotEmpty() }.joinToString("\n\n"),
        skills = mode.skills && skills,
    )

    companion object {
        /** 电荷: the app's own identity, and the safer default (nothing about quality is given up). */
        val Default = Electric
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Default
    }
}
