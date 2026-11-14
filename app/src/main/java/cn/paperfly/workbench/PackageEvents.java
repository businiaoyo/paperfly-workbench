package cn.paperfly.workbench;
import android.content.*;
import android.os.Build;
import java.util.function.Consumer;

/** Observes package availability only; it does not install, enable or remove other apps. */
final class PackageEvents implements AutoCloseable {
    private final Context context;private final BroadcastReceiver receiver;
    PackageEvents(Context context,Consumer<String> changed){this.context=context;receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){if(intent.getData()!=null)changed.accept(intent.getData().getSchemeSpecificPart());}};
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,filter);
    }
    @Override public void close(){context.unregisterReceiver(receiver);}
}
