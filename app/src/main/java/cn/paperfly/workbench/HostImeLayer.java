package cn.paperfly.workbench;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Keeps fallback IME layered with this Activity without taking input from a live display. */
final class HostImeLayer implements AutoCloseable {
    private final View decor, anchor;
    private final WindowManager manager;
    private boolean closed, attached;
    private final Runnable attach = this::attach;

    HostImeLayer(Activity activity) {
        decor = activity.getWindow().getDecorView();
        manager = activity.getWindowManager();
        anchor = new View(activity);
        anchor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        decor.post(attach);
    }

    private void attach() {
        if (closed || attached || decor.getWindowToken() == null) return;
        // Both flags make this child a layering target, but never an input/focus target.
        // The actual host remains focusable + ALT_FOCUSABLE_IM for physical navigation.
        // Without an eligible child, fallback IME can stay parented to the hidden home Activity.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.token = decor.getWindowToken();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("Workbench IME layer");
        manager.addView(anchor, params);
        attached = true;
    }

    @Override public void close() {
        closed = true;
        decor.removeCallbacks(attach);
        if (attached) { manager.removeViewImmediate(anchor); attached = false; }
    }
}
