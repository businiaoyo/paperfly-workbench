package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Shell-side adapter. No arbitrary intents, task IDs or commands are accepted from the client. */
@SuppressLint({"PrivateApi","BlockedPrivateApi"})
final class RecentTaskAccess {
    private final Context context;
    private final int ownerUser;
    RecentTaskAccess(Context context,int ownerUid){this.context=context;ownerUser=ownerUid/100000;}

    private Object manager()throws Exception{
        return Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
    }
    private void checkUser()throws Exception{
        int current=(Integer)ActivityManager.class.getMethod("getCurrentUser").invoke(null);
        if(current!=ownerUser)throw new SecurityException("当前用户已改变，请重新打开工作台");
    }
    @SuppressWarnings("unchecked")
    private List<ActivityManager.RecentTaskInfo> tasks()throws Exception{
        checkUser();Object manager=manager();
        Object slice=manager.getClass().getMethod("getRecentTasks",int.class,int.class,int.class).invoke(manager,48,0,ownerUser);
        return (List<ActivityManager.RecentTaskInfo>)slice.getClass().getMethod("getList").invoke(slice);
    }
    private static int number(Object object,String field)throws Exception{return object.getClass().getField(field).getInt(object);}
    private boolean eligible(ActivityManager.RecentTaskInfo task)throws Exception{
        return number(task,"userId")==ownerUser&&number(task,"displayId")==0&&task.numActivities>0&&task.baseActivity!=null
                &&task.baseIntent!=null&&!task.baseIntent.hasCategory(Intent.CATEGORY_HOME)
                &&!task.baseActivity.getPackageName().equals("cn.paperfly.workbench")
                &&!task.baseActivity.getPackageName().equals("com.android.systemui");
    }
    Bundle applications(){
        Bundle result=new Bundle();ArrayList<String> components=new ArrayList<>();
        try{
            HashSet<String> packages=new HashSet<>();
            for(ActivityManager.RecentTaskInfo task:tasks()){
                if(!eligible(task))continue;
                String pkg=task.baseActivity.getPackageName();if(!packages.add(pkg))continue;
                Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch==null||launch.getComponent()==null)continue;
                try{
                    ActivityInfo info=context.getPackageManager().getActivityInfo(launch.getComponent(),0);
                    if(info.exported&&info.enabled&&info.applicationInfo.enabled)components.add(launch.getComponent().flattenToString());
                }catch(PackageManager.NameNotFoundException ignored){/* Removed between task lookup and package lookup. */}
            }
            result.putBoolean("success",true);result.putStringArrayList("components",components);
        }catch(Exception|LinkageError error){result.putString("detail","无法读取最近应用，请重试或继续上次工作台");}
        return result;
    }
    /** Resolve again immediately before use: client-supplied/stale task IDs never cross the boundary. */
    int resumeOnDisplay(ComponentName component,int display)throws Exception{
        return resumeOnDisplay(component,display,null);
    }
    int resumeOnDisplay(ComponentName component,int display,LiveCanvasArea area)throws Exception{
        ActivityManager.RecentTaskInfo candidate=null;
        for(ActivityManager.RecentTaskInfo task:tasks())if(eligible(task)&&component.getPackageName().equals(task.baseActivity.getPackageName())){
            candidate=task;break;
        }
        if(candidate==null)return -1;
        Object manager=manager();
        final int id=candidate.taskId;
        Object standalone=standalone(manager,component,id);
        if(standalone==null)throw new IllegalStateException("请先退出该应用的系统分屏或小窗，再加入工作台");
        // Keep the stopped task invisible through intermediate display configurations.
        // Only the final target configuration should be delivered when it becomes visible.
        checkUser();
        Object token=standalone.getClass().getField("token").get(standalone);
        if(token==null)throw new IllegalStateException("后台任务令牌不可用");
        Class<?> tokenType=Class.forName("android.window.WindowContainerToken");
        Class<?> transactionType=Class.forName("android.window.WindowContainerTransaction");
        Object organizer=Class.forName("android.window.WindowOrganizer").getConstructor().newInstance();
        TaskRestore.run(new TaskRestore.Backend(){
            @Override public void hide()throws Exception{visibility(true);}
            @Override public void move()throws Exception{
                checkUser();Object fresh=standalone(manager,component,id);
                if(fresh==null||!token.equals(fresh.getClass().getField("token").get(fresh)))
                    throw new IllegalStateException("接续前后台任务已变化");
                if(area!=null)area.attach(token);
                else manager.getClass().getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(manager,id,display);
            }
            @Override public void show()throws Exception{visibility(false);}
            private void visibility(boolean hidden)throws Exception{
                Object transaction=transactionType.getConstructor().newInstance();
                transactionType.getMethod("setHidden",tokenType,boolean.class).invoke(transaction,token,hidden);
                organizer.getClass().getMethod("applyTransaction",transactionType).invoke(organizer,transaction);
            }
        });
        android.util.Log.i("WorkbenchLive","RESTORE task="+candidate.taskId+" display="+display+" direct=true hidden=true");
        return candidate.taskId;
    }
    private Object standalone(Object manager,ComponentName component,int id)throws Exception{
        Object standalone=null;
        List<?> roots=(List<?>)manager.getClass().getMethod("getAllRootTaskInfos").invoke(manager);
        for(Object root:roots){
            ComponentName base=(ComponentName)root.getClass().getField("baseActivity").get(root);
            Object configuration=root.getClass().getField("configuration").get(root);
            Object window=configuration.getClass().getField("windowConfiguration").get(configuration);
            int type=(Integer)window.getClass().getMethod("getActivityType").invoke(window);
            int mode=(Integer)window.getClass().getMethod("getWindowingMode").invoke(window);
            int[] children=(int[])root.getClass().getField("childTaskIds").get(root);
            if(TaskTransactionBridge.isIsolatedTarget(number(root,"taskId"),id,number(root,"displayId"),
                    number(root,"userId"),ownerUser,type,mode,children)
                    &&base!=null&&component.getPackageName().equals(base.getPackageName()))standalone=root;
        }
        return standalone;
    }
}
