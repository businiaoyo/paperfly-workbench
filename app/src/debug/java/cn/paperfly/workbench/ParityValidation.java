package cn.paperfly.workbench;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.TextureView;
import cn.paperfly.workbench.model.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Finite, private-file-armed debug QA. No exported receiver, RPC or production implementation. */
final class ParityValidation {
    private static final String PROBE="cn.paperfly.workbench.probe/cn.paperfly.workbench.probe.ProbeActivity";
    private final StageActivity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final File dir;
    private final JSONObject result=new JSONObject();
    private final JSONArray checks=new JSONArray();
    private WorkspaceDefinition baseline;
    private Object first,second;
    private Map<Integer,String> before;
    private long deadline;
    private boolean interactive;
    private ParityValidation(StageActivity activity){this.activity=activity;dir=new File(activity.getFilesDir(),"parity-validation");dir.mkdirs();}
    static void attach(StageActivity activity){File arm=new File(activity.getFilesDir(),"parity-validation-pending");
        if(!arm.isFile())return;String mode="";try{mode=new String(java.nio.file.Files.readAllBytes(arm.toPath()),StandardCharsets.UTF_8).trim();}catch(IOException ignored){}if(!arm.delete())return;ParityValidation validation=new ParityValidation(activity);validation.interactive="hold".equals(mode);
        validation.deadline=SystemClock.uptimeMillis()+90000;validation.waitReady(validation::start);
    }
    private Object field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object call(String name,Object... arguments)throws Exception {
        for(Method m:StageActivity.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==arguments.length){m.setAccessible(true);return m.invoke(activity,arguments);}throw new NoSuchMethodException(name);
    }
    @SuppressWarnings("unchecked") private List<Object> windows()throws Exception{return new ArrayList<>((List<Object>)field(activity,"windows"));}
    private WorkspaceDraft draft()throws Exception{return (WorkspaceDraft)field(activity,"draft");}
    private CardSession slots()throws Exception{return (CardSession)field(activity,"slots");}
    private int id(Object w)throws Exception{return (Integer)field(w,"id");}
    private StagePresentation presentation()throws Exception{return (StagePresentation)field(activity,"presentation");}
    private interface Step{void run()throws Exception;}
    private void later(long ms,Step action){main.postDelayed(()->{try{action.run();}catch(Exception error){fail(error);}},ms);}
    private void waitReady(Step next){later(250,()->{
        if(SystemClock.uptimeMillis()>deadline)throw new IOException("Timed out waiting for live windows");
        if(!(Boolean)field(activity,"started")||(Boolean)field(activity,"resizing")){waitReady(next);return;}
        for(Object w:windows())if(!(Boolean)field(w,"ready")||(Integer)field(w,"frames")==0){waitReady(next);return;}
        next.run();
    });}
    private void start()throws Exception {
        baseline=draft().snapshot();result.put("phase","running").put("started",System.currentTimeMillis());save();
        call("addApp",PROBE);waitReady(()->{
            for(Object w:windows())if(PROBE.equals(field(w,"component")))first=w;
            require(first!=null,"First probe opened");call("select",first);call("newInstance",first);
            waitReady(()->{for(Object w:windows())if(PROBE.equals(field(w,"component"))&&w!=first)second=w;
                require(second!=null,"Second probe opened");require(!field(first,"display").equals(field(second,"display")),"Distinct displays for same application");
                picture(first,"instance-one");picture(second,"instance-two");result.put("firstDisplay",field(first,"display")).put("secondDisplay",field(second,"display"));
                call("select",first);slots().mergeIntoPrimary(id(second));call("groupChanged");waitReady(this::layoutChecks);
            });
        });
    }
    private void layoutChecks()throws Exception {
        require(slots().card(id(first)).apps.size()==2,"Grouped instances retain separate identities");
        call("toggleFreeform");waitReady(()->{call("arrangeGroup",false);waitReady(()->{
            before=identities();picture(first,"tiled-one");picture(second,"tiled-two");if(interactive){result.put("phase","interactive");save();deadline=SystemClock.uptimeMillis()+300000;pollFinish();return;}call("setFullscreen",true);
            later(900,()->{same("Workspace fullscreen preserves all displays, canvases and surfaces");call("setFullscreen",false);
                later(900,()->{same("Workspace fullscreen exit preserves surfaces");call("enterAppFullscreen");
                    later(900,()->{same("App fullscreen preserves all displays, canvases and surfaces");call("leaveAppFullscreen");
                        later(900,()->{same("App fullscreen exit preserves surfaces");
                            later(900,()->{call("select",first);
                                later(700,()->{call("minimizeWindow",first);later(700,()->{same("Minimize preserves surfaces");require(presentation().minimized(id(first)),"Minimized state");require(slots().primary()==id(second),"Minimize keeps focus on remaining group member");call("select",first);
                                    later(700,()->{same("Restore preserves surfaces");require(!presentation().minimized(id(first)),"Restore clears minimized state");call("minimizeWindow",second);require(slots().primary()==id(first),"Minimizing an unfocused window keeps focus");presentation().restore(id(second));call("arrange",false);call("toggleRail");
                                        later(700,()->{same("Hide sidebar preserves canvases");call("toggleRail");call("windowActions",first);
                                            later(500,()->{android.app.Dialog dialog=(android.app.Dialog)field(activity,"activeDialog");dialog.dismiss();later(500,()->{same("Dialog dismissal preserves surfaces");detachCheck();});});
                                        });
                                    });
                                });});
                            });
                        });
                    });
                });
            });
        });});
    }
    private void pollFinish(){later(500,()->{File finish=new File(activity.getFilesDir(),"parity-validation-finish");if(finish.isFile()||SystemClock.uptimeMillis()>deadline){finish.delete();result.put("phase","interactive-restored");restore();save();}else pollFinish();});}
    private void detachCheck()throws Exception {
        String group=slots().card(id(first)).id;require(slots().detach(id(second)),"Detach succeeds");call("groupChanged");waitReady(()->{
            require(slots().card(id(first)).id.equals(group),"Remaining group keeps stable ID");require(slots().card(id(second)).apps.size()==1,"Detached instance remains independent");
            result.put("phase","complete").put("completed",System.currentTimeMillis());restore();save();
        });
    }
    private Map<Integer,String> identities()throws Exception {Map<Integer,String> map=new LinkedHashMap<>();for(Object w:windows())map.put(id(w),field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface"))+":"+field(w,"canvasWidth")+"x"+field(w,"canvasHeight"));return map;}
    private void same(String message)throws Exception {require(before.equals(identities()),message);}
    private void require(boolean condition,String message)throws Exception {if(!condition)throw new IOException(message);checks.put(message);save();}
    private void picture(Object w,String name)throws Exception {Bitmap bitmap=((TextureView)field(w,"texture")).getBitmap();if(bitmap==null)throw new IOException("Missing live bitmap");try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private void restore()throws Exception {if(baseline==null)return;for(Object w:windows())if(PROBE.equals(field(w,"component")))call("remove",w);draft().replace(baseline);call("groupChanged");}
    private void fail(Exception error){try{result.put("phase","failed").put("error",error.toString());restore();save();}catch(Exception restoration){try{result.put("restoreError",restoration.toString());save();}catch(Exception ignored){}}}
    private void save()throws Exception {result.put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(dir,"result.json"))){out.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}}
}
