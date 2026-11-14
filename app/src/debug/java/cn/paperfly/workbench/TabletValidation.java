package cn.paperfly.workbench;

import android.os.*;
import android.view.MotionEvent;
import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.WorkspaceCodec;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Finite QA armed only by a private debug file. External steps act on the disposable probe only. */
final class TabletValidation {
    private static final String PROBE="cn.paperfly.workbench.probe/cn.paperfly.workbench.probe.ProbeActivity";
    private final StageActivity activity;private final Handler main=new Handler(Looper.getMainLooper());private final File dir;
    private JSONObject result=new JSONObject();private JSONArray checks=new JSONArray();private WorkspaceDefinition baseline;
    private Map<Integer,String> before;private long deadline=SystemClock.uptimeMillis()+600000;
    private TabletValidation(StageActivity activity){this.activity=activity;dir=new File(activity.getFilesDir(),"tablet-validation");dir.mkdirs();}
    static void attach(StageActivity activity){File arm=new File(activity.getFilesDir(),"tablet-validation-pending");if(!arm.isFile())return;
        try{String mode=read(arm);if(!arm.delete())return;TabletValidation qa=new TabletValidation(activity);
            if(mode.equals("resume")||mode.equals("restore")){qa.baseline=new WorkspaceCodec().decode(read(new File(qa.dir,"baseline.json"))).current;qa.result=new JSONObject(read(new File(qa.dir,"result.json")));qa.checks=qa.result.getJSONArray("checks");}
            qa.waitReady(()->{if(mode.equals("restore")){qa.restore();qa.phase("restored");}else if(mode.equals("resume"))qa.resumed();else qa.start();});
        }catch(Exception error){android.util.Log.e("TabletValidation","Cannot start",error);}
    }
    private static String read(File file)throws IOException{return new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).trim();}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object call(String name,Object... args)throws Exception{for(Method m:StageActivity.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(activity,args);}throw new NoSuchMethodException(name);}
    @SuppressWarnings("unchecked") private List<Object> windows()throws Exception{return new ArrayList<>((List<Object>)field(activity,"windows"));}
    private List<Object> probes()throws Exception{List<Object> result=new ArrayList<>();for(Object w:windows())if(PROBE.equals(field(w,"component")))result.add(w);return result;}
    private WorkspaceDraft draft()throws Exception{return (WorkspaceDraft)field(activity,"draft");}
    private StagePresentation presentation()throws Exception{return (StagePresentation)field(activity,"presentation");}
    private CardSession slots()throws Exception{return (CardSession)field(activity,"slots");}
    private int id(Object w)throws Exception{return (Integer)field(w,"id");}
    private interface Step{void run()throws Exception;}private interface Condition{boolean ready()throws Exception;}
    private void later(long delay,Step action){main.postDelayed(()->{try{if(SystemClock.uptimeMillis()>deadline)throw new IOException("QA timeout");action.run();}catch(Exception error){fail(error);}},delay);}
    private void until(Condition condition,Step next){later(300,()->{if(condition.ready())next.run();else until(condition,next);});}
    private void waitReady(Step next){until(()->{if(!(Boolean)field(activity,"started")||(Boolean)field(activity,"resizing"))return false;for(Object w:windows())if(!(Boolean)field(w,"ready")||(Integer)field(w,"frames")==0)return false;return true;},next);}
    private void start()throws Exception{
        baseline=draft().snapshot();write("baseline.json",new WorkspaceCodec().encode(new WorkspaceLibrary(baseline,Collections.emptyList())));phase("running");before=identities(false);
        int count=windows().size();call("updateOptions",baseline.options.pin(PROBE,true).autoHide(true));require(windows().size()==count,"Pinning does not launch an application");
        later(3300,()->{require(!(Boolean)field(activity,"dockShown"),"Dock auto-hides");same("Dock auto-hide preserves canvases and surfaces",false);
            ((DockAutoHide)field(activity,"dockVisibility")).reveal();later(300,()->{require((Boolean)field(activity,"dockShown"),"Dock can be revealed");same("Dock reveal preserves canvases and surfaces",false);addProbe(0);});});
    }
    private void addProbe(int count)throws Exception{if(count==3){require(probes().size()==3,"Three independent probe windows opened");before=identities(false);Object probe=probes().get(0);call("select",probe);gestures(probe);return;}call("addTarget",count==0?new AppTarget(PROBE):new AppTarget(UUID.randomUUID().toString(),PROBE));waitReady(()->addProbe(count+1));}
    private void gestures(Object probe)throws Exception{
        later(600,()->{int initial=slots().primary();swipe(4,-230,0);later(600,()->{require(slots().primary()!=initial,"Four-finger swipe switches window through host input");swipe(5,230,0);
            later(600,()->{require(slots().primary()==initial,"Five-finger reverse swipe returns to window");swipe(4,0,-230);later(600,()->{require(slots().primary()==initial,"Four-finger upward gesture leaves workspace unchanged");same("Touch gestures preserve canvases and surfaces",false);call("select",probe);persist(probe);});});});});
    }
    private void swipe(int n,float dx,float dy){
        long down=SystemClock.uptimeMillis();float density=activity.getResources().getDisplayMetrics().density;
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[n];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=1100+i*65;coords[i].y=900+(i%2)*80;coords[i].pressure=1;coords[i].size=.1f;}
        send(down,MotionEvent.ACTION_DOWN,1,props,coords);for(int i=1;i<n;i++)send(down,MotionEvent.ACTION_POINTER_DOWN|(i<<8),i+1,props,coords);
        for(int step=0;step<5;step++){for(MotionEvent.PointerCoords c:coords){c.x+=dx*density/5;c.y+=dy*density/5;}send(down,MotionEvent.ACTION_MOVE,n,props,coords);}
        for(int i=n-1;i>0;i--)send(down,MotionEvent.ACTION_POINTER_UP|(i<<8),i+1,props,coords);send(down,MotionEvent.ACTION_UP,1,props,coords);
    }
    private void send(long down,int action,int count,MotionEvent.PointerProperties[] props,MotionEvent.PointerCoords[] coords){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);activity.dispatchTouchEvent(e);e.recycle();}
    private void persist(Object probe)throws Exception{
        call("minimizeWindow",probe);require(draft().snapshot().options.minimizedWindows.contains(field(probe,"windowId")),"Minimize is persisted by stable window identity");same("Minimize preserves live resources",false);
        result.put("minimizedId",field(probe,"windowId"));result.put("probeIds",new JSONArray(probeIds()));phase("restart-ready");call("returnHome");
    }
    private void resumed()throws Exception{
        require(draft().snapshot().options.pinnedApps.contains(PROBE)&&draft().snapshot().options.dockAutoHide,"Dock preferences survive process restart");String minimized=result.getString("minimizedId");Object restored=null;for(Object w:probes())if(minimized.equals(field(w,"windowId")))restored=w;
        require(restored!=null&&presentation().minimized(id(restored)),"Minimized window is restored after process restart");require(new JSONArray(probeIds()).toString().equals(result.getJSONArray("probeIds").toString()),"All window identities survive process restart");call("select",restored);before=identities(true);phase("package-disable-ready");waitUnavailable(false);
    }
    private List<String> probeIds()throws Exception{List<String> ids=new ArrayList<>();for(Object w:probes())ids.add((String)field(w,"windowId"));return ids;}
    private void waitUnavailable(boolean uninstall){until(()->{for(Object w:probes())if(!(Boolean)field(w,"failed")||(Boolean)field(w,"ready"))return false;return probes().size()==3;},()->{
        require(probes().size()==3,uninstall?"Uninstall retains all configured instances":"Disabled package retains all configured instances");same("Unavailable probe leaves other windows intact",true);phase(uninstall?"package-install-ready":"package-enable-ready");
        waitReady(()->{require(new JSONArray(probeIds()).toString().equals(result.getJSONArray("probeIds").toString()),"Available package recovers original stable window identities");same("Recovery leaves other canvases and surfaces intact",true);
            if(uninstall){restore();later(1000,()->phase("complete"));}else{phase("package-uninstall-ready");waitUnavailable(true);}});
    });}
    private Map<Integer,String> identities(boolean originalsOnly)throws Exception{Map<Integer,String> ids=new LinkedHashMap<>();for(Object w:windows())if(!originalsOnly||!PROBE.equals(field(w,"component")))ids.put(id(w),field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface"))+":"+field(w,"canvasWidth")+"x"+field(w,"canvasHeight"));return ids;}
    private void same(String message,boolean originalsOnly)throws Exception{require(before.equals(identities(originalsOnly)),message);}
    private void require(boolean value,String message)throws Exception{if(!value)throw new IOException(message);checks.put(message);save();}
    private void restore()throws Exception{if(baseline==null)return;for(Object w:probes())call("remove",w);draft().replace(baseline);call("groupChanged");}
    private void fail(Exception error){try{result.put("error",error.toString());restore();phase("failed");}catch(Exception restoration){android.util.Log.e("TabletValidation","Restore failed",restoration);}}
    private void phase(String value)throws Exception{result.put("phase",value);save();}
    private void save()throws Exception{result.put("checks",checks);write("result.json",result.toString(2));}
    private void write(String name,String value)throws IOException{try(FileOutputStream out=new FileOutputStream(new File(dir,name))){out.write(value.getBytes(StandardCharsets.UTF_8));}}
}
