package frb.axeron.manager.service

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.topjohnwu.superuser.Shell
import frb.axeron.api.Axeron
import frb.axeron.api.core.AxeronSettings
import frb.axeron.api.core.Starter
import frb.axeron.manager.adb.AdbStarter

class ServerHealthWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ServerHealthWorker"
    }

    override suspend fun doWork(): Result {
        if (!AxeronSettings.getEnableAutoRestart()) return Result.success()

        if (Axeron.pingBinder()) return Result.success()

        if (!AxeronSettings.getWasRunning()) return Result.success()

        // Proof-of-death gate. ADB has no su, so it must probe over the ADB channel; root uses
        // its shell. Inconclusive means "assume alive" - see ServerProcessUtil.
        val launchMode = AxeronSettings.getLastLaunchMode()
        val dead = when (launchMode) {
            AxeronSettings.LaunchMethod.ADB ->
                ServerProcessUtil.isServerDefinitelyDeadOverAdb(applicationContext)
            else -> ServerProcessUtil.isServerDefinitelyDead()
        }
        if (!dead) {
            Log.w(TAG, "Server process is alive (or could not be confirmed dead); skip restart")
            if (launchMode != AxeronSettings.LaunchMethod.ADB &&
                Shell.getCachedShell() == null
            ) {
                // Root mode with no shell yet - ask for one without blocking, so this worker
                // (and the thread it borrows) is never parked waiting for root.
                ServerProcessUtil.requestShellAsync()
            }
            return Result.success()
        }

        Log.w(TAG, "Server is dead — attempting auto-restart from WorkManager")

        when (launchMode) {
            AxeronSettings.LaunchMethod.ROOT -> tryRootRestart()
            AxeronSettings.LaunchMethod.ADB -> tryAdbRestart()
        }

        return Result.success()
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
        // Shared with the liveness probe on purpose - see AdbStarter.resolvePort(). This used to
        // only consider getTcpPort(), which is 0 unless TCP mode is enabled, so in the default
        // mDNS pairing the worker logged "no TCP port configured" and never restarted anything.
        val port = AdbStarter.resolvePort()
        if (port <= 0) {
            Log.w(TAG, "Auto-restart via ADB: no usable port")
            return
        }

        runCatching {
            AdbStarter.startAdbClient(applicationContext, port) {}
            Log.i(TAG, "Auto-restart via ADB: command sent to port $port")
        }.onFailure {
            Log.e(TAG, "Auto-restart via ADB failed", it)
        }
    }
}
