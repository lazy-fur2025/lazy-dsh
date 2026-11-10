package io.github.lazy.dsh

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.lazy.dsh.core.capability.AgentScreen
import io.github.lazy.dsh.core.data.SettingsRepository
import io.github.lazy.dsh.core.platform.AppLanguage
import io.github.lazy.dsh.core.platform.Notifications
import io.github.lazy.dsh.core.trigger.TriggerEvents
import io.github.lazy.dsh.core.trigger.TriggerHub
import io.github.lazy.dsh.service.FloatingBallService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class DshApp : Application() {
    @Inject lateinit var triggers: dagger.Lazy<TriggerHub>
    @Inject lateinit var settings: dagger.Lazy<SettingsRepository>
    @Inject lateinit var secrets: dagger.Lazy<io.github.lazy.dsh.core.data.SecretStore>
    @Inject lateinit var agentScreen: dagger.Lazy<AgentScreen>
    @Inject lateinit var timetableReminders: dagger.Lazy<io.github.lazy.dsh.core.timetable.TimetableReminders>

    override fun onCreate() {
        super.onCreate()
        // Every process (the :engine one posts its notification too): the in-app language choice.
        AppLanguage.applyToProcess(this)
        if (!isMainProcess()) return
        Notifications.createChannels(this)
        importDebugKey()
        TriggerEvents.init(this)
        // Startup work must never crash the process (a bad stored rule used to kill every app start).
        val guard = CoroutineExceptionHandler { _, e -> android.util.Log.e("DshApp", "startup task failed", e) }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + guard).launch {
            // Bundled skills land in $DSH_HOME/skills before the engine reads them (never overwrites).
            withContext(Dispatchers.IO) { io.github.lazy.dsh.core.skills.BundledSkills.seed(this@DshApp) }
            // Import :engine crash records (ApplicationExitInfo / tombstones) from earlier runs.
            runCatching { io.github.lazy.dsh.core.engine.EngineDiagnostics.collectExitInfo(this@DshApp) }
            runCatching { triggers.get().sync() }.onFailure { android.util.Log.e("DshApp", "automation sync failed", it) }
            // Force-stop and updates clear pending alarms: re-arm the class reminder chain.
            runCatching { timetableReminders.get().resync() }.onFailure { android.util.Log.e("DshApp", "timetable resync failed", it) }
            if (settings.get().current().floatingBall) runCatching { FloatingBallService.setEnabled(this@DshApp, true) }
            runCatching { agentScreen.get().restoreLeftoverMutes() }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 10–12: a configuration change resets the process locale to the system's.
        AppLanguage.applyToProcess(this)
    }

    /** Debug builds only: device tests provision the key with `run-as <pkg> sh -c 'cat > files/debug.key'`. */
    private fun importDebugKey() {
        if (!BuildConfig.DEBUG) return
        val f = java.io.File(filesDir, "debug.key")
        if (!f.exists()) return
        f.readText().trim().takeIf { it.isNotEmpty() }?.let { secrets.get().write(it) }
        f.delete()
    }

    private fun isMainProcess() = getProcessName() == packageName
}
