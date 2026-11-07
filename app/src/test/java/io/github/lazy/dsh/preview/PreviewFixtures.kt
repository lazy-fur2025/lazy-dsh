package io.github.lazy.dsh.preview

import io.github.lazy.dsh.core.data.SessionEntity
import io.github.lazy.dsh.core.data.Settings
import io.github.lazy.dsh.core.session.Block
import io.github.lazy.dsh.core.session.Conversation
import io.github.lazy.dsh.core.session.RowStatus
import io.github.lazy.dsh.core.session.SessionLive
import io.github.lazy.dsh.core.session.TodoItem
import io.github.lazy.dsh.core.session.ToolState
import io.github.lazy.dsh.core.session.Turn
import io.github.lazy.dsh.core.session.TurnStatus
import io.github.lazy.dsh.core.session.Usage
import io.github.lazy.dsh.core.session.UserMsg

/** Deterministic sample data for headless screenshots. Not used by the app. */
object PreviewFixtures {
    private const val MIN = 60_000L
    private const val NOW = 1_790_000_000_000L

    val settings = Settings()

    val sessions: List<SessionEntity> = listOf(
        SessionEntity("s1", "排查启动崩溃", NOW - 40 * MIN, NOW - 2 * MIN, running = true, pinned = true),
        SessionEntity("s2", "把这份周报整理成三点", NOW - 3 * 60 * MIN, NOW - 55 * MIN, unread = true),
        SessionEntity("s3", "美团和饿了么的优惠对比", NOW - 5 * 60 * MIN, NOW - 4 * 60 * MIN),
        SessionEntity("s4", "给妈妈写一条生日祝福", NOW - 26 * 60 * MIN, NOW - 25 * 60 * MIN, muted = true),
        SessionEntity("s5", "总结这篇论文的实验部分", NOW - 50 * 60 * MIN, NOW - 48 * 60 * MIN),
        SessionEntity("s6", "旧版归档：旅行计划", NOW - 90 * 60 * MIN, NOW - 80 * 60 * MIN, archived = true),
    )

    val status: Map<String, RowStatus> = mapOf("s1" to RowStatus.Running, "s3" to RowStatus.Question)

    val conversation: Conversation = Conversation(
        turns = listOf(
            Turn(
                key = "t1", number = 1,
                user = UserMsg(seq = 1, time = NOW - 8 * MIN, text = "应用一打开就闪退，日志在下面，帮我看看是什么原因。", images = 0, files = 0),
                blocks = listOf(
                    Block.Reasoning("r1", "先确认崩溃栈里的类名，再对照版本号判断是不是依赖冲突。"),
                    Block.Tool(
                        key = "c1", callId = "call_1", name = "read", arguments = "{\"path\":\"app/logs/crash.txt\"}",
                        args = null, state = ToolState.Done, result = "FATAL EXCEPTION: main\njava.lang.NoSuchMethodError: ...",
                        error = null, meta = null, startTime = NOW - 7 * MIN, endTime = NOW - 7 * MIN + 900,
                    ),
                ),
                startTime = NOW - 8 * MIN, endTime = NOW - 6 * MIN, status = TurnStatus.Completed, error = null,
                usage = Usage(), todos = listOf(TodoItem("读取崩溃日志", "completed"), TodoItem("定位冲突依赖", "completed")),
            ),
            Turn(
                key = "t2", number = 2,
                user = UserMsg(seq = 2, time = NOW - 5 * MIN, text = "那要怎么改？给我一个最小改动。", images = 0, files = 0),
                blocks = listOf(
                    Block.Text(
                        "k2",
                        "是 `androidx.core` 的版本被另一个库降级了。最小改动是在 `app/build.gradle.kts` 里显式锁版本：\n\n" +
                            "```kotlin\nimplementation(\"androidx.core:core-ktx:1.13.1\")\n```\n\n" +
                            "改完同步一次，再跑 `./gradlew :app:assembleDebug`。如果还崩，把新的栈贴给我。",
                        interrupted = false,
                    ),
                ),
                startTime = NOW - 5 * MIN, endTime = NOW - 4 * MIN, status = TurnStatus.Completed, error = null, usage = Usage(),
            ),
        ),
        todos = listOf(TodoItem("读取崩溃日志", "completed"), TodoItem("定位冲突依赖", "completed"), TodoItem("给出修复方案", "completed")),
        contextTokens = 18_420,
    )

    val live = SessionLive(sessionId = "s1", title = "排查启动崩溃", exists = true)

    val runningLive = live.copy(running = true, runStartedAt = NOW - 6_000)
}
