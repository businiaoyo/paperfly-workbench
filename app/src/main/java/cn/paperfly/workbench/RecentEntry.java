package cn.paperfly.workbench;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Read-only discovery on its own queue; a stopped entry screen never launches late. */
final class RecentEntry implements AutoCloseable {
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int generation;
    void load(Consumer<List<String>> loaded,Consumer<String> failed){
        int request=++generation;
        reader.execute(()->{
            try{
                IWorkbenchService service=BridgeState.service();
                if(service==null)throw new IllegalStateException("请先激活工作台");
                Bundle result=service.recentApplications();
                if(!result.getBoolean("success"))throw new IllegalStateException(result.getString("detail","无法读取最近应用"));
                List<String> components=result.getStringArrayList("components");
                if(components==null||components.isEmpty())throw new IllegalStateException("没有可接续的后台应用，可继续上次工作台或手动添加");
                main.post(()->{if(request==generation)loaded.accept(components);});
            }catch(Exception error){main.post(()->{if(request==generation)failed.accept(error.getMessage()==null?"无法读取最近应用":error.getMessage());});}
        });
    }
    void cancel(){generation++;}
    @Override public void close(){cancel();reader.shutdown();}
}
