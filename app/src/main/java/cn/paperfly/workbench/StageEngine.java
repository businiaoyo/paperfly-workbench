package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Called only after WorkbenchService checks the app UID. All task APIs retain system checks. */
@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
final class StageEngine {
    private final TransitionScale transitionScale = new TransitionScale();
    private String[] selection;
    private int active = -1;
    private int host = -1;
    private int user = -1;
    private Root[] visited;

    private int[] requestedBounds;
    private final java.util.Map<Integer, Rect> prepared = new java.util.HashMap<>();
    private static final java.io.File JOURNAL = new java.io.File("/data/local/tmp/paperfly-workbench-stage.json");

    Bundle switchTo(String[] targets, int index, int[] bounds, int hostTask, boolean stableSwitching) {
        Bundle result = new Bundle();
        long identity = Binder.clearCallingIdentity();
        long started = SystemClock.elapsedRealtime();
        try {
            requireShell();
            validate(targets, index, bounds);
            int currentUser = currentUser();
            Object manager = manager();
            if (selection == null || host != hostTask || user != currentUser || !Arrays.equals(selection, targets)) {
                cleanup();
                if (JOURNAL.exists()) throw new IllegalStateException("上次窗口未恢复，请重新激活后重试。");
                selection = targets.clone(); active = -1; host = hostTask; user = currentUser;
                visited = new Root[targets.length];
            }
            List<Root> roots = roots(manager, user);
            Root backdrop = byId(roots, hostTask);
            if (backdrop == null || !backdrop.component.getPackageName().equals("cn.paperfly.workbench"))
                throw new IllegalStateException("工作台页面已不在当前显示器，请重新进入。");
            if (stableSwitching) transitionScale.begin();
            Root previous = active >= 0 ? revalidate(roots, visited[active]) : null;
            if (previous != null && active != index) {
                byte[] thumb = snapshot(manager, previous);
                if (thumb != null) { result.putInt("snapshotIndex", active); result.putByteArray("snapshot", thumb); }
            }
            Root target = match(roots, targets[index]);
            boolean coldLaunch = target == null;
            if (coldLaunch) {
                WorkbenchService.CommandResult launch = WorkbenchService.run("/system/bin/am", "start",
                        "-W", "--user", Integer.toString(user), "--display", "0", "--windowingMode", "5",
                        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                        "-n", targets[index], "-f", "0x10000000");
                if (!launch.ok()) throw new IllegalStateException(launch.describe());
                for (int attempt = 0; attempt < 8; attempt++) {
                    roots = roots(manager, user); target = match(roots, targets[index]);
                    if (target != null) break;
                    SystemClock.sleep(100);
                }
                if (target == null) throw new IllegalStateException("应用任务尚未就绪，或属于共享任务组。");
            }
            roots = roots(manager, user);
            target = revalidate(roots, target); backdrop = revalidate(roots, backdrop);
            if (target == null || backdrop == null) throw new IllegalStateException("任务已变化，请重试。");
            if (!Arrays.equals(requestedBounds, bounds)) { prepared.clear(); requestedBounds = bounds.clone(); }
            boolean needsPlacement = target.mode != 5 || !target.bounds.equals(prepared.get(target.id));
            visited[index] = target;
            // Record exact task/user/component identities before changing managed windows.
            saveJournal();
            if (needsPlacement) {
                Transaction prepare = new Transaction();
                prepare.hidden(target, false);
                prepare.front(backdrop);
                prepare.place(target, bounds);
                prepare.front(target);
                prepare.apply();
                // Honor initializes its caption/scale only when the new window is visible.
                // This placement is only for first use or explicit bounds recovery; warm switches never resize.
                SystemClock.sleep(300);
                WorkbenchService.CommandResult resize = WorkbenchService.run("/system/bin/am", "task",
                        "resize", Integer.toString(target.id), Integer.toString(bounds[0]),
                        Integer.toString(bounds[1]), Integer.toString(bounds[2]), Integer.toString(bounds[3]));
                if (!resize.ok()) throw new IllegalStateException(resize.describe());
                SystemClock.sleep(200);
            }
            roots = roots(manager, user);
            target = revalidate(roots, target); backdrop = revalidate(roots, backdrop);
            if (target == null || backdrop == null) throw new IllegalStateException("应用任务已关闭，请重试。");
            Transaction show = new Transaction();
            // Keep prepared tasks at the same bounds underneath the primary. Force-hiding
            // each departing task makes Honor remove/recreate its caption and animate its
            // contents on every return. Normal task occlusion preserves that layout.
            show.hidden(target, false);
            show.front(target);
            show.apply();
            // Reordering alone does not restore Honor's minimized surface. Resume the
            // existing task through the normal recents API, keeping its size and contents.
            android.content.Context context = (android.content.Context) Class.forName("android.app.ActivityThread")
                    .getMethod("getSystemContext").invoke(Class.forName("android.app.ActivityThread")
                            .getMethod("currentActivityThread").invoke(null));
            android.app.ActivityOptions options = (android.app.ActivityOptions) android.app.ActivityOptions.class
                    .getMethod("makeCustomTaskAnimation", android.content.Context.class, int.class, int.class,
                            android.os.Handler.class, Class.forName("android.app.ActivityOptions$OnAnimationStartedListener"),
                            Class.forName("android.app.ActivityOptions$OnAnimationFinishedListener"))
                    .invoke(null, context.createPackageContext("com.android.shell", 0), 0, 0, null, null, null);
            manager.getClass().getMethod("startActivityFromRecents", int.class, Bundle.class)
                    .invoke(manager, target.id, options.toBundle());
            Root actual = null;
            for (int attempt = 0; attempt < 10; attempt++) {
                actual = revalidate(roots(manager, user), target);
                if (actual != null && actual.mode == 5 && actual.visible) break;
                SystemClock.sleep(50);
            }
            if (actual == null || actual.mode != 5 || !actual.visible)
                throw new IllegalStateException("系统未确认主窗口可见，可点应用卡片重试。");
            prepared.put(target.id, new Rect(actual.bounds));
            active = index;
            result.putBoolean("success", true);
            result.putInt("active", active);
            result.putInt("taskId", target.id);
            result.putString("detail", "主窗口：" + target.component.flattenToShortString()
                    + "\n任务 " + target.id + " · freeform · 可见\n"
                    + (needsPlacement ? "首次整理窗口" : "保留尺寸切换")
                    + (coldLaunch ? " · 新建任务" : " · 复用任务"));
        } catch (Exception | LinkageError error) {
            result.putString("detail", message(error));
            cleanup(); // Never leave an invisible app stranded after a partial transaction.
        } finally {
            if (stableSwitching) {
                SystemClock.sleep(250);
                try { transitionScale.restore(); }
                catch (Exception error) {
                    result.putBoolean("success", false);
                    result.putString("detail", "动画倍率恢复失败，请重新激活：" + message(error));
                }
            }
            Binder.restoreCallingIdentity(identity);
        }
        if (result.getBoolean("success")) result.putString("detail", result.getString("detail")
                + "\n切换耗时 " + (SystemClock.elapsedRealtime() - started) + " ms");
        return result;
    }

