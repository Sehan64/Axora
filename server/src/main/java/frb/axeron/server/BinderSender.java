package frb.axeron.server;

import static android.app.ActivityManagerHidden.UID_OBSERVER_ACTIVE;
import static android.app.ActivityManagerHidden.UID_OBSERVER_CACHED;
import static android.app.ActivityManagerHidden.UID_OBSERVER_GONE;
import static android.app.ActivityManagerHidden.UID_OBSERVER_IDLE;

import android.app.ActivityManagerHidden;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import frb.axeron.server.util.Logger;
import kotlin.collections.ArraysKt;
import moe.shizuku.server.IShizukuService;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.hidden.compat.adapter.ProcessObserverAdapter;
import rikka.hidden.compat.adapter.UidObserverAdapter;

public class BinderSender {

    private static final Logger LOGGER = new Logger("BinderSender");

    private static final String PERMISSION_MANAGER = "frb.axeron.manager.permission.MANAGER";

    private static final String SHIZUKU_PERMISSION_MANAGER = "moe.shizuku.manager.permission.MANAGER";

    private static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";

    private static IAxeronService axeronService;
    private static boolean registered = false;
    private static BroadcastReceiver binderRequestReceiver;

    private static final Map<Integer, List<String>> UID_PACKAGE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SENT_BINDERS = ConcurrentHashMap.newKeySet();

    private static List<String> getPackagesCached(int uid) {
        return UID_PACKAGE_CACHE.computeIfAbsent(uid, PackageManagerApis::getPackagesForUidNoThrow);
    }

    /**
     * Allow this uid/pid combination to be served again.
     *
     * The "already sent" set is a de-duplication cache, not a record of permanently handled
     * uids. Without pruning it turned into exactly that: the manager app was served the binder
     * on its first launch and never again, because a force-stop restarts the app process while
     * keeping the same uid. {@code onProcessDied} is the reliable hook - it is a per-process
     * callback, so it fires even on builds that skip uid-level notifications.
     */
    private static void pruneSentBinders(int uid, int pid) {
        SENT_BINDERS.remove(uid + ":" + pid);
        // The uid-level observers always use pid -1, so that key has to go too. Without this,
        // relaunching the app would hit the duplicate check and never be served.
        SENT_BINDERS.remove(uid + ":-1");
    }

    /** Drop every cached send for a uid that no longer has any processes. */
    private static void pruneSentBindersForUid(int uid) {
        String prefix = uid + ":";
        SENT_BINDERS.removeIf(key -> key.startsWith(prefix));
    }

