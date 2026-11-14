package cn.paperfly.workbench;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Package metadata is loaded outside input and rendering. Missing packages retain a usable label. */
final class DockCatalog implements AutoCloseable {
    static final class Asset {final String name;final Drawable icon;final boolean unavailable;Asset(String name,Drawable icon){this(name,icon,false);}Asset(String name,Drawable icon,boolean unavailable){this.name=name;this.icon=icon;this.unavailable=unavailable;}}
    private final Context context;private final ExecutorService reader=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Asset> cache=new HashMap<>();private final Set<String> pending=new HashSet<>();private boolean closed;private int generation;
    DockCatalog(Context context){this.context=context.getApplicationContext();}
    Asset get(String component,Runnable changed){Asset cached=cache.get(component);if(cached!=null)return cached;
        int requestGeneration=generation;
        if(!closed&&pending.add(component))reader.execute(()->{ComponentName name=ComponentName.unflattenFromString(component);Asset value;
            try{android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(name,0);if(!info.enabled||!info.applicationInfo.enabled||!info.exported)throw new IllegalArgumentException("Unavailable");value=new Asset(info.loadLabel(context.getPackageManager()).toString(),info.loadIcon(context.getPackageManager()));}
            catch(Exception ignored){value=new Asset(name==null?component:name.getPackageName()+" · 暂不可用",null,true);}
            Asset loaded=value;main.post(()->{if(closed||requestGeneration!=generation)return;pending.remove(component);cache.put(component,loaded);changed.run();});
        });return new Asset(component,null);
    }
    void invalidate(){generation++;cache.clear();pending.clear();}
    @Override public void close(){closed=true;reader.shutdown();main.removeCallbacksAndMessages(null);}
}
