package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceControl;
import java.util.concurrent.Executor;

/** A bounded, fullscreen task area inside one engine-owned display. Never registers a global organizer. */
@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
final class LiveCanvasArea {
    static final int BUFFER_EDGE = 1920;
    private final Object organizer, token;
    private final Class<?> organizerType, transactionType, tokenType;
    private final int displayId;
    private boolean attached;

    private LiveCanvasArea(int displayId) throws Exception {
        this.displayId = displayId;
        organizerType = Class.forName("android.window.DisplayAreaOrganizer");
        transactionType = Class.forName("android.window.WindowContainerTransaction");
        tokenType = Class.forName("android.window.WindowContainerToken");
        organizer = organizerType.getConstructor(Executor.class).newInstance((Executor) Runnable::run);
        // Use a sibling of DefaultTaskDisplayArea, never an empty nested task area. Some OEMs
        // dereference the top child of nested areas while returning the last task.
        Object areaToken;
        try {
            Object appeared = organizerType.getMethod("createTaskDisplayArea", int.class, int.class, String.class)
                    .invoke(organizer, displayId, 0, "OpenWorkbench Canvas " + displayId);
            Object info = appeared.getClass().getMethod("getDisplayAreaInfo").invoke(appeared);
            areaToken = info.getClass().getField("token").get(info);
            // WM owns the area surface. We don't animate, snapshot or replace it.
            ((SurfaceControl) appeared.getClass().getMethod("getLeash").invoke(appeared)).release();
        } catch (Exception | LinkageError error) {
            try { organizerType.getMethod("unregisterOrganizer").invoke(organizer); } catch (Exception ignored) {}
            throw error;
        }
        token=areaToken;
    }

    static LiveCanvasArea create(int displayId, int width, int height, int density) {
        LiveCanvasArea area = null;
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || width > BUFFER_EDGE || height > BUFFER_EDGE) return null;
            area = new LiveCanvasArea(displayId);
            area.resize(width, height, density);
            return area;
        } catch (Exception | LinkageError error) {
            if (area != null) area.close(); // No app has been attached yet.
            Log.w("WorkbenchLive", "Stable canvas unavailable; using display resize", error);
            return null;
        }
    }

    long resize(int width, int height, int density) throws Exception {
        if (width < 320 || height < 320 || width > BUFFER_EDGE || height > BUFFER_EDGE
                || density < 100 || density > 640) throw new IllegalArgumentException("画布尺寸无效");
        Object transaction = transactionType.getConstructor().newInstance();
        transactionType.getMethod("setBounds", tokenType, Rect.class)
                .invoke(transaction, token, new Rect(0, 0, width, height));
        transactionType.getMethod("setDensityDpi", tokenType, int.class).invoke(transaction, token, density);
        int widthDp = width * 160 / density, heightDp = height * 160 / density;
        transactionType.getMethod("setScreenSizeDp", tokenType, int.class, int.class)
                .invoke(transaction, token, widthDp, heightDp);
        transactionType.getMethod("setSmallestScreenWidthDp", tokenType, int.class)
                .invoke(transaction, token, Math.min(widthDp, heightDp));
        transactionType.getMethod("setIgnoreOrientationRequest", tokenType, boolean.class)
                .invoke(transaction, token, true);
        long frameAfter=0;
        if(attached)frameAfter=LiveCanvasSync.apply(transaction,transactionType);
        else apply(transaction);
        Log.i("WorkbenchLive", "CANVAS_AREA display=" + displayId + " " + width + "x" + height + " density=" + density);
        return frameAfter;
    }

    /** Caller has just verified the task's source ownership or its presence on our display. */
    void attach(Object taskToken) throws Exception {
        Object transaction = transactionType.getConstructor().newInstance();
        transactionType.getMethod("reparent", tokenType, tokenType, boolean.class)
                .invoke(transaction, taskToken, token, true);
        apply(transaction);
        attached=true;
    }

    private void apply(Object transaction) throws Exception {
        organizerType.getMethod("applyTransaction", transactionType).invoke(organizer, transaction);
    }

    /** Call only after task handoff/display release; deleting a populated area can foreground tasks. */
    void close() {
        try { organizerType.getMethod("unregisterOrganizer").invoke(organizer); }
        catch (Exception | LinkageError error) { Log.d("WorkbenchLive", "Canvas area already released display=" + displayId); }
    }
}
