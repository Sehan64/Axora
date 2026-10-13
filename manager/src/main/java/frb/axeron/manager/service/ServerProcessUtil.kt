package frb.axeron.manager.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.topjohnwu.superuser.Shell
import frb.axeron.adb.AdbClient
import frb.axeron.adb.AdbKey
import frb.axeron.adb.PreferenceAdbKeyStore
import frb.axeron.api.core.AxeronSettings
import frb.axeron.api.core.Starter
import frb.axeron.manager.adb.AdbStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object ServerProcessUtil {

    private const val TAG = "ServerProcessUtil"

    /**
     * The real process name of the detached server.
     *
     * Kept in lockstep with the two places that actually decide this:
     *  - `server/src/main/cpp/starter.cpp` (ARGV0 / the name it spawns and kills)
     *  - `server/src/main/java/frb/axeron/server/AxeronService.kt`
     *
     * It used to be misspelled as "axon_server" here. Because every liveness check was gated on
     * that constant, the typo meant we could never find a running server, which in turn made
     * callers treat a perfectly healthy server as dead and restart it - replacing a live
     * process, losing its uptime, and handing the user a new PID.
     */
    const val SERVER_PROCESS_NAME = "axeron_server"

    /**
     * True only when the server process is *positively confirmed* gone.
     *
     * This is deliberately inverted and fail-safe. The old `isServerProcessAlive()` returned
     * `false` for every non-root launch mode, so an ADB-launched server - which runs happily
     * detached after a force-stop - was always reported dead and restarted.
     *
     * The contract for every caller: `true` means "we checked and it is gone, you may start a
     * new one". Every inconclusive outcome - no shell, shell died, `pidof` missing, the job
     * never ran, an unparsable exit code - returns `false`, meaning "assume it is alive and
     * leave it alone". A needless restart destroys a working server and its uptime, so it is
     * always the worse mistake.
     */
    fun isServerDefinitelyDead(): Boolean {
        // Use cached shell only; never block creating one.
        val shell = Shell.getCachedShell() ?: return false
        if (!shell.isAlive) return false
        return runCatching {
            probeViaPidof() ?: probeViaProcessList() ?: false
        }.getOrDefault(false)
    }

    private val shellRequestInFlight = AtomicBoolean(false)

    /**
     * ADB-mode equivalent of [isServerDefinitelyDead].
     *
     * The shell-based probe above is useless in ADB mode: there is no root and no su binary, so
     * `Shell.getCachedShell()` is always null and the probe could only ever answer "unproven" -
     * which would make auto-restart permanently impossible for exactly the launch mode that needs
     * it most. The ADB connection we already own can run the same commands as the `shell` user,
     * so use that instead.
     *
     * Same contract: `true` means positively proven gone; every inconclusive outcome (cannot
     * resolve a port, cannot connect, blank output, unrecognisable listing) returns `false`.
     */
    suspend fun isServerDefinitelyDeadOverAdb(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Shared with the restart command on purpose - see AdbStarter.resolvePort().
        val port = AdbStarter.resolvePort()
        if (port <= 0) {
            Log.w(TAG, "ADB liveness: no usable port, unproven")
            return@withContext false
        }

        val key = runCatching {
            val keyStore = PreferenceAdbKeyStore(
                AxeronSettings.getPreferences(),
                Settings.Global.getString(context.contentResolver, Starter.KEY_PAIR)
            )
            AdbKey(keyStore, "axeron")
        }.getOrNull()
        if (key == null) {
            Log.w(TAG, "ADB liveness: could not load key, unproven")
            return@withContext false
        }

        // Cheap probe first. Note we cannot read the exit status over the ADB stream, so an empty
        // `pidof` result is ambiguous ("not found" vs "no such command") and must not be read as
        // proof of death - hence the fall through to the process listing.
        if (mentionsServer(adbShell(key, port, "pidof $SERVER_PROCESS_NAME"))) {
            return@withContext false
        }

        // Process-listing fallbacks, most specific first. `true` means the command produced a
        // real listing (many rows) that simply did not mention the server; a rejected option
        // prints a single error line, so row count is what separates "it ran" from "it failed".
        for (cmd in listOf("ps -A -o NAME=", "ps -A", "ps")) {
            val output = adbShell(key, port, cmd) ?: continue
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (mentionsServer(lines)) {
                Log.i(TAG, "ADB liveness: server found via '$cmd', alive")
                return@withContext false
            }
            if (lines.size > 1) {
                Log.i(TAG, "ADB liveness: listing via '$cmd' had no server, dead")
                return@withContext true
            }
        }

        Log.w(TAG, "ADB liveness: no conclusive result, unproven")
        false
    }

    /** Runs one command over the ADB channel, or null if the command could not be run. */
    private fun adbShell(key: AdbKey, port: Int, cmd: String): String? = runCatching {
        AdbClient(key, port).use { client ->
            client.connect()
            val out = StringBuilder()
            client.shellCommand(cmd) { chunk -> out.append(String(chunk)) }
            out.toString()
        }
    }.getOrNull()

    /**
     * Kicks off a non-blocking request for a shell.
     *
     * Liveness checking needs a shell, but [Shell.getShell] blocks until one is available,
     * which hangs the caller indefinitely when no root or ADB shell can be established. This
     * uses the callback form instead so the caller returns immediately; once the shell lands it
     * is cached, and the next [isServerDefinitelyDead] call can use it.
     *
     * Guarded by an atomic so a tight retry loop cannot queue up duplicate shell requests.
     */
    fun requestShellAsync() {
        if (!shellRequestInFlight.compareAndSet(false, true)) return
        runCatching {
            Shell.getShell { shell ->
                shellRequestInFlight.set(false)
                if (shell == null) {
                    Log.w(TAG, "Shell request failed; will retry on next attempt")
                }
            }
        }.onFailure {
            shellRequestInFlight.set(false)
            Log.w(TAG, "Shell request threw; will retry on next attempt", it)
        }
    }

    /**
     * `pidof` probe. Returns null when the result is inconclusive, so the caller can try
     * another strategy instead of mistaking uncertainty for death.
     */
    private fun probeViaPidof(): Boolean? {
        val result = runCatching {
            Shell.cmd("pidof $SERVER_PROCESS_NAME").exec()
        }.getOrNull() ?: return null

        if (mentionsServer(result.out)) return false

        // toybox `pidof` exits 0 when it finds the process and 1 when it finds nothing.
        // 127 (no such command), 126 (not executable) and Shell.Result.JOB_NOT_EXECUTED
        // (-1) all mean "the probe did not work", which is NOT evidence of death.
        return when (result.code) {
            0 -> false // claims success but printed nothing - trust it, do not restart
            1 -> true // ran correctly, no such process: genuinely gone
            else -> null
        }
    }

    /**
     * `ps` fallback, for devices whose toybox build has no usable `pidof`.
     */
    private fun probeViaProcessList(): Boolean? {
        val result = runCatching {
            Shell.cmd("ps", "-A", "-o", "NAME=").exec()
        }.getOrNull() ?: return null

        if (mentionsServer(result.out)) return false

        // `ps` exits 0 whether or not anything matched, so a clean run with no match is the
        // only trustworthy "dead" signal. A non-zero code means the column was rejected
        // (older Android `ps` builds), leaving us with nothing to go on.
        return if (result.code == 0) true else null
    }

    /**
     * Whether any output line refers to the server.
     *
     * Matching is intentionally loose. `pidof` prints bare PIDs, `ps` may print a full path or
     * an argv fragment, and some Android builds truncate the comm column - so a short prefix
     * is treated as a *hit*, never as a miss. Erring toward "alive" is the safe direction.
     */
    private fun mentionsServer(lines: List<String>): Boolean {
        val name = SERVER_PROCESS_NAME
        return lines.any { raw ->
            val token = raw.trim()
            token.isNotEmpty() && (
                token == name ||
                    token.split('/').any { it == name } ||
                    // Truncated comm column: a genuine prefix still proves presence.
                    (token.length in 4 until name.length && name.startsWith(token))
                )
        }
    }

    /** Same test for a single blob of output, as returned by the ADB channel. */
    private fun mentionsServer(output: String?): Boolean =
        output != null && mentionsServer(output.lineSequence().toList())
}
