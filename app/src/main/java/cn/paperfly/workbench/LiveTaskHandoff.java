package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shell adapter for returning only tasks currently on this session's owned displays. */
@SuppressLint({"PrivateApi","BlockedPrivateApi"})
final class LiveTaskHandoff implements TaskHandoff.Backend {
    private final Object manager,organizer;
    private final Class<?> transactionType,tokenType;
    private final java.util.Set<Object> focusedTokens=new java.util.HashSet<>();
    private final java.util.Set<Object> pausedInput=new java.util.HashSet<>();
    private LiveTaskHandoff()throws Exception{
        manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        tokenType=Class.forName("android.window.WindowContainerToken");
        transactionType=Class.forName("android.window.WindowContainerTransaction");
        organizer=Class.forName("android.window.WindowOrganizer").getConstructor().newInstance();
    }
    static void returnBehind(Map<Integer,Integer> ownedDisplays)throws Exception{
        returnBehind(ownedDisplays,Collections.emptySet());
    }
    static void returnBehind(Map<Integer,Integer> ownedDisplays,java.util.Set<Object> inputTokens)throws Exception{
        LiveTaskHandoff backend=new LiveTaskHandoff();backend.focusedTokens.addAll(inputTokens);
        TaskHandoff.returnBehind(ownedDisplays,backend);
    }
    @Override public List<TaskHandoff.Task> snapshot()throws Exception{
        List<?> roots=(List<?>)manager.getClass().getMethod("getAllRootTaskInfos").invoke(manager);
        List<TaskHandoff.Task> result=new ArrayList<>();
        for(Object root:roots){Class<?> type=root.getClass();
            // A focused task is known to be focusable; don't overwrite an unknown task setting.
            if(Boolean.TRUE.equals(type.getField("isFocused").get(root)))focusedTokens.add(type.getField("token").get(root));
            result.add(new TaskHandoff.Task(
                type.getField("taskId").getInt(root),type.getField("displayId").getInt(root),
                type.getField("userId").getInt(root),type.getField("token").get(root)));}
        return result;
    }
    @Override public void hide(List<TaskHandoff.Task> tasks)throws Exception{apply(tasks,true,false);}
    @Override public void moveToMain(TaskHandoff.Task task)throws Exception{
        // Resolve immediately before moving; a stale task ID alone is not authority.
        TaskHandoff.Task current=find(task);
        if(current==null)return;
        if(current.display!=task.display||current.user!=task.user)throw new IllegalStateException("交还前任务归属已变化");
        manager.getClass().getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(manager,task.id,0);
        current=find(task);
        if(current!=null&&current.display!=0)throw new IllegalStateException("系统未交还应用任务");
    }
    private TaskHandoff.Task find(TaskHandoff.Task expected)throws Exception{
        for(TaskHandoff.Task task:snapshot())if(task.id==expected.id&&Objects.equals(task.token,expected.token))return task;
        return null;
    }
    @Override public void restoreBehind(List<TaskHandoff.Task> tasks)throws Exception{
        try{apply(tasks,false,true);}
        catch(Exception batchFailure){
            // A disappearing task/vendor transaction failure must not leave its peers hidden.
            for(TaskHandoff.Task task:tasks)try{apply(Collections.singletonList(task),false,true);}
            catch(Exception retryFailure){
                try{apply(Collections.singletonList(task),false,false);}
                catch(Exception visibilityFailure){batchFailure.addSuppressed(visibilityFailure);}
                batchFailure.addSuppressed(retryFailure);
            }
            throw batchFailure;
        }
    }
    private void apply(List<TaskHandoff.Task> tasks,boolean hidden,boolean behind)throws Exception{
        Object transaction=transactionType.getConstructor().newInstance();
        for(TaskHandoff.Task task:tasks){
            if(behind)transactionType.getMethod("reorder",tokenType,boolean.class).invoke(transaction,task.token,false);
            transactionType.getMethod("setHidden",tokenType,boolean.class).invoke(transaction,task.token,hidden);
            // Keep the departing task unable to reclaim input during reparenting. An area-only
            // restriction is lost as soon as the task moves out, which can resurrect fallback IME.
            // Restore focusability together with background order and visibility, even on failure.
            if(hidden&&focusedTokens.contains(task.token))pausedInput.add(task.token);
            if(pausedInput.contains(task.token))transactionType.getMethod("setFocusable",tokenType,boolean.class)
                    .invoke(transaction,task.token,!hidden);
        }
        organizer.getClass().getMethod("applyTransaction",transactionType).invoke(organizer,transaction);
        Log.i("WorkbenchLive","HANDOFF "+(hidden?"hide":behind?"behind-and-show":"show")+" tasks="+tasks.size());
    }
}
