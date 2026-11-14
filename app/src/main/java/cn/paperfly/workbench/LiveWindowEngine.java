package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shell-owned displays feed app-owned Surfaces directly. No screenshots or video encoding. */
@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
final class LiveWindowEngine {
    private final Context context;
    private final Map<Integer, Window> windows = new java.util.concurrent.ConcurrentHashMap<>();
    private Object inputManager;
    private Method inject, setDisplay;
    private int host = -1;
    private final RecentTaskAccess recentTasks;
    private static final class Window {
        final VirtualDisplay display;
        LiveCanvasArea area;
        Surface surface;
        final String component;
        final int user;
        final java.util.Set<Object> inputTokens=java.util.concurrent.ConcurrentHashMap.newKeySet();
        String windowId;
        int width,height,density;
        Window(VirtualDisplay d, Surface s, String c,int w,int h,int dpi,int user) { display=d; surface=s; component=c;width=w;height=h;density=dpi;this.user=user; }
    }
    LiveWindowEngine(Context context,RecentTaskAccess recentTasks) { this.context=context;this.recentTasks=recentTasks; }

    Bundle open(String value, Surface surface, int width, int height, int density, int hostTask) {
        return openInstance(new cn.paperfly.workbench.model.AppTarget(value).id,value,surface,width,height,density,hostTask);
    }
    Bundle openInstance(String windowId,String value,Surface surface,int width,int height,int density,int hostTask){
        Bundle result = new Bundle();
        VirtualDisplay display = null;
        LiveCanvasArea area = null;
        try {
            if (surface == null || !surface.isValid() || width < 320 || height < 320
                    || width > 3200 || height > 3200 || density < 100 || density > 640)
                throw new IllegalArgumentException("窗口画面参数无效");
            cn.paperfly.workbench.model.AppTarget target=new cn.paperfly.workbench.model.AppTarget(windowId,value);
            boolean additional=target.additionalInstance();
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null || component.getPackageName().equals("cn.paperfly.workbench"))
                throw new IllegalArgumentException("应用无效");
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            if (!info.exported || !info.enabled) throw new SecurityException("此应用入口不可用");
            if(additional&&!InstancePolicy.supports(info.launchMode))throw new IllegalArgumentException("此应用不支持独立多窗口");
            for(Window window:windows.values())if(windowId.equals(window.windowId))throw new IllegalArgumentException("窗口已在工作台中");
            boolean hostFound = false;
            for (Object task : roots()) {
                ComponentName top=(ComponentName)field(task,"topActivity");
                if (taskId(task) == hostTask && displayOf(task) == 0 && top != null
                        && top.getClassName().equals("cn.paperfly.workbench.StageActivity")
                        && Boolean.TRUE.equals(field(task,"visible"))) hostFound=true;
            }
            if (!hostFound || (host >= 0 && host != hostTask)) throw new SecurityException("工作台任务已变化");
            host = hostTask;
            Map<Integer,Integer> priorTasks=new java.util.HashMap<>();
            if(additional)for(Object task:roots())if(windows.containsKey(displayOf(task)))priorTasks.put(taskId(task),displayOf(task));
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | flag("VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH") | flag("VIRTUAL_DISPLAY_FLAG_TRUSTED")
                    | flag("VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP") | flag("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS");
            // No DESTROY_CONTENT_ON_REMOVAL: Android returns tasks to the physical display on release/death.
            DisplayManager dm = context.getSystemService(DisplayManager.class);
            display = dm.createVirtualDisplay("OpenWorkbench " + component.getPackageName(), width, height,
                    density, surface, flags);
            if (display == null) throw new IllegalStateException("系统未创建应用显示器");
            int id = display.getDisplay().getDisplayId();
            android.os.IBinder windowBinder=(android.os.IBinder)Class.forName("android.os.ServiceManager")
                    .getMethod("getService",String.class).invoke(null,"window");
            Object wm = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,windowBinder);
            // WindowManager lazily creates DisplayContent; establish it before creating our area.
            wm.getClass().getMethod("setDisplayImePolicy", int.class, int.class).invoke(wm, id, 0);
            area=LiveCanvasArea.create(id,width,height,density);
            if(area!=null)display.resize(LiveCanvasArea.BUFFER_EDGE,LiveCanvasArea.BUFFER_EDGE,160);
            // Stable display buffers exceed the clipped app viewport. Let Android place IME
            // on the physical display so every key remains reachable, while input still targets
            // this app. Legacy displays keep their local IME policy. Never change global settings.
            if(area!=null)wm.getClass().getMethod("setDisplayImePolicy", int.class, int.class).invoke(wm, id, 1);
            int resumedTask=additional?-1:recentTasks.resumeOnDisplay(component,id,area);
            if(resumedTask<0){
            WorkbenchService.CommandResult launch = WorkbenchService.run("/system/bin/am", "start", "-W", "--user", "current",
                    "--display", Integer.toString(id), "--windowingMode", "1", "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER", "-n", value, "-f", additional?"0x18000000":"0x10000000");
            if (!launch.ok()) throw new IllegalStateException(launch.describe());
            }
            Object found=null;
            for (Object task : roots()) {
                ComponentName base=(ComponentName)field(task,"baseActivity");
                if (displayOf(task) == id && base != null
                        && base.getPackageName().equals(component.getPackageName())) found=task;
            }
            if (found == null) throw new IllegalStateException("此应用未进入工作台显示器，已撤销窗口");
            if(area!=null&&resumedTask<0)area.attach(field(found,"token"));
            if(additional){
                // Some apps redirect a standard launcher into a singleton Activity. Do not let
                // that redirect steal an already-owned task into the new window.
                boolean stolen=false;
                for(Object task:roots()){Integer previous=priorTasks.get(taskId(task));if(previous!=null&&displayOf(task)!=previous){
                    stolen=true;
                    if(displayOf(task)==id){Object manager=manager();manager.getClass().getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(manager,taskId(task),previous);}
                }}
                if(stolen){result.putString("userMessage","此应用无法保持两个独立窗口");throw new IllegalStateException("Application redirected an existing owned task");}
            }
            if(resumedTask>=0&&taskId(found)!=resumedTask)throw new IllegalStateException("后台任务已变化，已撤销窗口");
            windows.put(id, new Window(display, surface, value,width,height,density,(Integer)field(found,"userId")));
            windows.get(id).windowId=windowId;
            windows.get(id).area=area;
            result.putBoolean("success", true);
            result.putInt("bufferEdge",area==null?0:LiveCanvasArea.BUFFER_EDGE);
            result.putBoolean("reusedTask",resumedTask>=0);
            result.putInt("displayId", id); result.putInt("taskId", taskId(found));
            result.putString("detail", "实时窗口 " + id + " · 任务 " + taskId(found));
            System.out.println("OPEN display="+id+" task="+taskId(found)+" "+value);
        } catch (Exception | LinkageError e) {
            if (display != null){
                // A failed launch may still have created a task. Hand it back behind the host
                // before releasing this newly-created display, using only its owned identity.
                try{Map<Integer,Integer> owned=new java.util.HashMap<>();for(Object task:roots())if(displayOf(task)==display.getDisplay().getDisplayId())owned.put(displayOf(task),(Integer)field(task,"userId"));if(!owned.isEmpty())LiveTaskHandoff.returnBehind(owned);}catch(Exception ignored){}
                display.release();
            }
            if(area!=null)area.close();
            if (surface != null) surface.release();
            Throwable cause=e; while(cause.getCause()!=null) cause=cause.getCause();
            result.putString("detail", cause.toString());
            Log.e("WorkbenchLive", "Open failed", e);
            e.printStackTrace();
        }
        return result;
    }

    private static int flag(String name) throws Exception { return DisplayManager.class.getField(name).getInt(null); }

    private static List<?> roots() throws Exception {
        Object manager=manager();
        return (List<?>) manager.getClass().getMethod("getAllRootTaskInfos").invoke(manager);
    }
    private static Object manager() throws Exception { return Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null); }
    private static Object field(Object task,String name) throws Exception{return task.getClass().getField(name).get(task);}
    private static int taskId(Object task) throws Exception{return (Integer)field(task,"taskId");}
    private static int displayOf(Object task) throws Exception { return (Integer)field(task,"displayId"); }

    void touch(int id, MotionEvent event) {
        if (!windows.containsKey(id) || event == null || event.getPointerCount()>10) return;
        try {
            int source=event.getSource();if(source==0){source=InputDevice.SOURCE_TOUCHSCREEN;event.setSource(source);}
            if((source&InputDevice.SOURCE_CLASS_POINTER)==0)return;
            if(source!=InputDevice.SOURCE_MOUSE&&source!=InputDevice.SOURCE_STYLUS&&source!=InputDevice.SOURCE_TOUCHSCREEN&&source!=InputDevice.SOURCE_TOUCHPAD)return;
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_OUTSIDE)return;
            inject(id, event);
        } catch (Exception e) { Log.e("WorkbenchLive", "Touch failed", e); e.printStackTrace(); }
    }
    void keyEvent(int id,KeyEvent event){
        if(!windows.containsKey(id)||event==null||!InputPolicy.allowedKey(event.getKeyCode())||event.getRepeatCount()>1000)return;
        if(event.getAction()!=KeyEvent.ACTION_DOWN&&event.getAction()!=KeyEvent.ACTION_UP)return;
        try{inject(id,event);}catch(Exception error){Log.w("WorkbenchLive","Keyboard input failed",error);}
    }
    synchronized boolean key(int id, int code) {
        if (!windows.containsKey(id) || (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ENTER
                && code != KeyEvent.KEYCODE_DEL && code != KeyEvent.KEYCODE_TAB)) return false;
        focus(id);
        long down=SystemClock.uptimeMillis();
        try {
            inject(id,new KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0,0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD),2);
            inject(id,new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0,0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD),2);
            if(code==KeyEvent.KEYCODE_BACK){
                // MAIN activities may finish at their root on a presentation display, which has no launcher.
                // Restore only this owned app into the same display when Back leaves it empty.
                SystemClock.sleep(180);
                boolean occupied=false;
                for(Object task:roots())if(displayOf(task)==id&&Boolean.TRUE.equals(field(task,"visible"))&&field(task,"topActivity")!=null)occupied=true;
                Window window=windows.get(id);
                if(!occupied&&window!=null){
                    WorkbenchService.CommandResult restored=WorkbenchService.run("/system/bin/am","start","-W","--user","current","--display",Integer.toString(id),
                        "--windowingMode","1","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-n",window.component,"-f",new cn.paperfly.workbench.model.AppTarget(window.windowId,window.component).additionalInstance()?"0x18000000":"0x10000000");
                    Log.i("WorkbenchLive","BACK_ROOT_RESTORE display="+id+" ok="+restored.ok());if(!restored.ok())return false;
                    if(window.area!=null)for(Object task:roots())if(displayOf(task)==id)window.area.attach(field(task,"token"));
                }
            }
            return true;
        } catch (Exception e) { Log.e("WorkbenchLive", "Key failed", e);return false; }
    }
    void focus(int id){
        if(id!=0&&!windows.containsKey(id))return;
        try{
            if(id==0){
                boolean hostVisible=false;
                for(Object task:roots())if(taskId(task)==host&&displayOf(task)==0&&Boolean.TRUE.equals(field(task,"visible")))hostVisible=true;
                if(!hostVisible)return;
            }
            Object manager=manager();manager.getClass().getMethod("focusTopTask",int.class).invoke(manager,id);
            // Remember confirmed input ownership before a host menu temporarily takes focus.
            // The handoff later needs this evidence even when isFocused has already become false.
            Window window=windows.get(id);
            if(window!=null){
                Object focused=manager.getClass().getMethod("getFocusedRootTaskInfo").invoke(manager);
                if(focused!=null&&displayOf(focused)==id&&(Integer)field(focused,"userId")==window.user)
                    window.inputTokens.add(field(focused,"token"));
            }
        }
        catch(Exception e){System.err.println("Unable to focus live display "+id);e.printStackTrace();}
    }
    private void inject(int id, InputEvent event) throws Exception { inject(id,event,0); }
    private void inject(int id, InputEvent event,int mode) throws Exception {
        if (inputManager == null) {
            Class<?> type;
            try { type=Class.forName("android.hardware.input.InputManagerGlobal"); }
            catch (ClassNotFoundException ignored) { type=Class.forName("android.hardware.input.InputManager"); }
            inputManager=type.getMethod("getInstance").invoke(null);
            inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
            setDisplay=InputEvent.class.getMethod("setDisplayId",int.class);
        }
        setDisplay.invoke(event,id);
        if (!((Boolean)inject.invoke(inputManager,event,mode))) Log.w("WorkbenchLive","Input rejected display="+id);
    }

    synchronized String remove(int id) {
        return remove(id,true);
    }
    private String remove(int id,boolean keepHost) {
        Window window=windows.remove(id);
        if (window == null) return "窗口已移除";
        // Single-card removal needs the same hidden handoff as a whole-session exit.
        // Never bring the removed application in front of the host, even for one frame.
        if(keepHost)try{LiveTaskHandoff.returnBehind(java.util.Collections.singletonMap(id,window.user),window.inputTokens);}
        catch(Exception|LinkageError error){Log.w("WorkbenchLive","Unable to return removed task behind host",error);}
        try { window.display.release(); }
        finally { if(window.area!=null)window.area.close();window.surface.release(); }
        Log.i("WorkbenchLive","CLOSE display="+id);
        return "应用已返回系统任务";
    }

    Bundle memory() {
        java.util.Set<String> packages = new java.util.LinkedHashSet<>();
        for (Window window : windows.values()) {
            ComponentName component = ComponentName.unflattenFromString(window.component);
            if (component != null) packages.add(component.getPackageName());
        }
        return MemorySampler.sample(context, packages);
    }

    Bundle rebind(String component,Surface surface){
        Bundle result=new Bundle();boolean adopted=false;
        try{
            if(surface==null||!surface.isValid())throw new IllegalArgumentException("画面 Surface 不可用");
            for(Window w:windows.values())if(w.windowId.equals(component)){
                Surface old=w.surface;w.display.setSurface(null);w.display.setSurface(surface);w.surface=surface;adopted=true;old.release();
                result.putBoolean("success",true);result.putBoolean("reusedTask",true);result.putInt("displayId",w.display.getDisplay().getDisplayId());
                result.putInt("bufferEdge",w.area==null?0:LiveCanvasArea.BUFFER_EDGE);
                result.putString("detail","已恢复应用画面");System.out.println("REBIND display="+w.display.getDisplay().getDisplayId()+" "+component);return result;
            }
            throw new IllegalStateException("应用窗口已移出");
        }catch(Exception e){result.putString("detail",e.toString());Log.e("WorkbenchLive","Surface rebind failed",e);return result;}
        finally{if(!adopted&&surface!=null)surface.release();}
    }

    Bundle resizeOne(int id,int width,int height,int density){
        Bundle result=new Bundle();Window w=windows.get(id);
        try{
            if(w==null||width<320||height<320||width>3200||height>3200||density<100||density>640)throw new IllegalArgumentException("窗口参数无效");
            if(w.width!=width||w.height!=height||w.density!=density)result.putLong("frameAfterNanos",resizeWindow(w,width,height,density));
            w.width=width;w.height=height;w.density=density;result.putBoolean("success",true);
        }catch(Exception e){result.putString("detail",e.toString());}return result;
    }
    Bundle resize(int width,int height,int density){
        Bundle result=new Bundle();List<Window> changed=new ArrayList<>();
        try{
            if(width<320||height<320||width>3200||height>3200||density<100||density>640)throw new IllegalArgumentException("画布尺寸无效");
            for(Window w:windows.values()){resizeWindow(w,width,height,density);changed.add(w);}
            for(Window w:changed){w.width=width;w.height=height;w.density=density;}
            result.putBoolean("success",true);
            System.out.println("RESIZE windows="+changed.size()+" "+width+"x"+height+" density="+density);
        }catch(Exception e){
            for(Window w:changed)try{resizeWindow(w,w.width,w.height,w.density);}catch(Exception rollback){Log.e("WorkbenchLive","Resize rollback failed",rollback);}
            result.putString("detail",e.toString());Log.e("WorkbenchLive","Resize failed",e);
        }
        return result;
    }
    private long resizeWindow(Window window,int width,int height,int density)throws Exception{
        if(window.area!=null)return window.area.resize(width,height,density);
        window.display.resize(width,height,density);return 0;
    }
    synchronized String close() {
        long identity=android.os.Binder.clearCallingIdentity();
        try {
            // Releasing a populated display brings its apps in front of Home. First hand our
            // tasks back while hidden, then reveal them behind the current physical foreground.
            Map<Integer,Integer> owned=new java.util.HashMap<>();
            java.util.Set<Object> inputTokens=new java.util.HashSet<>();
            for(Map.Entry<Integer,Window> entry:windows.entrySet()){
                owned.put(entry.getKey(),entry.getValue().user);
                inputTokens.addAll(entry.getValue().inputTokens);
            }
            if(!owned.isEmpty())try{LiveTaskHandoff.returnBehind(owned,inputTokens);}
            catch(Exception|LinkageError error){Log.w("WorkbenchLive","Background handoff unavailable; returning displays normally",error);}
            for (int id : new ArrayList<>(windows.keySet())) {
                try { remove(id,false); } catch (Exception e) { Log.e("WorkbenchLive","Release failed",e); }
            }
            host=-1;
            return "工作台已退出";
        } finally { android.os.Binder.restoreCallingIdentity(identity); }
    }
}
