package cn.paperfly.workbench;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;



/** Owns the built-in helper connection and asynchronous calls; listener callbacks always use the main thread. */
public final class WorkbenchController {
    public interface Listener {
        void onStatus(String text, boolean authorized);
        void onResult(String title, String detail);
    }

    private final Listener listener;
    private final Context applicationContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile IWorkbenchService service;
    private volatile boolean closed;
    private final Runnable connectionChanged = this::refresh;

    public WorkbenchController(Activity activity, Listener listener) {
        this.listener = listener;
        applicationContext = activity.getApplicationContext();
        BridgeState.add(connectionChanged);
    }
    public void connect() { refresh(); }
    public void refresh() {
        if (closed) return;
        service = BridgeState.service();
        status(service == null ? "尚未激活，请在首页完成无线或 USB 激活" : "已激活 · 可以进入工作台", service != null);
    }

    public void diagnose() {
        call("兼容性检测", IWorkbenchService::diagnostics, basicDiagnostics());
    }

    private String basicDiagnostics() {
        PackageManager pm = applicationContext.getPackageManager();
        return "本地设备信息（无需激活）\n设备：" + Build.MANUFACTURER + " / " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT
                + "\n系统声明 freeform："
                + pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                + "\n系统声明副屏 Activity："
                + pm.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
                + "\n系统未声明某项能力，不代表没有厂商自己的小窗功能。"
                + "\n开发者开关与实际窗口行为需要激活后进一步检测。\n\n";
    }

    public void launch(String[] components, int[] bounds) {
        String invalid = WorkbenchService.validate(components, bounds);
        if (invalid != null) {
            result("无法启动", invalid);
            return;
        }
        String[] copiedComponents = components.clone();
        int[] copiedBounds = bounds.clone();
        call("窗口试开结果", remote -> remote.launch(copiedComponents, copiedBounds), "");
    }

    public interface StageCallback { void complete(Bundle result); }

    public void switchStage(String[] components, int index, int[] bounds, int hostTask, IBinder session, boolean stableSwitching,
                            StageCallback callback) {
        IWorkbenchService current = authorizedService();
        if (current == null || !running.compareAndSet(false, true)) {
            Bundle failed = new Bundle();
            failed.putString("detail", current == null ? "尚未激活，请返回应用列表查看无线激活入口。" : "正在切换，请稍候。");
            main.post(() -> { if (!closed) callback.complete(failed); });
            return;
        }
        String[] copied = components.clone();
        int[] rectangle = bounds.clone();
        executor.execute(() -> {
            Bundle response;
            try {
                response = current.switchStage(copied, index, rectangle, hostTask, session, stableSwitching);
                if (response == null) throw new IllegalStateException("系统没有返回切换结果。");
            } catch (RemoteException | RuntimeException error) {
                response = new Bundle();
                response.putString("detail", usefulMessage(error));
            }
            running.set(false);
            Bundle delivered = response;
            main.post(() -> { if (!closed) callback.complete(delivered); });
        });
    }

    public void endStage(int hostTask, boolean returnToHost, java.util.function.Consumer<String> callback) {
        IWorkbenchService current = authorizedService();
        executor.execute(() -> {
            String report;
            try { report = current == null ? "服务已断开，可通过系统窗口控件关闭主窗口。" : current.endStage(hostTask, returnToHost); }
            catch (RemoteException | RuntimeException error) { report = usefulMessage(error); }
            String delivered = report;
            main.post(() -> { if (!closed) callback.accept(delivered); });
        });
    }

    private IWorkbenchService authorizedService() {
        return !closed && service != null && service.asBinder().pingBinder() ? service : null;
    }

    private interface RemoteCall { String run(IWorkbenchService remote) throws RemoteException; }

    private void call(String title, RemoteCall call, String fallback) {
        if (closed) return;
        IWorkbenchService current = authorizedService();
        if (current == null) {
            result(title, fallback + "尚未激活。请按首页的无线激活说明操作。");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            result(title, "上一项操作仍在执行，请稍候。");
            return;
        }
        status("正在" + (title.equals("兼容性检测") ? "检测设备能力" : "请求并核验窗口") + "…", true);
        executor.execute(() -> {
            try {
                String detail = call.run(current);
                result(title, detail == null ? "服务没有返回结果；执行状态未知。" : detail);
            } catch (RemoteException | RuntimeException exception) {
                result(title, fallback + "操作没有完成验证：" + usefulMessage(exception)
                        + "\n已经发出的启动请求可能仍然生效，请查看设备画面后再重试。");
            } finally {
                running.set(false);
                refresh();
            }
        });
    }

    private void status(String text, boolean authorized) {
        main.post(() -> { if (!closed) listener.onStatus(text, authorized); });
    }

    private void result(String title, String detail) {
        main.post(() -> { if (!closed) listener.onResult(title, detail); });
    }

    private static String usefulMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    public void close() {
        if (closed) return;
        closed = true;
        service = null;
        BridgeState.remove(connectionChanged);
        main.removeCallbacksAndMessages(null);
        executor.shutdown();
    }
}
