package cn.paperfly.workbench;

import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.WorkspaceCodec;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Private finite regression: closes only existing split members, then restores this run's draft. */
final class WindowCloseValidation {
    private final StageActivity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final JSONArray checks=new JSONArray();
    private final JSONObject result=new JSONObject();
    private final long deadline=SystemClock.uptimeMillis()+120000;
    private WorkspaceDefinition baseline,original;
    private Map<String,String> resources,canvases;
    private WindowCloseValidation(StageActivity activity){this.activity=activity;}
    static void attach(StageActivity activity){
        File arm=new File(activity.getFilesDir(),"window-close-validation-pending");
        if(arm.isFile()&&arm.delete()){WindowCloseValidation test=new WindowCloseValidation(activity);test.ready(test::start);}
    }
    private Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private Object call(String name,Object... args)throws Exception{for(Method m:StageActivity.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(activity,args);}throw new NoSuchMethodException(name);}
    @SuppressWarnings("unchecked") private List<Object> windows()throws Exception{return new ArrayList<>((List<Object>)field(activity,"windows"));}
    private WorkspaceDraft draft()throws Exception{return (WorkspaceDraft)field(activity,"draft");}
    private CardSession slots()throws Exception{return (CardSession)field(activity,"slots");}
    private interface Step{void run()throws Exception;}
    private void later(long delay,Step next){main.postDelayed(()->{try{next.run();}catch(Exception e){fail(e);}},delay);}
    private void ready(Step next){later(350,()->{
        if(SystemClock.uptimeMillis()>deadline)throw new IOException("Timed out");
        if(!(Boolean)field(activity,"started")||(Boolean)field(activity,"resizing")||(Boolean)field(activity,"animating")){ready(next);return;}
        for(Object w:windows())if(!(Boolean)field(w,"ready")){ready(next);return;}next.run();
    });}
    private void require(boolean ok,String message)throws Exception{if(!ok)throw new IOException(message);checks.put(message);save();}
    private Object window(String id)throws Exception{for(Object w:windows())if(id.equals(field(w,"windowId")))return w;return null;}
    private Map<String,String> identities(boolean canvas)throws Exception{Map<String,String> map=new LinkedHashMap<>();for(Object w:windows())map.put((String)field(w,"windowId"),canvas?field(w,"canvasWidth")+"x"+field(w,"canvasHeight")+"@"+field(w,"canvasDensity"):field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface")));return map;}
    private void same(String label)throws Exception{require(resources.equals(identities(false))&&canvases.equals(identities(true)),label+" preserves displays, Surfaces and canvases");}
    private void controls(String label)throws Exception{
        StagePresentation mode=(StagePresentation)field(activity,"presentation");
        ViewGroup scene=(ViewGroup)field(activity,"scene");
        for(Object w:windows()){
            int id=(Integer)field(w,"id");
            if(slots().primaryCard(id)&&mode.showsWindow(id)&&!slots().card(id).freeform){
                StageLayout.Bounds pane=(StageLayout.Bounds)call("windowTarget",w);
                float x=pane.right-WorkbenchUi.dp(activity,20),y=pane.top+WorkbenchUi.dp(activity,20);
                boolean covered=false;
                for(int i=0;i<scene.getChildCount();i++){
                    View v=scene.getChildAt(i);if(v==w||!v.isShown()||v.getAlpha()==0||!v.isClickable()||v.getZ()<=((View)w).getZ())continue;
                    covered|=x>=v.getX()&&x<v.getX()+v.getWidth()&&y>=v.getY()&&y<v.getY()+v.getHeight();
                }
                require(!covered,label+" content corner has no overlaid clickable control: "+id);
            }
        }
    }
    private View find(View root,String value){
        if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())
                ||root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=find(((ViewGroup)root).getChildAt(i),value);if(result!=null)return result;}
        return null;
    }
    @SuppressWarnings("unchecked") private View handle(int boundary)throws Exception{return (View)((List<Object>)field(field(activity,"splitDivider"),"handles")).get(boundary);}
    private void menus(int boundary,Step next)throws Exception{
        WorkspaceCard card=draft().snapshot().cards.get(0);
        if(boundary>=card.apps.size()-1){next.run();return;}
        WorkspaceDefinition before=draft().snapshot();int focus=slots().primary();click(handle(boundary));
        later(350,()->{
            WorkbenchDialog dialog=(WorkbenchDialog)field(activity,"activeDialog");require(dialog!=null&&dialog.isShowing(),"Divider "+boundary+" tap opens actions");
            for(int i=0;i<card.apps.size();i++){
                String label="关闭 "+field(window(card.apps.get(i).id),"name")+" 窗口";
                require((find(dialog.body,label)!=null)==(i==boundary||i==boundary+1),"Divider "+boundary+" targets only its adjacent member "+i);
            }
            same("Divider menu");require(slots().primary()==focus&&draft().snapshot()==before,"Opening actions preserves focus and draft");
            result.put("phase","menu-open-"+boundary);save();later(1200,()->{dialog.onBackPressed();later(250,()->{
                require(slots().primary()==focus&&draft().snapshot()==before,"Dismissing actions preserves focus and draft");same("Divider menu dismissed");menus(boundary+1,next);
            });});
        });
    }
    private void start()throws Exception{
        File recovery=new File(activity.getFilesDir(),"window-close-validation-baseline.json");
        if(recovery.isFile()){
            original=new WorkspaceCodec().decode(new String(java.nio.file.Files.readAllBytes(recovery.toPath()),StandardCharsets.UTF_8)).current;
            restore();ready(this::prepare);return;
        }
        original=draft().snapshot();
        try(FileOutputStream out=new FileOutputStream(recovery)){out.write(new WorkspaceCodec().encode(new WorkspaceLibrary(original,Collections.emptyList())).getBytes(StandardCharsets.UTF_8));}
        prepare();
    }
    private void prepare()throws Exception{
        baseline=original;
        if(original.cards.get(0).apps.size()!=3||original.cards.size()<2){
            List<AppTarget> apps=new ArrayList<>();for(WorkspaceCard card:original.cards)apps.addAll(card.apps);
            // Only already configured/recent apps; never accept third-party first-use dialogs.
            for(String component:original.options.recentApps){if(apps.size()>=4)break;
                AppTarget app=new AppTarget(component);if(apps.stream().noneMatch(a->a.id.equals(app.id)))apps.add(app);}
            if(apps.size()<4)throw new IOException("Need four configured/recent apps for the finite close regression");
            List<WorkspaceCard> cards=new ArrayList<>();cards.add(new WorkspaceCard(original.cards.get(0).id,apps.subList(0,3),apps.get(0).id));
            for(int i=3;i<apps.size();i++)cards.add(new WorkspaceCard("close-validation-"+i,Collections.singletonList(apps.get(i)),apps.get(i).id));
            baseline=original.withCards(cards);
            for(AppTarget app:apps)if(window(app.id)==null)call("addTarget",app);
            draft().replace(baseline);call("groupChanged");
        }
        ready(()->later(500,this::begin));
    }
    private void begin()throws Exception{
        resources=identities(false);canvases=identities(true);
        require(baseline.cards.get(0).apps.size()==3&&baseline.cards.size()>1,"Three-member split and side card available");
        result.put("phase","running");controls("Normal");dragChecks();
        menus(0,()->{call("setFullscreen",true);later(800,()->{controls("Workspace fullscreen");same("Workspace fullscreen");menus(0,()->{call("setFullscreen",false);
            later(800,()->{call("enterAppFullscreen");later(800,()->{controls("App fullscreen");same("App fullscreen");call("leaveAppFullscreen");later(800,this::swap);});});});
        });});
    }
    private void dragChecks()throws Exception{
        SplitDivider divider=(SplitDivider)field(activity,"splitDivider");boolean horizontal=(Boolean)field(divider,"horizontal");
        WorkspaceDefinition before=draft().snapshot();WorkspaceCard card=before.cards.get(0);
        for(int boundary=0;boundary<card.apps.size()-1;boundary++){
            View handle=handle(boundary),member=(View)window(card.apps.get(boundary).id);int[] location=new int[2];handle.getLocationOnScreen(location);
            float x=location[0]+handle.getWidth()/2f,y=location[1]+handle.getHeight()/2f;
            float scale=horizontal?member.getScaleX():member.getScaleY();long down=SystemClock.uptimeMillis();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE}){
                if(action==MotionEvent.ACTION_MOVE){if(horizontal)x+=WorkbenchUi.dp(activity,24);else y+=WorkbenchUi.dp(activity,24);}
                MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);activity.dispatchTouchEvent(event);event.recycle();
            }
            require(divider.dragging()&&Math.abs((horizontal?member.getScaleX():member.getScaleY())-scale)>.001f,"Divider "+boundary+" drag changes live pane before release");
            require(field(activity,"activeDialog")==null,"Dragging does not open actions");same("Drag preview");
            MotionEvent cancel=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,x,y,0);cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);activity.dispatchTouchEvent(cancel);cancel.recycle();
            require(!divider.dragging()&&draft().snapshot()==before,"Canceled drag restores proportions without a menu");
        }
    }
    private void swap()throws Exception{
        Object original=call("primary"),side=window(baseline.cards.get(1).focusedWindowId);call("select",side);
        later(800,()->{controls("Side promoted");same("Main/side swap");call("select",original);later(800,()->{
            controls("Split restored");same("Swap back");click((View)field(activity,"addCard"));
            later(400,()->{AppPanel panel=(AppPanel)field(activity,"appPanel");require(panel!=null&&panel.isShowing(),"Sidebar trailing plus opens picker");panel.dismiss();later(400,()->{
                same("Picker dismissal");closeMiddle();
            });});
        });});
    }
    private void click(View view){
        int[] p=new int[2];view.getLocationOnScreen(p);long down=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p[0]+view.getWidth()/2f,p[1]+view.getHeight()/2f,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);activity.dispatchTouchEvent(event);event.recycle();
        }
    }
    private void closeMiddle()throws Exception{
        WorkspaceCard card=baseline.cards.get(0);String id=card.apps.get(1).id;
        // Explicitly keep a different member focused: close must bind to the clicked window.
        Object focus=window(card.apps.get(2).id);call("select",focus);
        later(600,()->closeAndCheck(id,"middle",()->{
            WorkspaceCard remaining=draft().snapshot().cards.get(0);
            require(remaining.id.equals(card.id)&&remaining.apps.equals(Arrays.asList(card.apps.get(0),card.apps.get(2))),"Middle close preserves group ID and remaining order");
            require(remaining.focusedWindowId.equals(card.apps.get(2).id),"Closing unfocused member keeps current focus");
            require(Math.abs(remaining.weights.get(0)/remaining.weights.get(1)-card.weights.get(0)/card.weights.get(2))<.0001f,"Remaining relative proportions preserved");
            result.put("phase","two-remaining");save();later(1800,()->closeAndCheck(card.apps.get(2).id,"focused",()->{
                require(draft().snapshot().cards.get(0).focusedWindowId.equals(card.apps.get(0).id),"Focused close selects surviving member");
                require(draft().snapshot().cards.get(0).weights.equals(Collections.singletonList(1f)),"Last member fills its card");
                result.put("phase","one-remaining");save();later(1500,()->closeAndCheck(card.apps.get(0).id,"last",()->{
                    require(draft().snapshot().cards.get(0).id.equals(baseline.cards.get(1).id),"Last member close promotes next side card");
                    stress();
                }));
            }));
        }));
    }
    private void closeAndCheck(String id,String label,Step next)throws Exception{
        Object removed=window(id);int display=(Integer)field(removed,"display"),count=windows().size();
        DisplayManager manager=activity.getSystemService(DisplayManager.class);
        require(manager.getDisplay(display)!=null,label+" owns a live display before close");
        WorkspaceCard card=slots().card((Integer)field(removed,"id"));boolean split=card.apps.size()>1;
        if(split){int index=0;while(!card.apps.get(index).id.equals(id))index++;click(handle(Math.min(index,card.apps.size()-2)));}
        else{ @SuppressWarnings("unchecked") Map<String,View> tiles=(Map<String,View>)field(field(activity,"dock"),"tiles");tiles.get("window:"+field(removed,"id")).performContextClick();}
        later(350,()->{
            WorkbenchDialog dialog=(WorkbenchDialog)field(activity,"activeDialog");
            require(dialog!=null&&dialog.isShowing(),label+" opens window-specific menu");
            View button=find(dialog.body,split?"关闭 "+field(removed,"name")+" 窗口":"关闭此窗口");
            require(button!=null,label+" menu offers its close action");CloseTrace trace=new CloseTrace(id,label);click(button);
            later(300,()->{
            require(window(id)==null&&windows().size()==count-1,label+" touch closes only its bound member");
            require(!dialog.isShowing(),label+" dismisses actions before closing");
            ready(()->{later(500,()->{
            trace.finish();
            require(manager.getDisplay(display)==null,label+" releases only the closed display");
            Map<String,String> now=identities(false),sizes=identities(true);
            for(String kept:now.keySet())require(now.get(kept).equals(resources.get(kept)),label+" survivor keeps display and Surface: "+kept);
            for(Object w:windows())if((Integer)field(w,"bufferEdge")>0){
                android.graphics.Point pixels=new android.graphics.Point();manager.getDisplay((Integer)field(w,"display")).getRealSize(pixels);
                View texture=(View)field(w,"texture");int edge=(Integer)field(w,"bufferEdge");
                require(pixels.x==edge&&pixels.y==edge&&texture.getWidth()==edge&&texture.getHeight()==edge,
                        label+" stable display and consumer buffer: "+field(w,"windowId"));
                require(field(w,"requestedCanvas")==null,label+" resized content frame committed: "+field(w,"windowId"));
            }
            for(WorkspaceCard side:baseline.cards.subList(1,baseline.cards.size()))for(AppTarget app:side.apps)require(sizes.get(app.id).equals(canvases.get(app.id)),label+" side canvas unchanged: "+app.id);
            controls(label);next.run();
            });});
            });
        });
    }
    private void restore()throws Exception{
        if(original==null)return;
        Set<String> keep=new HashSet<>();for(WorkspaceCard card:original.cards)for(AppTarget app:card.apps)keep.add(app.id);
        for(Object w:windows())if(!keep.contains(field(w,"windowId")))call("remove",w);
        for(WorkspaceCard card:original.cards)for(AppTarget app:card.apps)if(window(app.id)==null)call("addTarget",app);
        draft().replace(original);call("groupChanged");
    }
    private void stress()throws Exception{
        for(WorkspaceCard card:baseline.cards)for(AppTarget app:card.apps)if(window(app.id)==null)call("addTarget",app);
        draft().replace(baseline);call("groupChanged");
        ready(()->later(500,()->{
            resources=identities(false);canvases=identities(true);call("setFullscreen",true);
            later(800,()->{
                String first=baseline.cards.get(0).apps.get(1).id,second=baseline.cards.get(0).apps.get(2).id;
                CloseTrace trace=new CloseTrace(first,"rapidFullscreen");trace.exclude(second);
                call("remove",window(first));later(80,()->{
                    require((Boolean)field(activity,"animating"),"Second close arrives while first reflow is moving");call("remove",window(second));
                    ready(()->later(500,()->{
                        trace.finish();require(draft().snapshot().cards.get(0).apps.size()==1,"Rapid fullscreen close retains only intended survivor");
                        call("setFullscreen",false);restore();ready(()->{
                            controls("Restored");require(draft().snapshot()==original,"Original draft restored with IDs, order, focus and ratios");
                            result.put("phase","complete");save();new File(activity.getFilesDir(),"window-close-validation-baseline.json").delete();
                        });
                    }));
                });
            });
        }));
    }
    /** Observe the frames actually submitted for drawing, including the resize callback. */
    private final class CloseTrace implements ViewTreeObserver.OnPreDrawListener {
        final Set<String> excluded=new HashSet<>();final String label;final long start=SystemClock.uptimeMillis();final JSONArray samples=new JSONArray();
        final Map<String,float[]> before=new LinkedHashMap<>();final Map<String,List<float[]>> positions=new LinkedHashMap<>();
        String error;
        CloseTrace(String closed,String label)throws Exception{excluded.add(closed);this.label=label;
            for(Object w:windows())if(!closed.equals(field(w,"windowId"))){String id=(String)field(w,"windowId");before.put(id,rect((View)w));positions.put(id,new ArrayList<>());}
            activity.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(this);
        }
        void exclude(String id){excluded.add(id);positions.remove(id);before.remove(id);}
        float[] rect(View w){float[] p={0,0,w.getWidth(),w.getHeight()};w.getMatrix().mapPoints(p);return p;}
        public boolean onPreDraw(){try{
            JSONObject frame=new JSONObject().put("ms",SystemClock.uptimeMillis()-start);
            for(Object w:windows()){
                String id=(String)field(w,"windowId");if(excluded.contains(id))continue;
                View cover=(View)field(w,"cover");float[] p=rect((View)w);positions.get(id).add(p);
                if(Math.abs(((StageMotion)field(w,"tilt")).value)<.001){
                    float x=(float)(((StageMotion)field(w,"x")).value+((StageMotion)field(w,"sweep")).value),y=(float)((StageMotion)field(w,"y")).value;
                    float[] expected={x,y,x+(float)((StageMotion)field(w,"width")).value,y+(float)((StageMotion)field(w,"height")).value};
                    for(int i=0;i<4;i++)if(Math.abs(p[i]-expected[i])>1)error="Transformed pane escaped its animated bounds: "+id;
                }
                boolean covered=cover.getVisibility()==View.VISIBLE&&cover.getAlpha()>.001f;
                if(covered)error="Loading cover flashed on "+id;
                if(!Objects.equals(resources.get(id),field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface"))))error="Live resource replaced: "+id;
                frame.put(id,new JSONObject().put("bounds",new JSONArray(p)).put("covered",covered));
            }
            samples.put(frame);
        }catch(Exception e){error=e.toString();}return true;}
        void finish()throws Exception{
            activity.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(this);
            result.put(label+"Frames",samples);require(error==null,label+" retains live frames without loading cover: "+error);
            boolean moved=false;
            for(String id:positions.keySet()){
                List<float[]> frames=positions.get(id);if(frames.isEmpty())continue;
                float[] first=before.get(id),last=frames.get(frames.size()-1);int axis=0;
                for(int i=1;i<4;i++)if(Math.abs(last[i]-first[i])>Math.abs(last[axis]-first[axis]))axis=i;
                float distance=Math.abs(last[axis]-first[axis]);if(distance<10)continue;moved=true;
                float previous=first[axis],maxStep=0;int intermediate=0;
                for(float[] p:frames){maxStep=Math.max(maxStep,Math.abs(p[axis]-previous));previous=p[axis];if(Math.abs(p[axis]-first[axis])>1&&Math.abs(p[axis]-last[axis])>1)intermediate++;}
                require(intermediate>=3&&maxStep/distance<.55f,label+" moves continuously: "+id+" intermediate="+intermediate+" maxFraction="+maxStep/distance);
            }
            require(moved,label+" observed a survivor filling the vacated area");
        }
    }
    private void fail(Exception e){try{result.put("phase","failed").put("error",e.toString());restore();save();}catch(Exception ignored){}}
    private void save()throws Exception{result.put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"window-close-validation-result.json"))){out.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}}
}