    private static void sendBinder(int uid, int pid) throws RemoteException {
        String key = uid + ":" + pid;
        // Cheap duplicate check only - deliberately NOT inserting here.
        //
        // This used to be `if (!SENT_BINDERS.add(key)) return;`, which marked the uid as
        // "already served" *before* we knew whether a send could even happen: the code below
        // still bailed out on an empty package list, a missing Shizuku service, or a denied
        // permission. Worse, the key was never removed, so the entry survived for the lifetime
        // of the server process. Consequence: the manager app got the binder exactly once, and
        // every later launch - notably after a force-stop, which restarts the app process but
        // keeps the same uid - was silently suppressed. The app then sat at "Need to Activate"
        // with a perfectly healthy server holding its binder. Mark the key only once we have
        // actually dispatched, and prune it when the process/uid goes away.
        if (SENT_BINDERS.contains(key)) {
            LOGGER.v("Skip duplicate binder send for %s", key);
            return;
        }

        int userId = uid / 100000;

        List<String> packages = getPackagesCached(uid);
        if (packages.isEmpty())
            return;

        boolean isOurManager = packages.contains(ServerConstants.MANAGER_APPLICATION_ID);

        // 🔒 The manager app is the Axeron UI and always speaks IAxeronService - it obtains
        // the Shizuku service *through* that binder (IAxeronService.getShizukuService()).
        // It must therefore always be sent the AxeronService binder, never the Shizuku
        // intercept binder.
        //
        // This used to be gated on `shizukuService == null`, so once the Shizuku intercept
        // (AX-Scope / ax_perm_companion) was enabled the manager fell through to the generic
        // client loop below and was handed shizukuService.asBinder() instead. The manager then
        // treated a Shizuku binder as IAxeronService, every transaction failed with
        // SecurityException("Binder invocation to an incorrect interface"), getAxeronInfo()
        // fell back to its placeholder, and the Home screen sat on "Need to Activate" while
        // the server was perfectly healthy. It also clobbered a working binder, because a
        // later push replaces whatever the app already held.
        if (isOurManager) {
            LOGGER.d("uid %d belongs to manager, send axeron binder directly", uid);
            if (AxeronService.sendBinderToManager(axeronService.asBinder(), userId)) {
                SENT_BINDERS.add(key);
            }
            return;
        }

        IShizukuService shizukuService = axeronService.getShizukuService();

        // Non-manager app and no Shizuku service → nothing to hand out.
        if (shizukuService == null)
            return;

        LOGGER.d(
                "sendBinder to uid %d: packages=%s",
                uid,
                TextUtils.join(", ", packages)
        );

        for (String packageName : packages) {
            try {
                PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(
                        packageName,
                        PackageManager.GET_PERMISSIONS,
                        userId
                );
                if (pi == null || pi.requestedPermissions == null)
                    continue;

                if (ArraysKt.contains(pi.requestedPermissions, PERMISSION_MANAGER)) {
                    boolean granted = pid == -1
                            ? PermissionManagerApis.checkPermission(PERMISSION_MANAGER, uid)
                            == PackageManager.PERMISSION_GRANTED
                            : ActivityManagerApis.checkPermission(PERMISSION_MANAGER, pid, uid)
                            == PackageManager.PERMISSION_GRANTED;

                    if (granted) {
                        if (AxeronService.sendBinderToUserApp(
                                shizukuService.asBinder(),
                                packageName,
                                userId
                        )) {
                            SENT_BINDERS.add(key);
                        }
                        return;
                    }

                } else if (ArraysKt.contains(pi.requestedPermissions, SHIZUKU_PERMISSION_MANAGER)) {
                    boolean granted = pid == -1
                            ? PermissionManagerApis.checkPermission(SHIZUKU_PERMISSION_MANAGER, uid)
                            == PackageManager.PERMISSION_GRANTED
                            : ActivityManagerApis.checkPermission(SHIZUKU_PERMISSION_MANAGER, pid, uid)
                            == PackageManager.PERMISSION_GRANTED;

                    if (granted) {
                        if (AxeronService.sendBinderToUserApp(
                                shizukuService.asBinder(),
                                ServerConstants.SHIZUKU_MANAGER_APPLICATION_ID,
                                userId
                        )) {
                            SENT_BINDERS.add(key);
                        }
                        return;
                    }

                } else if (ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    AxeronService.sendBinderToUserApp(
                            shizukuService.asBinder(),
                            packageName,
                            userId
                    );
                    return;
                }

            } catch (Throwable e) {
                LOGGER.w(e, "sendBinder failed for package %s", packageName);
            }
        }
    }


//    private static void sendBinder(int uid, int pid) {
//        String key = uid + ":" + pid;
//        if (!SENT_BINDERS.add(key)) {
//            LOGGER.v("Skip duplicate binder send for %s", key);
//            return;
//        }
//
//        List<String> packages = getPackagesCached(uid);
//        if (packages.isEmpty())
//            return;
//
//        LOGGER.d("sendBinder to uid %d: packages=%s", uid, TextUtils.join(", ", packages));
//
//        int userId = uid / 100000;
//        for (String packageName : packages) {
//            try {
//                PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, PackageManager.GET_PERMISSIONS, userId);
//                if (pi == null || pi.requestedPermissions == null)
//                    continue;
//
//                if (ArraysKt.contains(pi.requestedPermissions, PERMISSION_MANAGER)) {
//                    boolean granted;
//                    if (pid == -1)
//                        granted = PermissionManagerApis.checkPermission(PERMISSION_MANAGER, uid) == PackageManager.PERMISSION_GRANTED;
//                    else
//                        granted = ActivityManagerApis.checkPermission(PERMISSION_MANAGER, pid, uid) == PackageManager.PERMISSION_GRANTED;
//
//                    if (granted) {
//                        AxeronService.sendBinderToManager(axeronService.asBinder(), userId);
//                        return;
//                    }
//                } else if (ArraysKt.contains(pi.requestedPermissions, SHIZUKU_PERMISSION_MANAGER)) {
//                    boolean granted;
//                    if (pid == -1)
//                        granted = PermissionManagerApis.checkPermission(SHIZUKU_PERMISSION_MANAGER, uid) == PackageManager.PERMISSION_GRANTED;
//                    else
//                        granted = ActivityManagerApis.checkPermission(SHIZUKU_PERMISSION_MANAGER, pid, uid) == PackageManager.PERMISSION_GRANTED;
//
//                    if (granted) {
//                        IShizukuService shizukuService = axeronService.getShizukuService();
//                        if (shizukuService != null)
//                            AxeronService.sendBinderToShizukuManager(shizukuService.asBinder(), userId);
//                        return;
//                    }
//                } else if (ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
//                    IShizukuService shizukuService = axeronService.getShizukuService();
//                    if (shizukuService != null) {
//                        AxeronService.sendBinderToUserApp(shizukuService.asBinder(), packageName, userId);
//                    }
//                    return;
//                }
//            } catch (Throwable e) {
//                LOGGER.w(e, "sendBinder failed for package %s", packageName);
//            }
//        }
//    }

