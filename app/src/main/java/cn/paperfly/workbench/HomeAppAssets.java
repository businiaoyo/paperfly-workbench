package cn.paperfly.workbench;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import cn.paperfly.workbench.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Home-only presentation assets. Package reads and icon rasterization stay off the UI thread. */
final class HomeAppAssets implements AutoCloseable {
    static final class Entry {
        final String label;final Bitmap icon;
        Entry(String label,Bitmap icon){this.label=label;this.icon=icon;}
    }
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int generation;
    private boolean closed;
    HomeAppAssets(Context context){this.context=context.getApplicationContext();}
    void load(WorkspaceLibrary library,Consumer<Map<String,Entry>> ready){
        int request=++generation;
        worker.execute(()->{
            Map<String,Entry> result=new HashMap<>();
            List<WorkspaceDefinition> definitions=new ArrayList<>(library.saved);definitions.add(library.current);
            int size=WorkbenchUi.dp(context,48);
            for(WorkspaceDefinition definition:definitions)for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps){
                if(Thread.currentThread().isInterrupted())return;
                if(result.containsKey(app.component))continue;
                String label=app.packageName+"（暂不可用）";Bitmap bitmap=null;
                try{
                    ActivityInfo info=context.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(app.component),0);
                    label=info.loadLabel(context.getPackageManager()).toString();
                    Drawable icon=info.loadIcon(context.getPackageManager());
                    bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,size,size);icon.draw(new Canvas(bitmap));
                }catch(Exception missing){/* Keep unavailable members in the presentation and configuration. */}
                result.put(app.component,new Entry(label,bitmap));
            }
            main.post(()->{if(!closed&&generation==request)ready.accept(Collections.unmodifiableMap(result));});
        });
    }
    @Override public void close(){closed=true;generation++;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