    String end(int hostTask, boolean returnToHost) {
        if (hostTask != host) return "工作台已退出。";
        return restore(returnToHost);
    }
    void cleanup() { restore(false); }
    void recover() {
        long identity = Binder.clearCallingIdentity();
        try {
            requireShell();
            transitionScale.restore();
            if (JOURNAL.exists() && visited == null) {
                org.json.JSONObject data = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(JOURNAL.toPath()), java.nio.charset.StandardCharsets.UTF_8));
                if (!bootId().equals(data.getString("boot"))) {
                    java.nio.file.Files.delete(JOURNAL.toPath()); return;
                }
                user = data.getInt("user");
                org.json.JSONArray tasks = data.getJSONArray("tasks");
                if (tasks.length() > 5) throw new IllegalStateException("Invalid stage recovery journal");
                visited = new Root[tasks.length()];
                for (int i = 0; i < tasks.length(); i++) {
                    org.json.JSONObject task = tasks.getJSONObject(i);
                    ComponentName component = ComponentName.unflattenFromString(task.getString("component"));
                    if (component == null) throw new IllegalStateException("Invalid task identity");
                    visited[i] = new Root(task.getInt("id"), component, null, 5, false, new Rect());
                }
            }
            String report = restore(false);
            if (JOURNAL.exists()) throw new IllegalStateException(report);
        } catch (Exception error) { throw new IllegalStateException("窗口恢复失败：" + message(error), error); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    private String restore(boolean returnToHost) {
        long identity = Binder.clearCallingIdentity();
        try {
            requireShell();
            transitionScale.restore();
            if (visited == null) return "工作台已退出。";
            // roots() rechecks the recorded user, default display, isolated task and full component.
            List<Root> roots = roots(manager(), user);
            Transaction transaction = new Transaction();
            for (Root known : visited) {
                Root actual = revalidate(roots, known);
                if (actual != null) transaction.park(actual);
            }
            Root backdrop = byId(roots, host);
            if (returnToHost && currentUser() == user && backdrop != null
                    && backdrop.component.getPackageName().equals("cn.paperfly.workbench")) transaction.front(backdrop);
            transaction.apply();
            java.nio.file.Files.deleteIfExists(JOURNAL.toPath());
            selection = null; visited = null; active = -1; prepared.clear(); requestedBounds = null;
            return "已退出，应用保留在系统后台。";
        } catch (Exception | LinkageError error) {
            return "窗口恢复未完成，请重新激活：" + message(error);
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    private static int currentUser() {
        return Integer.parseInt(WorkbenchService.run("/system/bin/am", "get-current-user").output.trim());
    }
    private static String bootId() throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/sys/kernel/random/boot_id")), java.nio.charset.StandardCharsets.UTF_8).trim();
    }
    private void saveJournal() throws Exception {
        org.json.JSONArray tasks = new org.json.JSONArray();
        for (Root root : visited) if (root != null) tasks.put(new org.json.JSONObject()
                .put("id", root.id).put("component", root.component.flattenToString()));
        byte[] bytes = new org.json.JSONObject().put("boot", bootId()).put("user", user)
                .put("tasks", tasks).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.File temp = new java.io.File(JOURNAL.getPath() + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
            android.system.Os.chmod(temp.getPath(), 0600);
            out.write(bytes); out.getFD().sync();
        }
        android.system.Os.rename(temp.getPath(), JOURNAL.getPath());
    }

    private static void validate(String[] targets, int index, int[] bounds) {
        if (targets == null || targets.length < 1 || targets.length > 5
                || index < 0 || index >= targets.length)
            throw new IllegalArgumentException("请选择 1–5 个应用。");
        Set<String> packages = new HashSet<>();
        for (String target : targets) {
            String invalid = WorkbenchService.validate(new String[]{target}, bounds);
            if (invalid != null) throw new IllegalArgumentException(invalid);
            ComponentName name = ComponentName.unflattenFromString(target);
            if (name == null || name.getPackageName().equals("cn.paperfly.workbench")
                    || !packages.add(name.getPackageName()))
                throw new IllegalArgumentException("每个应用只能选择一次。");
        }
    }

    private static void requireShell() {
        if (android.os.Process.myUid() != 2000 && android.os.Process.myUid() != 0)
            throw new SecurityException("请先完成无线或 USB 激活。");
    }

    private static Object manager() throws ReflectiveOperationException {
        return Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
    }

    private static List<Root> roots(Object manager, int user) throws ReflectiveOperationException {
        List<?> infos = (List<?>) manager.getClass().getMethod("getAllRootTaskInfos").invoke(manager);
        List<Root> result = new ArrayList<>();
        for (Object info : infos) {
            Class<?> type = info.getClass();
            int id = type.getField("taskId").getInt(info);
            Object config = type.getField("configuration").get(info);
            Object window = config.getClass().getField("windowConfiguration").get(config);
            int mode = (int) window.getClass().getMethod("getWindowingMode").invoke(window);
            if (!StageGeometry.isolated(id, id, type.getField("displayId").getInt(info),
                    type.getField("userId").getInt(info), user,
                    (int) window.getClass().getMethod("getActivityType").invoke(window), mode,
                    (int[]) type.getField("childTaskIds").get(info))) continue;
            ComponentName component = (ComponentName) type.getField("baseActivity").get(info);
            Object token = type.getField("token").get(info);
            if (component != null && token != null)
                result.add(new Root(id, component, token, mode, type.getField("visible").getBoolean(info),
                        new Rect((Rect) window.getClass().getMethod("getBounds").invoke(window))));
        }
        return result;
    }

    private static Root byId(List<Root> roots, int id) {
        for (Root root : roots) if (root.id == id) return root;
        return null;
    }

    private static Root revalidate(List<Root> roots, Root expected) {
        if (expected == null) return null;
        Root actual = byId(roots, expected.id);
        return actual != null && actual.component.equals(expected.component) ? actual : null;
    }

    private static Root match(List<Root> roots, String target) {
        ComponentName component = ComponentName.unflattenFromString(target);
        Root found = null;
        for (Root root : roots) {
            if (component == null || !root.component.getPackageName().equals(component.getPackageName())) continue;
            if (found != null) throw new IllegalStateException("同一应用有多个任务，请先在系统最近任务中整理。");
            found = root;
        }
        return found;
    }

    /** Only the departing, explicitly selected app. No screen-wide capture, disk writes or polling. */
    private static byte[] snapshot(Object manager, Root root) {
        Object snapshot = null;
        Bitmap hardware = null, software = null, small = null;
        try {
            if (!root.visible) return null;
            try {
                Class<?> api = Class.forName("android.window.TaskSnapshotManager");
                Object instance = api.getMethod("getInstance").invoke(null);
                snapshot = api.getMethod("takeTaskSnapshot", int.class, boolean.class)
                        .invoke(instance, root.id, true);
            } catch (ClassNotFoundException | NoSuchMethodException olderPlatform) {
                snapshot = manager.getClass().getMethod("takeTaskSnapshot", int.class, boolean.class)
                        .invoke(manager, root.id, true);
            }
            if (snapshot == null) return null;
            Class<?> type = snapshot.getClass();
            if (!(boolean) type.getMethod("isRealSnapshot").invoke(snapshot)) return null;
            try {
                if ((boolean) type.getMethod("hasProtectedContent").invoke(snapshot)) return null;
            } catch (NoSuchMethodException ignored) { }
            ComponentName top = (ComponentName) type.getMethod("getTopActivityComponent").invoke(snapshot);
            if (top == null || !top.getPackageName().equals(root.component.getPackageName())) return null;
            try {
                hardware = (Bitmap) type.getMethod("wrapToBitmap").invoke(snapshot);
            } catch (NoSuchMethodException olderPlatform) {
                HardwareBuffer buffer = (HardwareBuffer) type.getMethod("getHardwareBuffer").invoke(snapshot);
                if (buffer == null) return null;
                hardware = Bitmap.wrapHardwareBuffer(buffer,
                        (ColorSpace) type.getMethod("getColorSpace").invoke(snapshot));
            }
            if (hardware == null) return null;
            software = hardware.copy(Bitmap.Config.ARGB_8888, false);
            float scale = Math.min(1f, 420f / Math.max(software.getWidth(), software.getHeight()));
            small = Bitmap.createScaledBitmap(software, Math.max(1, Math.round(software.getWidth() * scale)),
                    Math.max(1, Math.round(software.getHeight() * scale)), true);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 82, encoded);
            return encoded.size() <= 200000 ? encoded.toByteArray() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null; // Icon card remains usable if a snapshot is protected or unavailable.
        } finally {
            if (small != null && small != software) small.recycle();
            if (software != null) software.recycle();
            if (hardware != null) hardware.recycle();
            if (snapshot != null) {
                try { snapshot.getClass().getMethod("closeBuffer").invoke(snapshot); }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
    }

    private static String message(Throwable error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static final class Root {
        final int id, mode;
        final ComponentName component;
        final Object token;
        final boolean visible;
        final Rect bounds;
        Root(int id, ComponentName component, Object token, int mode, boolean visible, Rect bounds) {
            this.id = id; this.component = component; this.token = token;
            this.mode = mode; this.visible = visible; this.bounds = bounds;
        }
    }

    private static final class Transaction {
        final Class<?> type = Class.forName("android.window.WindowContainerTransaction");
        final Class<?> token = Class.forName("android.window.WindowContainerToken");
        final Object value = type.getConstructor().newInstance();
        Transaction() throws ReflectiveOperationException { }
        void park(Root root) throws ReflectiveOperationException {
            hidden(root, false);
            type.getMethod("setWindowingMode", token, int.class).invoke(value, root.token, 1);
            type.getMethod("setBounds", token, Rect.class).invoke(value, root.token, null);
            type.getMethod("reorder", token, boolean.class).invoke(value, root.token, false);
        }
        void front(Root root) throws ReflectiveOperationException {
            type.getMethod("reorder", token, boolean.class).invoke(value, root.token, true);
        }
        void place(Root root, int[] bounds) throws ReflectiveOperationException {
            type.getMethod("setWindowingMode", token, int.class).invoke(value, root.token, 5);
            type.getMethod("setBounds", token, Rect.class).invoke(value, root.token,
                    new Rect(bounds[0], bounds[1], bounds[2], bounds[3]));
        }
        void hidden(Root root, boolean hidden) throws ReflectiveOperationException {
            type.getMethod("setHidden", token, boolean.class).invoke(value, root.token, hidden);
        }
        void apply() throws ReflectiveOperationException {
            Class<?> organizer = Class.forName("android.window.WindowOrganizer");
            organizer.getMethod("applyTransaction", type).invoke(organizer.getConstructor().newInstance(), value);
        }
    }
}