    public static void register(IAxeronService service) {
        if (registered) return;
        registered = true;
        axeronService = service;

        try {
            ActivityManagerApis.registerProcessObserver(new ProcessObserver());
        } catch (Throwable tr) {
            LOGGER.e(tr, "registerProcessObserver");
        }

        int flags = UID_OBSERVER_GONE | UID_OBSERVER_IDLE | UID_OBSERVER_ACTIVE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            flags |= UID_OBSERVER_CACHED;
        }
        try {
            ActivityManagerApis.registerUidObserver(new UidObserver(), flags,
                    ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
                    null);
        } catch (Throwable tr) {
            LOGGER.e(tr, "registerUidObserver");
        }
    }

    private static class ProcessObserver extends ProcessObserverAdapter {

        private static final Set<Integer> PID_LIST = new HashSet<>();

        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) throws RemoteException {
            LOGGER.d("onForegroundActivitiesChanged: pid=%d, uid=%d, foregroundActivities=%s", pid, uid, foregroundActivities ? "true" : "false");

            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid) || !foregroundActivities) {
                    return;
                }
                PID_LIST.add(pid);
            }

            sendBinder(uid, pid);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            LOGGER.d("onProcessDied: pid=%d, uid=%d", pid, uid);

            synchronized (PID_LIST) {
                PID_LIST.remove(pid);
            }

            pruneSentBinders(uid, pid);
        }

        @Override
        public void onProcessStateChanged(int pid, int uid, int procState) throws RemoteException {
            LOGGER.d("onProcessStateChanged: pid=%d, uid=%d, procState=%d", pid, uid, procState);

            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid)) {
                    return;
                }
                PID_LIST.add(pid);
            }

            sendBinder(uid, pid);
        }
    }

    /**
     * Lets an app ask us to hand over the binder on demand.
     *
     * The observer-driven push above is best-effort: it depends on the platform delivering
     * {@code onUidActive} / {@code onProcessStateChanged} after a force-stop, and some builds
     * do not. This is the deterministic fallback - the manager broadcasts
     * {@code AxRuntime.REQUEST_BINDER} with a binder of its own in the {@code data} extra, and
     * we transact straight back into it.
     *
     * Note this is deliberately not {@code ShellBinderRequestHandler}: that one replies with
     * {@code Axeron.getBinder()}, which is only meaningful inside the manager process (where the
     * manager brokers a binder it already holds). Inside the server process the binder we own
     * is {@code axeronService} itself.
     */
    public static void registerBinderRequestReceiver(Context context, IAxeronService service) {
        if (binderRequestReceiver != null) return;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!ServerConstants.REQUEST_BINDER_DIRECT.equals(intent.getAction())) {
                    return;
                }

                IBinder target = intent.getBundleExtra("data") == null
                        ? null
                        : requireDataBinder(intent);

                if (target == null) {
                    LOGGER.w("binder request without a reply target");
                    return;
                }

                Parcel data = Parcel.obtain();
                try {
                    data.writeStrongBinder(service.asBinder());
                    data.writeString(ctx.getApplicationInfo().nativeLibraryDir);
                    target.transact(1, data, null, IBinder.FLAG_ONEWAY);
                    LOGGER.d("answered a binder request");
                } catch (Throwable tr) {
                    LOGGER.w(tr, "failed to answer binder request");
                } finally {
                    data.recycle();
                }
            }
        };

        IntentFilter filter = new IntentFilter(ServerConstants.REQUEST_BINDER_DIRECT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The request comes from the manager, a different package, so this has to be
                // exported for the broadcast to arrive at all.
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            binderRequestReceiver = receiver;
            LOGGER.d("registered binder request receiver");
        } catch (Throwable tr) {
            LOGGER.w(tr, "registerReceiver failed");
        }
    }

    private static IBinder requireDataBinder(Intent intent) {
        return intent.getBundleExtra("data").getBinder("binder");
    }

    private static class UidObserver extends UidObserverAdapter {

        private static final Set<Integer> UID_LIST = new HashSet<>();

        @Override
        public void onUidActive(int uid) throws RemoteException {
            LOGGER.d("onUidCachedChanged: uid=%d", uid);

            uidStarts(uid);
        }

        @Override
        public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {
            LOGGER.d("onUidCachedChanged: uid=%d, cached=%s", uid, Boolean.toString(cached));

            if (!cached) {
                uidStarts(uid);
            }
        }

        @Override
        public void onUidIdle(int uid, boolean disabled) throws RemoteException {
            LOGGER.d("onUidIdle: uid=%d, disabled=%s", uid, Boolean.toString(disabled));

            uidStarts(uid);
        }

        @Override
        public void onUidGone(int uid, boolean disabled) {
            LOGGER.d("onUidGone: uid=%d, disabled=%s", uid, Boolean.toString(disabled));

            uidGone(uid);
        }

        private void uidStarts(int uid) throws RemoteException {
            synchronized (UID_LIST) {
                if (UID_LIST.contains(uid)) {
                    LOGGER.v("Uid %d already starts", uid);
                    return;
                }
                UID_LIST.add(uid);
                LOGGER.v("Uid %d starts", uid);
            }

            sendBinder(uid, -1);
        }

        private void uidGone(int uid) {
            synchronized (UID_LIST) {
                UID_LIST.remove(uid);
            }
            LOGGER.v("Uid %d dead", uid);

            pruneSentBindersForUid(uid);
        }
    }
}