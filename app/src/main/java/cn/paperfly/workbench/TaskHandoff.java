package cn.paperfly.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Session-only task handoff ordering. No configuration, Android or UI dependencies. */
final class TaskHandoff {
    static final class Task {
        final int id,display,user;
        final Object token;
        Task(int id,int display,int user,Object token){this.id=id;this.display=display;this.user=user;this.token=token;}
    }
    interface Backend {
        List<Task> snapshot() throws Exception;
        void hide(List<Task> tasks) throws Exception;
        void moveToMain(Task task) throws Exception;
        void restoreBehind(List<Task> tasks) throws Exception;
    }
    static void returnBehind(Map<Integer,Integer> ownedDisplays,Backend backend)throws Exception{
        List<Task> selected=new ArrayList<>();
        for(Task task:backend.snapshot()){
            Integer owner=ownedDisplays.get(task.display);
            if(task.display>0&&owner!=null&&owner==task.user&&task.id>=0&&task.token!=null)selected.add(task);
        }
        if(selected.isEmpty())return;
        Exception failure=null;
        try{
            backend.hide(selected);
            for(Task task:selected)backend.moveToMain(task);
        }catch(Exception e){failure=e;throw e;}
        finally{
            // Also undo a partially applied hide or a failed transfer. Never strand hidden apps.
            try{backend.restoreBehind(selected);}catch(Exception e){if(failure!=null)failure.addSuppressed(e);else throw e;}
        }
    }
}
