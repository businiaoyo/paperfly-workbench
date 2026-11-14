package cn.paperfly.workbench.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import cn.paperfly.workbench.model.WorkspaceDefinition;
import cn.paperfly.workbench.model.WorkspaceLibrary;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One storage queue per app process. Never shares the input/rendering or PSS sampling queue. */
public final class WorkspacePersistence {
    private static WorkspacePersistence instance;
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WorkspaceRepository repository;

    private WorkspacePersistence(Context context) { repository = new WorkspaceRepository(new AndroidWorkspaceStorage(context)); }

    public static synchronized WorkspacePersistence get(Context context) {
        if (instance == null) instance = new WorkspacePersistence(context.getApplicationContext());
        return instance;
    }

    public void loadCurrent(Consumer<WorkspaceDefinition> loaded, Consumer<String> failed) {
        storage.execute(() -> {
            try {
                WorkspaceDefinition current = repository.load().current;
                main.post(() -> loaded.accept(current));
            } catch (IOException error) { main.post(() -> failed.accept(error.getMessage())); }
        });
    }

    public void saveCurrent(WorkspaceDefinition current, Consumer<String> failed) {
        storage.execute(() -> {
            try { repository.saveCurrent(current); }
            catch (IOException error) { main.post(() -> failed.accept(error.getMessage())); }
        });
    }
    private interface Operation<T>{T run()throws IOException;}
    private <T> void submit(Operation<T> operation,Consumer<T> done,Consumer<String> failed){
        storage.execute(()->{try{T value=operation.run();main.post(()->done.accept(value));}
            catch(IOException e){main.post(()->failed.accept(e.getMessage()));}});
    }
    public void library(Consumer<WorkspaceLibrary> done,Consumer<String> failed){submit(repository::load,done,failed);}
    public void saveAs(WorkspaceDefinition current,String name,Consumer<WorkspaceDefinition> done,Consumer<String> failed){submit(()->repository.saveAs(current,name),done,failed);}
    public void activate(String id,Consumer<WorkspaceDefinition> done,Consumer<String> failed){submit(()->repository.activate(id),done,failed);}
    public void enterRecent(java.util.List<String> components,Consumer<WorkspaceDefinition> done,Consumer<String> failed){
        java.util.List<String> copy=new java.util.ArrayList<>(components);submit(()->repository.enterRecent(copy),done,failed);
    }
    public void rename(String id,String name,Runnable done,Consumer<String> failed){submit(()->{repository.rename(id,name);return true;},v->done.run(),failed);}
    public void overwrite(String id,WorkspaceDefinition current,Runnable done,Consumer<String> failed){submit(()->{repository.overwrite(id,current);return true;},v->done.run(),failed);}
    public void delete(String id,Runnable done,Consumer<String> failed){submit(()->{repository.delete(id);return true;},v->done.run(),failed);}
}
