package frb.axeron.manager.ui.viewmodel

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import frb.axeron.adb.AdbPairingService
import frb.axeron.adb.util.AdbEnvironment
import frb.axeron.api.Axeron
import frb.axeron.api.AxeronCommandSession
import frb.axeron.api.AxeronInfo
import frb.axeron.api.core.AxeronSettings
import frb.axeron.api.core.Engine
import frb.axeron.api.core.Starter
import frb.axeron.manager.service.BinderRequest
import frb.axeron.manager.service.ServerHealthScheduler
import frb.axeron.manager.service.ServerProcessUtil
import frb.axeron.manager.adb.AdbStarter
import frb.axeron.manager.adb.AdbStarter.stopTcp
import frb.axeron.manager.adb.AdbStateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ActivateViewModel : ViewModel() {

    companion object {
        const val TAG = "AdbViewModel"
        const val ACTIVATE_FAILED = -1
        const val ACTIVATE_PROCESS = 0
        const val ACTIVATE_SUCCESS = 1
        const val MAX_AUTO_RETRIES = 10

        /**
         * How long to wait for the binder before declaring the server gone.
         *
         * The bind is asynchronous, so on a cold start there is a window where the server is
         * already up but we have not reached it yet. Treating that window as "dead" is what
         * used to make a healthy server look stopped for a beat - and then get restarted.
         * Kept short so a genuinely absent server still resolves quickly; a false Disable is
         * now cheap anyway, because auto-restart additionally requires proof of death.
         */
        const val BINDER_GRACE_PERIOD_MS = 1500L

        /**
         * How many times to ask the server for its binder before declaring it unreachable.
         *
         * [BinderRequest.request] is a broadcast, and broadcasts are best-effort - right after a
         * force-stop the platform may drop the first one. A single request therefore left a
         * healthy server looking stopped. We retry a couple of times and stop the instant the
         * binder appears (the sticky listener usually emits Running first, so we rarely even
         * reach the last attempt).
         */
        const val BINDER_REQUEST_ATTEMPTS = 3

        /** Pause between binder requests. Kept short so a truly dead server still resolves fast. */
        const val BINDER_REQUEST_RETRY_MS = 500L
    }

    /**
     * Pure status classification, with no side effects.
     *
     * Shared by the constructor probe and [axeronObserve] so the synchronous and asynchronous
     * paths can never disagree about whether the server is up. Deliberately free of
     * `setTryToActivate` / `Axeron.newProcess` side effects so it is safe to call while the
     * object is still being constructed.
     */
    private fun classify(info: AxeronInfo): ActivateStatus = when {
        // A placeholder reply means the IPC failed - Axeron.getAxeronInfo() swallows the error
        // and hands back AxeronInfo(), whose serverInfo.versionCode is -1. Since isNeedUpdate()
        // is `VERSION_CODE > versionCode && pingBinder()`, a placeholder satisfies it and we
        // would report "Updating" for a perfectly healthy server. The Updating branch does
        // nothing else - no wasRunning, no health check, no path to Running - so the UI would
        // wedge on "Updating". Report the honest "not confirmed" instead and let the next
        // binder event settle it.
        info.getVersionCode() <= 0L -> ActivateStatus.Disable
        info.isNeedUpdate() -> ActivateStatus.Updating(info)
        info.isRunning() -> ActivateStatus.Running(info)
        info.isNeedExtraStep() -> ActivateStatus.NeedExtraStep
        else -> ActivateStatus.Disable
    }

    /**
     * Instantaneous liveness probe.
     *
     * [Axeron.pingBinder] is just `binder.pingBinder()` - a local check against a binder we
     * already hold, with no round trip. Returns null when the binder is not connected yet,
     * which is deliberately distinct from "the server is dead": that is the async-bind window,
     * not a verdict.
     */
    private fun probeNow(): Pair<ActivateStatus, AxeronInfo>? {
        if (!Axeron.pingBinder()) return null
        val info = runCatching { Axeron.getAxeronInfo() }.getOrElse { return null }
        return classify(info) to info
    }

    private val initialProbe: Pair<ActivateStatus, AxeronInfo>? = probeNow()

    var activateStatus by mutableStateOf<ActivateStatus>(
        initialProbe?.first ?: ActivateStatus.Disable
    )
        private set

    var axeronInfo by mutableStateOf(
        initialProbe?.second ?: AxeronInfo()
    )
        private set

    var isShizukuActive by mutableStateOf(
        Axeron.getShizukuService() != null
    )
        private set

    fun setShizukuIntercept(enable: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            isShizukuActive = enable
            Axeron.enableShizukuService(enable)
        }
    }

    fun checkShizukuIntercept() {
        viewModelScope.launch(Dispatchers.Main) {
            isShizukuActive = Axeron.pingBinder() && Axeron.getShizukuService() != null
        }
    }

    var isNotificationEnabled by mutableStateOf(false)
        private set

    var devSettings by mutableStateOf(false)
        private set

    fun setLaunchDevSettings(launch: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            devSettings = launch
        }
    }

    var tryActivate by mutableStateOf(false)
        private set

    fun setTryToActivate(activate: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            tryActivate = activate
        }
    }

    // Auto-restart state
    private var autoRestartJob: Job? = null
    private var retryCount = 0
    var intentionalStop by mutableStateOf(false)
        private set

    fun markIntentionalStop() {
        intentionalStop = true
        AxeronSettings.setWasRunning(false)
        ServerHealthScheduler.cancel(Engine.application)
        autoRestartJob?.cancel()
    }

    sealed class ActivateStatus {
        object Disable : ActivateStatus()
        object NeedExtraStep : ActivateStatus()
        class Updating(val axeronInfo: AxeronInfo) : ActivateStatus()
        class Running(val axeronInfo: AxeronInfo) : ActivateStatus()
    }

    fun axeronObserve(): Flow<ActivateStatus> = callbackFlow {
        if (Axeron.pingBinder()) {
            Log.i("AxoraBinder", "binderHasReceived")
            val info = Axeron.getAxeronInfo()
            when (val status = classify(info)) {
                is ActivateStatus.Updating -> {
                    trySend(status)
                    setTryToActivate(true)
                    Axeron.newProcess(
                        AxeronCommandSession.getQuickCmd(
                            Starter.internalCommand,
                            true,
                            false
                        ),
                        null,
                        null
                    )
                }

                else -> trySend(status)
            }
        }
        val receivedListener = Axeron.OnBinderReceivedListener {
            Log.i("AxoraBinder", "onBinderReceived")
            probeNow()?.let { (status, _) -> trySend(status) }
        }
        val deadListener = Axeron.OnBinderDeadListener {
            Log.i("AxoraBinder", "onBinderDead")
            trySend(ActivateStatus.Disable)
        }
        // Sticky, so a binder that is already connected when we subscribe still notifies us
        // immediately instead of waiting for the next reconnect.
        Axeron.addBinderReceivedListenerSticky(receivedListener)
        Axeron.addBinderDeadListener(deadListener)

        if (!Axeron.pingBinder()) {
            delay(BINDER_GRACE_PERIOD_MS)
            // Re-check: the sticky listener may have resolved it while we were waiting.
            if (!Axeron.pingBinder()) {
                // Still nothing. Rather than sit on "Need to Activate" indefinitely, ask the
                // server to hand the binder over directly. The server answers on its own
                // receiver, so this does not depend on the observer callbacks firing after a
                // force-stop. A duplicate reply is harmless - onBinderReceived ignores a binder
                // that is already live.
                //
                // The request is a best-effort broadcast and can be dropped, so ask more than
                // once before giving up. We stop the moment the binder appears; the sticky
                // received-listener normally wins the race and emits Running on its own, in
                // which case we never reach Disable.
                var bound = Axeron.pingBinder()
                for (attempt in 1..BINDER_REQUEST_ATTEMPTS) {
                    if (bound) break
                    BinderRequest.request(Engine.application)
                    if (attempt < BINDER_REQUEST_ATTEMPTS) {
                        delay(BINDER_REQUEST_RETRY_MS)
                        bound = Axeron.pingBinder()
                    }
                }
                if (!bound) trySend(ActivateStatus.Disable)
            }
        }

        awaitClose {
            Axeron.removeBinderReceivedListener(receivedListener)
            Axeron.removeBinderDeadListener(deadListener)
        }
    }

    init {
        viewModelScope.launch {
            axeronObserve().collect { status ->
                val isStillUpdating =
                    status is ActivateStatus.Disable && activateStatus is ActivateStatus.Updating

                when (status) {
                    is ActivateStatus.Running -> {
                        AxeronSettings.setWasRunning(true)
                        intentionalStop = false
                        retryCount = 0
                        autoRestartJob?.cancel()
                        ServerHealthScheduler.schedule(Engine.application)
                        checkShizukuIntercept()
                        axeronInfo = status.axeronInfo
                    }

                    is ActivateStatus.Updating -> {
                        axeronInfo = status.axeronInfo
                    }

                    else -> {
                        axeronInfo = if (isStillUpdating) {
                            (activateStatus as ActivateStatus.Updating).axeronInfo
                        } else {
                            AxeronInfo()
                        }
                    }
                }

                if (isStillUpdating) return@collect

                if (status is ActivateStatus.Disable && AxeronSettings.getWasRunning() && !intentionalStop && AxeronSettings.getEnableAutoRestart()) {
                    launchAutoRestart()
                }

                Log.i("AxoraBinder", "status: $status")
                activateStatus = status
                setTryToActivate(false)
            }
        }
    }

    private fun launchAutoRestart() {
        autoRestartJob?.cancel()
        autoRestartJob = viewModelScope.launch(Dispatchers.IO) {
            while (retryCount < MAX_AUTO_RETRIES && !intentionalStop) {
                if (Axeron.pingBinder()) break

                val delay = minOf(1000L shl retryCount, 30000L)
                delay(delay)

                if (intentionalStop || Axeron.pingBinder()) break

                // Proof-of-death gate, shared by both launch modes. A restart is only allowed
                // when the process is *positively* confirmed gone. This matters because:
                //  - the ADB-launched server is detached and survives a force-stop, so
                //    "binder unreachable" does not imply "server dead";
                //  - starter.cpp SIGKILLs any existing axeron_server before spawning, so a
                //    needless restart destroys a healthy process, resets its uptime, and
                //    hands the user a new PID.
                // Each mode needs a different probe: ADB has no su, so it must ask over the ADB
                // channel, while root can just use its shell.
                val launchMode = AxeronSettings.getLastLaunchMode()
                val dead = when (launchMode) {
                    AxeronSettings.LaunchMethod.ADB ->
                        ServerProcessUtil.isServerDefinitelyDeadOverAdb(Engine.application)
                    else -> ServerProcessUtil.isServerDefinitelyDead()
                }

                if (!dead) {
                    // Either a server is alive, or we could not prove otherwise. Both mean
                    // "leave it alone" - a needless restart is strictly the worse mistake.
                    if (launchMode != AxeronSettings.LaunchMethod.ADB &&
                        Shell.getCachedShell() == null
                    ) {
                        // Root mode with no shell yet. Ask for one asynchronously; blocking on
                        // it would wedge this loop and starve the binder, so the status would
                        // never settle on Running. Does NOT consume a retry, otherwise a device
                        // that never grants root would burn the whole budget on no-ops.
                        ServerProcessUtil.requestShellAsync()
                        Log.i(TAG, "Auto-restart deferred: no cached shell yet, will retry")
                    } else {
                        Log.i(TAG, "Auto-restart skipped: server alive, or death unproven")
                    }
                    continue
                }

                retryCount++
                Log.i(TAG, "Auto-restart attempt $retryCount/$MAX_AUTO_RETRIES")

                // The liveness probe does I/O, which can take a moment. Re-confirm the binder
                // once more so a reconnect during that window is never raced.
                if (Axeron.pingBinder()) {
                    Log.i(TAG, "Auto-restart skipped: binder reconnected during liveness check")
                    break
                }

                when (launchMode) {
                    AxeronSettings.LaunchMethod.ROOT -> tryRootRestart()
                    AxeronSettings.LaunchMethod.ADB -> tryAdbRestart()
                }
            }
        }
    }

    private fun tryRootRestart() {
        runCatching {
            val shell = Shell.getCachedShell()
            if (shell != null && shell.isAlive && shell.isRoot) {
                Shell.cmd(Starter.internalCommand).exec()
                Log.i(TAG, "Auto-restart via root: command sent")
            } else {
                Log.w(TAG, "Auto-restart via root: no cached root shell available")
            }
        }.onFailure {
            Log.e(TAG, "Auto-restart via root failed", it)
        }
    }

    private suspend fun tryAdbRestart() {
        runCatching {
            // Shared with the liveness probe on purpose: if the two disagreed we could prove the
            // server dead on one port and then send the restart to another, doing nothing.
            // This used to consider only getTcpPort(), which is 0 unless TCP mode is on, so in
            // the default mDNS pairing it logged "no TCP port configured" and gave up silently.
            val port = AdbStarter.resolvePort()
            if (port > 0) {
                AdbStarter.startAdbClient(Engine.application, port) {}
                Log.i(TAG, "Auto-restart via ADB: command sent to port $port")
            } else {
                Log.w(TAG, "Auto-restart via ADB: no usable port")
            }
        }.onFailure {
            Log.e(TAG, "Auto-restart via ADB failed", it)
        }
    }

    fun startRoot(result: (Int) -> Unit = {}) {
        intentionalStop = false
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (tryActivate) return@launch result(ACTIVATE_PROCESS)
                setTryToActivate(true)

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    result(ACTIVATE_FAILED)
                    return@launch
                }

                Shell.cmd(Starter.internalCommand).submit {
                    if (it.isSuccess) {
                        AxeronSettings.setLastLaunchMode(AxeronSettings.LaunchMethod.ROOT)
                        result(ACTIVATE_SUCCESS)
                    } else {
                        result(ACTIVATE_FAILED)
                    }
                    Shell.getCachedShell()?.close()
                }
            }.onFailure {
                it.printStackTrace()
                result(ACTIVATE_FAILED)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun updateNotificationState(context: Context) {
        viewModelScope.launch {
            isNotificationEnabled = checkNotificationEnabled(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startAdbWireless(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        intentionalStop = false
        viewModelScope.launch(Dispatchers.IO) {
            if (AdbEnvironment.isWifiRequired() && !isWifiEnabled(context)) return@launch requestEnableWifi(context)
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            AdbStarter.startAdbWireless(context, result)
        }
    }

    fun startAdbTcp(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        intentionalStop = false
        viewModelScope.launch(Dispatchers.IO) {
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            val tcpPort = AdbEnvironment.getAdbTcpPort()

            AdbStarter.startAdbClient(context, tcpPort, result)
        }
    }

    fun stopAdbTcp(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            val tcpPort = AdbEnvironment.getAdbTcpPort()
            if (tcpPort > 0 && !AxeronSettings.getTcpMode()) {
                stopTcp(context, tcpPort)
            }
        }
    }

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun requestEnableWifi(context: Context) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        context.startActivity(intent)
    }



    @RequiresApi(Build.VERSION_CODES.R)
    fun startPairingService(context: Context) = runBlocking(Dispatchers.IO) {
        if (!isNotificationEnabled) return@runBlocking
        setLaunchDevSettings(true)

        val intent = AdbPairingService.startIntent(context)
        try {
            context.startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e("Axora", "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = context.getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow(
                        "android:start_foreground",
                        android.os.Process.myUid(),
                        context.packageName,
                        null,
                        null
                    )
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(
                        context,
                        "OP_START_FOREGROUND is denied. What are you doing?",
                        Toast.LENGTH_LONG
                    ).show()
                }
                context.startService(intent)
            }
        }
    }


    /**
     * Cek notifikasi aktif atau tidak
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkNotificationEnabled(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }
}