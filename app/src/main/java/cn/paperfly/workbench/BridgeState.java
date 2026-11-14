package cn.paperfly.workbench;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import java.util.concurrent.CopyOnWriteArraySet;

final class BridgeState {
    private static volatile IBinder binder;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Runnable> LISTENERS = new CopyOnWriteArraySet<>();

    static synchronized void attach(IBinder incoming) throws RemoteException {
        if (incoming == null || !incoming.pingBinder()) throw new RemoteException("辅助进程未响应");
        if (binder == incoming) return;
        incoming.linkToDeath(() -> {
            synchronized (BridgeState.class) {
                if (binder == incoming) { binder = null; announce(); }
            }
        }, 0);
        binder = incoming;
        announce();
    }
    static IWorkbenchService service() {
        IBinder current = binder;
        return current != null && current.pingBinder() ? IWorkbenchService.Stub.asInterface(current) : null;
    }
    static void add(Runnable listener) { LISTENERS.add(listener); MAIN.post(listener); }
    static void remove(Runnable listener) { LISTENERS.remove(listener); }
    private static void announce() { for (Runnable listener : LISTENERS) MAIN.post(listener); }
}
