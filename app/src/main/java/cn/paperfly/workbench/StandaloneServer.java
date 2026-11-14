package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

/** Loaded from this APK with app_process by the user's authorized local ADB session, never by the app UID. */
@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public final class StandaloneServer {
    private static final String APP = "cn.paperfly.workbench";
    public static void main(String[] args) {
        try {
            if (android.os.Process.myUid() != 2000 && android.os.Process.myUid() != 0)
                throw new SecurityException("Start this helper from an authorized ADB shell");
            // Leave the bootstrap shell's process group before Android starts any worker threads.
            // This changes no UID or permissions; it prevents terminal/session cleanup reaching us.
            try { android.system.Os.setsid(); }
            catch(android.system.ErrnoException alreadyLeader) { System.out.println("Session detach: "+alreadyLeader.errno); }
            android.os.Process.class.getMethod("setArgV0", String.class)
                    .invoke(null, "paperfly_workbench_server");
            Looper.prepareMainLooper();
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object activityThread = thread.getMethod("systemMain").invoke(null);
            Context system = (Context) thread.getMethod("getSystemContext").invoke(activityThread);
            Context shell = system.createPackageContext("com.android.shell", 0);
            ServerValidation.attach(shell);
            PackageInfo initial = shell.getPackageManager().getPackageInfo(APP, 0);
            ApplicationInfo info = initial.applicationInfo;
            WorkbenchService service = new WorkbenchService(info.uid, shell);
            service.recoverLocal();
            System.out.println("OpenWorkbench " + BuildConfig.VERSION_NAME + " helper UID=" + android.os.Process.myUid());
            Thread watcher = new Thread(() -> watch(shell, service, info.uid, initial.lastUpdateTime), "workbench-client-watch");
            watcher.start();
            Runtime.getRuntime().addShutdownHook(new Thread(service::cleanupLocal));
            Looper.loop();
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void watch(Context context, WorkbenchService service, int ownerUid, long installedAt) {
        int deliveredPid = -1;
        while (true) {
            try {
                PackageInfo pkg = context.getPackageManager().getPackageInfo(APP, 0);
                if (pkg.getLongVersionCode() != BuildConfig.VERSION_CODE || pkg.applicationInfo.uid != ownerUid
                        || pkg.lastUpdateTime != installedAt) {
                    service.cleanupLocal();
                    System.out.println("APK updated; activation is required for the new version.");
                    System.exit(0);
                }
                int[] pids = (int[]) android.os.Process.class.getMethod("getPidsForCommands", String[].class)
                        .invoke(null, (Object) new String[]{APP});
                int pid = pids != null && pids.length == 1 ? pids[0] : -1;
                if (pid < 0) {
                    if (deliveredPid >= 0) service.cleanupLocal();
                    deliveredPid = -1;
                } else if (pid != deliveredPid && (pkg.applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    Bundle extras = new Bundle();
                    extras.putInt("version", BuildConfig.VERSION_CODE);
                    extras.putBinder("service", service.asBinder());
                    Bundle response = deliver(extras);
                    if (response != null && response.getBoolean("attached")) {
                        deliveredPid = pid;
                        System.out.println("Attached to app process " + pid);
                    }
                }
            } catch (Throwable error) {
                System.err.println("Waiting for app: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            try { Thread.sleep(1000); }
            catch (InterruptedException stopped) { return; }
        }
    }

    /** Same external-provider acquisition as Android's shell `content` command. */
    private static Bundle deliver(Bundle extras) throws Exception {
        String authority = APP + ".bridge";
        Object manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        android.os.IBinder token = new android.os.Binder();
        int user = android.os.Process.myUid() / 100000;
        Object holder = manager.getClass().getMethod("getContentProviderExternal", String.class,
                int.class, android.os.IBinder.class, String.class)
                .invoke(manager, authority, user, token, "workbench-activation");
        if (holder == null) return null;
        try {
            Object provider = holder.getClass().getField("provider").get(holder);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.content.AttributionSource source = new android.content.AttributionSource.Builder(
                        android.os.Process.myUid()).setPackageName("com.android.shell").build();
                return (Bundle) provider.getClass().getMethod("call", android.content.AttributionSource.class,
                        String.class, String.class, String.class, Bundle.class)
                        .invoke(provider, source, authority, "attach", null, extras);
            }
            return (Bundle) provider.getClass().getMethod("call", String.class, String.class,
                    String.class, String.class, String.class, Bundle.class)
                    .invoke(provider, "com.android.shell", null, authority, "attach", null, extras);
        } finally {
            manager.getClass().getMethod("removeContentProviderExternalAsUser", String.class,
                    android.os.IBinder.class, int.class).invoke(manager, authority, token, user);
        }
    }
}
