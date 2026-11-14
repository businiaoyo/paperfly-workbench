package cn.paperfly.workbench;

import android.os.*;
import android.view.*;
import cn.paperfly.workbench.model.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Private, finite split-layout regression using the current scene, restored through its draft. */
final class SplitValidation {
    private final StageActivity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final JSONArray checks=new JSONArray();
    private final JSONObject result=new JSONObject();
    private WorkspaceDefinition baseline;
    private long deadline=SystemClock.uptimeMillis()+120000;
    private Map<Integer,String> resources,canvases;
    private SplitValidation(StageActivity activity){this.activity=activity;}
    static void attach(StageActivity activity){File arm=new File(activity.getFilesDir(),"split-validation-pending");
        if(arm.isFile()&&arm.delete()){SplitValidation test=new SplitValidation(activity);test.ready(test::start);}}
    private Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private Object call(String name,Object... args)throws Exception{for(Method m:StageActivity.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(activity,args);}throw new NoSuchMethodException(name);}
    @SuppressWarnings("unchecked") private List<Object> windows()throws Exception{return new ArrayList<>((List<Object>)field(activity,"windows"));}
    private WorkspaceDraft draft()throws Exception{return (WorkspaceDraft)field(activity,"draft");}
    private CardSession slots()throws Exception{return (CardSession)field(activity,"slots");}
    private interface Step{void run()throws Exception;}
    private void later(long delay,Step next){main.postDelayed(()->{try{next.run();}catch(Exception error){fail(error);}},delay);}
    private void ready(Step next){later(350,()->{if(SystemClock.uptimeMillis()>deadline)throw new IOException("Timed out");
        if(!(Boolean)field(activity,"started")||(Boolean)field(activity,"resizing")||(Boolean)field(activity,"animating")){ready(next);return;}
        for(Object w:windows())if(!(Boolean)field(w,"ready")){ready(next);return;}next.run();});}
    private void require(boolean value,String message)throws Exception{if(!value)throw new IOException(message);checks.put(message);save();}
    private Map<Integer,String> identities(boolean canvas)throws Exception{Map<Integer,String> map=new LinkedHashMap<>();for(Object w:windows())map.put((Integer)field(w,"id"),canvas?field(w,"canvasWidth")+"x"+field(w,"canvasHeight")+"@"+field(w,"canvasDensity"):field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface")));return map;}
    private WorkspaceCard current()throws Exception{return slots().card(slots().primary());}
    private SplitDivider divider()throws Exception{return (SplitDivider)field(activity,"splitDivider");}
    @SuppressWarnings("unchecked") private int handleCount()throws Exception{return ((List<Object>)field(divider(),"handles")).size();}
    private void start()throws Exception{
        baseline=draft().snapshot();result.put("phase","running");require(baseline.cards.get(0).apps.size()>1,"Existing split scene available");
        resources=identities(false);checkFill();require(handleCount()==current().apps.size()-1,"One handle for every adjacent pair");
        later(600,()->boundary(0));
    }
    private Map<String,float[]> rectangles()throws Exception{
        Map<String,float[]> values=new LinkedHashMap<>();for(Object object:windows()){View v=(View)object;
            values.put((String)field(object,"windowId"),new float[]{v.getTranslationX(),v.getTranslationY(),(Integer)field(object,"canvasWidth")*v.getScaleX(),(Integer)field(object,"canvasHeight")*v.getScaleY()});}return values;
    }
    private boolean close(float[] a,float[] b){for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i])>3)return false;return true;}
    private void noCover()throws Exception{for(Object w:windows()){View cover=(View)field(w,"cover");require(cover.getVisibility()!=View.VISIBLE||cover.getAlpha()<.05f,"Live edit does not cover a ready window");}}
    private void boundary(int index)throws Exception{
        if(index>=current().apps.size()-1){cancelCheck();return;}
        WorkspaceCard before=current();canvases=identities(true);Map<String,float[]> rects=rectangles();drag(index,.05f,false);
        require(divider().dragging(),"Divider "+index+" owns pointer stream");
        require(current().weights.equals(before.weights),"Live preview leaves persisted draft unchanged");
        require(resources.equals(identities(false)),"Live preview retains all displays and Surfaces");
        Map<String,float[]> moved=rectangles();
        for(int i=0;i<before.apps.size();i++){String id=before.apps.get(i).id;boolean changed=!close(rects.get(id),moved.get(id));
            require(changed==(i==index||i==index+1),"Divider "+index+" changes only adjacent window "+i+" before release");}
        noCover();result.put("phase","dragging-"+index);save();
        later(1800,()->{
            finishDrag(false);noCover();ready(()->{
                WorkspaceCard after=current();require(!after.weights.equals(before.weights),"Release commits pair weights");require(resources.equals(identities(false)),"Commit preserves live resources");
                Map<Integer,String> resized=identities(true);for(Object w:windows()){
                    String windowId=(String)field(w,"windowId");if(!windowId.equals(before.apps.get(index).id)&&!windowId.equals(before.apps.get(index+1).id)){
                        int id=(Integer)field(w,"id");require(canvases.get(id).equals(resized.get(id)),"Other window canvas unchanged after pair resize");}}
                noCover();checkFill();boundary(index+1);
            });
        });
    }
    private void cancelCheck()throws Exception{
        WorkspaceCard before=current();Map<String,float[]> rects=rectangles();drag(current().apps.size()-2,-.04f,true);
        require(before.weights.equals(current().weights),"Cancellation preserves all ratios");Map<String,float[]> after=rectangles();
        for(String id:rects.keySet())require(close(rects.get(id),after.get(id)),"Cancellation restores live window bounds");
        Object side=null;for(Object w:windows())if(!slots().primaryCard((Integer)field(w,"id"))){side=w;break;}
        if(side!=null){slots().mergeIntoPrimary((Integer)field(side,"id"));call("groupChanged");ready(()->{
            require(handleCount()==current().apps.size()-1,"Newly merged member adds its adjacent handle");
            int last=current().apps.size()-2;WorkspaceCard joined=current();drag(last,-.03f,false);
            require(divider().dragging(),"New last divider accepts drag");finishDrag(false);ready(()->{require(!joined.weights.equals(current().weights),"New last divider commits independently");presentationChecks();});
        });}else presentationChecks();
    }
    private void presentationChecks()throws Exception{
        draft().replace(baseline);call("groupChanged");ready(()->{
            canvases=identities(true);call("setFullscreen",true);later(800,()->{same("Workspace fullscreen");call("setFullscreen",false);
                later(800,()->{same("Workspace fullscreen exit");call("enterAppFullscreen");later(800,()->{same("App fullscreen");call("leaveAppFullscreen");
                    later(800,()->{same("App fullscreen exit");swap();});});});});
        });
    }
    private void same(String label)throws Exception{require(resources.equals(identities(false))&&canvases.equals(identities(true)),label+" preserves displays, Surfaces and canvases");}
    private void checkFill()throws Exception{
        StageLayout layout=(StageLayout)call("layout");
        for(Object w:windows())if(slots().primaryCard((Integer)field(w,"id"))){StageLayout.Bounds pane=(StageLayout.Bounds)call("memberBounds",w,layout.target(0,0));StageLayout.Bounds shown=(StageLayout.Bounds)call("windowTarget",w);
            require(shown.width()*shown.height()/(pane.width()*pane.height())>.985f,"Normal split pane fills at least 98.5% of allocation");}
    }
    private long down;private float endX,endY;
    private void event(int action,float x,float y)throws Exception{View scene=(View)field(activity,"scene");int[] pos=new int[2];scene.getLocationOnScreen(pos);MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+pos[0],y+pos[1],0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);activity.dispatchTouchEvent(e);e.recycle();}
    private void drag(int index,float delta,boolean cancel)throws Exception{
        SplitDivider divider=divider();StageLayout.Bounds b=(StageLayout.Bounds)field(divider,"bounds");boolean horizontal=(Boolean)field(divider,"horizontal");float gap=(Float)field(divider,"gap");
        float available=(horizontal?b.width():b.height())-gap*(current().apps.size()-1);
        float start=PaneLayout.divider(b,current().weights,index,horizontal,gap),end=start+available*delta;
        float x=horizontal?start:(b.left+b.right)/2,y=horizontal?(b.top+b.bottom)/2:start;endX=horizontal?end:x;endY=horizontal?y:end;
        down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y);event(MotionEvent.ACTION_MOVE,endX,endY);
        require(divider.dragging(),"Adjacent divider owns pointer stream");if(cancel)finishDrag(true);
    }
    private void finishDrag(boolean cancel)throws Exception{event(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,endX,endY);}
    private void swap()throws Exception{
        Object original=call("primary"),side=null;for(Object w:windows())if(!slots().primaryCard((Integer)field(w,"id"))){side=w;break;}
        if(side==null){finish();return;}call("select",side);later(800,()->{same("Main/side swap");call("select",original);later(800,()->{same("Swap back");call("editGroup");
            later(350,()->{((android.app.Dialog)field(activity,"activeDialog")).dismiss();later(350,()->{same("Split panel dismissal");finish();});});});});
    }
    private void finish()throws Exception{restore();ready(()->{require(draft().snapshot().cards.get(0).weights.equals(baseline.cards.get(0).weights),"Original ratios restored");result.put("phase","complete");save();});}
    private void restore()throws Exception{if(baseline!=null){draft().replace(baseline);call("groupChanged");}}
    private void fail(Exception error){try{result.put("phase","failed").put("error",error.toString());restore();save();}catch(Exception ignored){}}
    private void save()throws Exception{result.put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"split-validation-result.json"))){out.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}}
}
