package cn.paperfly.workbench;

import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs in the USB-activated built-in shell process, never in the application's UI process. */
public final class WorkbenchService extends IWorkbenchService.Stub {
    private static final int OUTPUT_LIMIT = 65536;
    private static final int COMMAND_TIMEOUT_SECONDS = 8;
    private static final Pattern COMPONENT = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*/"
                    + "\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern ROOT = Pattern.compile("^\\s*(?:RootTask|Stack) id=(\\d+).*?displayId=(\\d+).*$");
    private static final Pattern TASK = Pattern.compile(
            "^\\s*taskId=(\\d+):\\s+(\\S+)(.*)$");
    private static final Pattern USER = Pattern.compile("\\buserId=(\\d+)\\b");
    private static final Pattern RECT = Pattern.compile(
            "\\bbounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private final int ownerUid;
    private final StageEngine stageEngine = new StageEngine();
    private final LiveWindowEngine liveEngine;
    private final RecentTaskAccess recentTasks;

    private volatile IBinder session;
    private IBinder.DeathRecipient sessionDeath;

    public WorkbenchService(int ownerUid, Context shellContext) {
        this.ownerUid = ownerUid;
        recentTasks = new RecentTaskAccess(shellContext,ownerUid);
        liveEngine = new LiveWindowEngine(shellContext,recentTasks);
    }

    synchronized void recoverLocal() { stageEngine.recover(); }
    synchronized void cleanupLocal() {
        liveEngine.close();
        stageEngine.cleanup();
        detachSession();
    }
    private void detachSession() {
        if (session != null && sessionDeath != null) session.unlinkToDeath(sessionDeath, 0);
        session = null;
        sessionDeath = null;
    }
    private void attachSession(IBinder token) throws RemoteException {
        if (token == null) throw new SecurityException("Missing stage session");
        if (token == session) return;
        if (session != null) cleanupLocal();
        sessionDeath = () -> {
            synchronized (WorkbenchService.this) {
                if (session == token) cleanupLocal();
            }
        };
        token.linkToDeath(sessionDeath, 0);
        session = token;
    }

    private void checkCaller() {
        if (Binder.getCallingUid() != ownerUid) {
            throw new SecurityException("Only the Workbench application may call this service.");
        }
    }

    @Override public synchronized Bundle recentApplications(){
        checkCaller();long identity=Binder.clearCallingIdentity();
        try{return recentTasks.applications();}finally{Binder.restoreCallingIdentity(identity);}
    }

    @Override public synchronized Bundle openLiveWindow(String component, android.view.Surface surface,
            int width, int height, int density, int hostTask, IBinder token) throws RemoteException {
        checkCaller();
        attachSession(token);
        long identity = Binder.clearCallingIdentity();
        try { return liveEngine.open(component, surface, width, height, density, hostTask); }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public synchronized Bundle openLiveInstance(String windowId,String component,android.view.Surface surface,int width,int height,int density,int hostTask,IBinder token)throws RemoteException{
        checkCaller();attachSession(token);long identity=Binder.clearCallingIdentity();
        try{return liveEngine.openInstance(windowId,component,surface,width,height,density,hostTask);}finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public synchronized Bundle rebindInstanceSurface(String windowId,android.view.Surface surface,IBinder token){
        checkCaller();if(token==null||!token.equals(session))throw new SecurityException("工作台会话已结束");
        long identity=Binder.clearCallingIdentity();try{return liveEngine.rebind(windowId,surface);}finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public void liveTouch(int displayId, android.view.MotionEvent event, IBinder token) {
        checkCaller();
        if (token == null || !token.equals(session)) { if(event!=null)event.recycle(); return; }
        long identity = Binder.clearCallingIdentity();
        try { liveEngine.touch(displayId, event); }
        finally { if (event != null) event.recycle(); Binder.restoreCallingIdentity(identity); }
    }

    @Override public void liveKeyEvent(int displayId,android.view.KeyEvent event,IBinder token){
        checkCaller();if(token==null||!token.equals(session))return;
        long identity=Binder.clearCallingIdentity();try{liveEngine.keyEvent(displayId,event);}finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public void liveKey(int displayId, int keyCode, IBinder token) {
        checkCaller();
        if (token == null || !token.equals(session)) return;
        long identity = Binder.clearCallingIdentity();
        try { liveEngine.key(displayId, keyCode); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    @Override public Bundle backLiveWindow(int displayId,IBinder token){
        checkCaller();if(token==null||!token.equals(session))throw new SecurityException("工作台会话已结束");
        long identity=Binder.clearCallingIdentity();try{Bundle result=new Bundle();result.putBoolean("success",liveEngine.key(displayId,android.view.KeyEvent.KEYCODE_BACK));return result;}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public void focusLiveWindow(int displayId, IBinder token) {
        checkCaller();
        if(token==null||!token.equals(session))return;
        long identity=Binder.clearCallingIdentity();
        try{liveEngine.focus(displayId);}finally{Binder.restoreCallingIdentity(identity);}
    }

    @Override public synchronized String closeLiveStage(IBinder token) {
        checkCaller();
        if (token == null || !token.equals(session)) return "会话已结束";
        long identity = Binder.clearCallingIdentity();
        try { String result = liveEngine.close(); detachSession(); return result; }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public Bundle liveMemory(IBinder token) {
        checkCaller();
        if (token == null || (session != null && !token.equals(session))) return new Bundle();
        long identity = Binder.clearCallingIdentity();
        try { return liveEngine.memory(); }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public synchronized Bundle resizeLiveStage(int width,int height,int density,IBinder token) {
        checkCaller();
        if(token==null||!token.equals(session))throw new SecurityException("工作台会话已结束");
        long identity=Binder.clearCallingIdentity();
        try{return liveEngine.resize(width,height,density);}
        finally{Binder.restoreCallingIdentity(identity);}
    }

    @Override public synchronized Bundle rebindLiveSurface(String component,android.view.Surface surface,IBinder token){
        checkCaller();
        if(token==null||!token.equals(session))throw new SecurityException("工作台会话已结束");
        long identity=Binder.clearCallingIdentity();
        try{return liveEngine.rebind(component,surface);}
        finally{Binder.restoreCallingIdentity(identity);}
    }

    @Override public synchronized Bundle resizeLiveWindow(int displayId,int width,int height,int density,IBinder token){
        checkCaller();if(token==null||!token.equals(session))throw new SecurityException("工作台会话已结束");
        long identity=Binder.clearCallingIdentity();try{return liveEngine.resizeOne(displayId,width,height,density);}finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public synchronized String closeLiveWindow(int displayId, IBinder token) {
        checkCaller();
        if (token == null || !token.equals(session)) return "会话已结束";
        long identity = Binder.clearCallingIdentity();
        try { return liveEngine.remove(displayId); }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public synchronized Bundle switchStage(String[] components, int index, int[] bounds,
                                                      int hostTask, IBinder token, boolean stableSwitching) throws RemoteException {
        checkCaller();
        attachSession(token);
        return stageEngine.switchTo(components, index, bounds, hostTask, stableSwitching);
    }

    @Override public synchronized String endStage(int hostTask, boolean returnToHost) {
        checkCaller();
        String result = stageEngine.end(hostTask, returnToHost);
        detachSession();
        return result;
    }

    @Override
    public synchronized String diagnostics() {
        checkCaller();
        StringBuilder out = new StringBuilder();
        out.append("设备：").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL)
                .append("\nAndroid ").append(Build.VERSION.RELEASE)
                .append(" · API ").append(Build.VERSION.SDK_INT)
                .append("\n服务 UID：").append(android.os.Process.myUid())
                .append("\n\n本次仅查询，没有修改系统设置。\n");
        out.append("当前窗口后端：独立虚拟显示器 → 本地 Surface。\n")
                .append("主次切换只移动画面，不改变应用分辨率，不修改系统动画倍率。\n");
        append(out, "系统声明的副屏 Activity 能力", run("/system/bin/pm", "has-feature",
                "android.software.activities_on_secondary_displays"));
        append(out, "屏幕尺寸", run("/system/bin/wm", "size"));
        append(out, "屏幕密度", run("/system/bin/wm", "density"));
        out.append("\n兼容性以应用实际进入窗口、触控和输入法正常为准。")
                .append("不需要开启自由窗口或强制调整大小；不支持的应用会提示移除。\n");
        return out.toString();
    }

    @Override
    public synchronized String launch(String[] components, int[] bounds) {
        checkCaller();
        String invalid = validate(components, bounds);
        if (invalid != null) return "未执行：" + invalid;
        // Copy caller-owned data even for in-process tests.
        String[] targets = components.clone();
        int[] rectangles = bounds.clone();
        StringBuilder out = new StringBuilder("已请求系统以自由窗口打开应用。以下为系统回报，画面仍需实机确认。\n");
        CommandResult currentUser = run("/system/bin/am", "get-current-user");
        int user = parseNonnegativeInt(currentUser.output.trim());
        if (!currentUser.ok() || user < 0) {
            return "未执行：无法确定当前 Android 用户，避免操作到其他用户的任务。\n"
                    + currentUser.describe();
        }
        List<Placement> placements = new ArrayList<>();
        long deadline = SystemClock.elapsedRealtime() + 65000;
        for (int i = 0; i < targets.length; i++) {
            if (SystemClock.elapsedRealtime() > deadline) {
                out.append("\n已停止：本次操作超过时间预算，剩余应用未启动。\n");
                break;
            }
            String target = targets[i];
            int[] requested = Arrays.copyOfRange(rectangles, i * 4, i * 4 + 4);
            out.append("\n").append(i + 1).append(". ").append(target).append("\n");
            CommandResult started = run("/system/bin/am", "start", "-W", "--user",
                    Integer.toString(user), "--display", "0", "--windowingMode", "5",
                    "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                    "-n", target, "-f", "0x10000000");
            out.append("启动：").append(started.describe()).append("\n");
            if (!started.ok()) continue;

            TaskRecord task = awaitSettledTask(target, user);
            if (task == null) {
                out.append("启动后未能稳定、唯一地识别当前用户的目标任务；没有调整其他任务。窗口模式未验证。\n");
                continue;
            }
            if (!normalize(target).equals(task.component)) {
                out.append("启动页已跳转，匹配到当前用户唯一的同包任务：")
                        .append(task.component).append("\n");
            }
            if (!task.freeform && isHonor()) {
                out.append("荣耀窗口适配：")
                        .append(TaskTransactionBridge.requestFreeform(task, user)).append("\n");
                TaskRecord converted = awaitSettledTask(target, user);
                if (converted == null || converted.id != task.id) {
                    out.append("转换后任务已变化或无法核验；跳过布局。\n");
                    continue;
                }
                task = converted;
            }
            if (!task.freeform) {
                out.append("系统未报告该任务处于 freeform；跳过调整。可能被厂商策略改为全屏或其他模式。\n");
                continue;
            }
            CommandResult resized = run("/system/bin/am", "task", "resize",
                    Integer.toString(task.id), Integer.toString(requested[0]),
                    Integer.toString(requested[1]), Integer.toString(requested[2]),
                    Integer.toString(requested[3]));
            out.append("布局请求：").append(resized.describe()).append("\n");
            placements.add(new Placement(target, task.component, task.id, requested));
        }
        // Verify after launching every app: a later launch may have hidden an earlier window.
        if (!placements.isEmpty()) {
            SystemClock.sleep(450); // Let OEM window transitions finish before reporting bounds.
            out.append("\n最终系统状态：\n");
            CommandResult finalList = run("/system/bin/am", "stack", "list");
            for (Placement placement : placements) {
                TaskRecord actual = finalList.ok()
                        ? findTask(finalList.output, placement.resolvedComponent, user) : null;
                out.append(placement.component).append("：");
                if (actual == null || actual.id != placement.taskId) {
                    out.append("任务状态无法验证。\n");
                } else if (!actual.freeform) {
                    out.append("已不在 freeform 模式。\n");
                } else {
                    out.append("系统报告 freeform，")
                            .append(actual.visible ? "所在任务组可见" : "所在任务组不可见或可见性未知")
                            .append("；");
                    if (actual.bounds != null && Arrays.equals(actual.bounds, placement.bounds)) {
                        out.append("坐标与请求一致");
                    } else if (actual.bounds != null) {
                        out.append("系统采用坐标 ").append(Arrays.toString(actual.bounds))
                                .append("，与请求不同");
                    } else {
                        out.append("坐标无法验证");
                    }
                    out.append("。\n");
                }
            }
        }
        out.append("\n该结果不保证窗口未被遮挡，也不代表 vivo 级工作台已实现。")
                .append("如仍全屏，请保留诊断结果；本应用不会自动更改开发者设置。\n");
        return out.toString();
    }

    private static boolean isHonor() {
        return "HONOR".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static String validate(String[] components, int[] bounds) {
        if (components == null || components.length < 1 || components.length > 3) {
            return "一次只能启动 1–3 个应用。";
        }
        if (bounds == null || bounds.length != components.length * 4) return "窗口坐标数量不正确。";
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            if (component == null || component.length() > 512 || !COMPONENT.matcher(component).matches()) {
                return "应用组件名称无效。";
            }
            String normalized = normalize(component);
            if (normalized == null || !seen.add(normalized)) return "应用组件无效或重复。";
            int l = bounds[i * 4], t = bounds[i * 4 + 1];
            int r = bounds[i * 4 + 2], b = bounds[i * 4 + 3];
            if (l < 0 || t < 0 || r > 20000 || b > 20000 || r <= l || b <= t
                    || r - l < 80 || b - t < 80) {
                return "窗口坐标超出允许范围，或窗口太小。";
            }
        }
        return null;
    }

    private static String normalize(String text) {
        ComponentName name = ComponentName.unflattenFromString(text);
        return name == null ? null : name.flattenToString();
    }

    /** am start -W can return before a launcher trampoline has opened its real task. */
    private static TaskRecord awaitSettledTask(String component, int user) {
        TaskRecord previous = null;
        int[] delays = {250, 400, 400};
        for (int delay : delays) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
            CommandResult listed = run("/system/bin/am", "stack", "list");
            TaskRecord current = listed.ok()
                    ? findTaskWithPackageFallback(listed.output, component, user) : null;
            if (current != null && previous != null && current.id == previous.id
                    && current.component.equals(previous.component)
                    && current.freeform == previous.freeform) {
                return current;
            }
            previous = current;
        }
        return null; // Do not resize a still-changing or ambiguous launch result.
    }

    /** Conservative parser of AOSP `am stack list`; unknown OEM formats fail closed. */
    static TaskRecord findTask(String dump, String component, int user) {
        String expected = normalize(component);
        TaskRecord found = null;
        for (TaskRecord task : parseTasks(dump, user)) {
            if (!task.component.equals(expected)) continue;
            if (found != null) return null;
            found = task;
        }
        return found;
    }

    static TaskRecord findTaskWithPackageFallback(String dump, String component, int user) {
        String expected = normalize(component);
        if (expected == null) return null;
        String packagePrefix = expected.substring(0, expected.indexOf('/') + 1);
        TaskRecord exact = null;
        TaskRecord samePackage = null;
        int exactCount = 0;
        int packageCount = 0;
        for (TaskRecord task : parseTasks(dump, user)) {
            if (task.component.equals(expected)) {
                exact = task;
                exactCount++;
            }
            if (task.component.startsWith(packagePrefix)) {
                samePackage = task;
                packageCount++;
            }
        }
        if (exactCount == 1) return exact;
        if (exactCount > 1) return null;
        // Launcher redirects may change the component. Only a unique same-package task is safe.
        return packageCount == 1 ? samePackage : null;
    }

    private static List<TaskRecord> parseTasks(String dump, int user) {
        List<TaskRecord> records = new ArrayList<>();
        if (dump.contains("[输出超过")) return records;
        int display = -1;
        boolean freeform = false;
        for (String line : dump.split("\\r?\\n")) {
            Matcher root = ROOT.matcher(line);
            if (root.matches()) {
                display = parseNonnegativeInt(root.group(2));
                freeform = false;
                continue;
            }
            if (line.trim().startsWith("configuration=")) {
                freeform = Pattern.compile("\\bmWindowingMode=freeform\\b").matcher(line).find();
                continue;
            }
            Matcher task = TASK.matcher(line);
            if (!task.matches() || display != 0) continue;
            String taskComponent = normalize(task.group(2));
            if (taskComponent == null) continue;
            Matcher taskUser = USER.matcher(task.group(3));
            if (!taskUser.find() || parseNonnegativeInt(taskUser.group(1)) != user) continue;
            int id = parseNonnegativeInt(task.group(1));
            if (id < 0) continue;
            int[] rect = null;
            Matcher coordinates = RECT.matcher(task.group(3));
            if (coordinates.find()) {
                try {
                    rect = new int[] {Integer.parseInt(coordinates.group(1)),
                            Integer.parseInt(coordinates.group(2)), Integer.parseInt(coordinates.group(3)),
                            Integer.parseInt(coordinates.group(4))};
                } catch (NumberFormatException ignored) { }
            }
            records.add(new TaskRecord(id, taskComponent, freeform,
                    Pattern.compile("\\bvisible=true\\b").matcher(task.group(3)).find(), rect));
        }
        return records;
    }

    private static int parseNonnegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static void append(StringBuilder out, String label, CommandResult result) {
        out.append("\n").append(label).append("：\n").append(result.describe()).append("\n");
    }

    /** Commands are defined internally; this method is not exposed through Binder. */
    static CommandResult run(String... args) {
        Process process = null;
        OutputCollector collector = null;
        Thread reader = null;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
            collector = new OutputCollector(process.getInputStream());
            reader = new Thread(collector, "workbench-command-output");
            reader.setDaemon(true);
            reader.start();
            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            reader.join(750);
            String output = collector.text();
            if (!completed) return new CommandResult(-1, output, "命令超时，未确认执行结果");
            return new CommandResult(process.exitValue(), output, null);
        } catch (IOException exception) {
            return new CommandResult(-1, "", "命令无法执行：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "操作已中断，未确认执行结果");
        } finally {
            if (process != null) {
                process.destroy();
                try { process.getInputStream().close(); } catch (IOException ignored) { }
                try { process.getOutputStream().close(); } catch (IOException ignored) { }
                try { process.getErrorStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public void destroy() {
        checkCaller();
        cleanupLocal();
        System.exit(0);
    }

    static final class TaskRecord {
        final int id;
        final String component;
        final boolean freeform;
        final boolean visible;
        final int[] bounds;
        TaskRecord(int id, String component, boolean freeform, boolean visible, int[] bounds) {
            this.id = id;
            this.component = component;
            this.freeform = freeform;
            this.visible = visible;
            this.bounds = bounds;
        }
    }

    private static final class Placement {
        final String component;
        final String resolvedComponent;
        final int taskId;
        final int[] bounds;
        Placement(String component, String resolvedComponent, int taskId, int[] bounds) {
            this.component = component;
            this.resolvedComponent = resolvedComponent;
            this.taskId = taskId;
            this.bounds = bounds;
        }
    }

    static final class CommandResult {
        final int exit;
        final String output;
        final String error;
        CommandResult(int exit, String output, String error) {
            this.exit = exit;
            this.output = output;
            this.error = error;
        }
        boolean ok() {
            return exit == 0 && error == null && !output.contains("Error:")
                    && !output.contains("Exception") && !output.contains("Permission Denial");
        }
        String describe() {
            String message = output.trim();
            if (message.length() > 3000) message = message.substring(0, 3000) + "\n[已截断]";
            return (error == null ? "退出码 " + exit : error)
                    + (message.isEmpty() ? "（无文本输出）" : "\n" + message);
        }
    }

    private static final class OutputCollector implements Runnable {
        private final InputStream stream;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean truncated;
        OutputCollector(InputStream stream) { this.stream = stream; }
        @Override public void run() {
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    synchronized (this) {
                        int keep = Math.min(read, OUTPUT_LIMIT - bytes.size());
                        if (keep > 0) bytes.write(buffer, 0, keep);
                        if (keep < read) truncated = true;
                    }
                }
            } catch (IOException ignored) { }
        }
        synchronized String text() {
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    + (truncated ? "\n[输出超过 64 KiB，已截断]" : "");
        }
    }
}
