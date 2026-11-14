package cn.paperfly.workbench;
import android.os.*;
import java.util.function.Consumer;

/** Auto-hide affects only host views. It never schedules app canvas changes. */
final class DockAutoHide implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());private final Consumer<Boolean> changed;
    private boolean enabled=true,automatic,visible=true,held;
    private final Runnable hide=()->{if(enabled&&automatic&&!held)set(false);};
    DockAutoHide(Consumer<Boolean> changed){this.changed=changed;}
    void configure(boolean enabled,boolean automatic){if(this.enabled==enabled&&this.automatic==automatic)return;this.enabled=enabled;this.automatic=automatic;main.removeCallbacks(hide);set(enabled);if(enabled&&automatic)later();}
    boolean visible(){return visible;}boolean revealAvailable(){return enabled&&automatic&&!visible;}
    void reveal(){if(!enabled)return;set(true);later();}
    void interact(){if(enabled&&automatic)later();}
    void hold(boolean value){if(held==value)return;held=value;main.removeCallbacks(hide);if(!value)later();}
    private void later(){main.removeCallbacks(hide);if(enabled&&automatic&&!held)main.postDelayed(hide,2500);}
    private void set(boolean value){if(visible==value)return;visible=value;changed.accept(value);}
    @Override public void close(){main.removeCallbacksAndMessages(null);}
}
