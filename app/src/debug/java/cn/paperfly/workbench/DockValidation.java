package cn.paperfly.workbench;

import android.os.*;
import android.view.*;
import android.widget.*;
import cn.paperfly.workbench.model.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Private finite Dock regression. No apps are installed, added or removed. */
final class DockValidation {
    private final StageActivity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final JSONArray checks=new JSONArray();
    private final JSONObject result=new JSONObject();
    private final long deadline=SystemClock.uptimeMillis()+90000;
    private WorkspaceDefinition baseline;
    private Map<Integer,String> resources;
    private DockValidation(StageActivity activity){this.activity=activity;}
    static void attach(StageActivity activity){File arm=new File(activity.getFilesDir(),"dock-validation-pending");if(arm.isFile()&&arm.delete()){DockValidation test=new DockValidation(activity);test.ready(test::start);}}
    private Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private Object call(String name,Object... args)throws Exception{for(Method m:StageActivity.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(activity,args);}throw new NoSuchMethodException(name);}
    private interface Step{void run()throws Exception;}
    private void later(long delay,Step next){main.postDelayed(()->{try{next.run();}catch(Exception e){fail(e);}},delay);}
    private void ready(Step next){later(300,()->{if(SystemClock.uptimeMillis()>deadline)throw new IOException("Timed out");if(!(Boolean)field(activity,"started")||(Boolean)field(activity,"resizing")||(Boolean)field(activity,"animating")){ready(next);return;}for(Object w:windows())if(!(Boolean)field(w,"ready")){ready(next);return;}next.run();});}
    @SuppressWarnings("unchecked") private List<Object> windows()throws Exception{return new ArrayList<>((List<Object>)field(activity,"windows"));}
    private WorkspaceDraft draft()throws Exception{return (WorkspaceDraft)field(activity,"draft");}
    private StageDock dock()throws Exception{return (StageDock)field(activity,"dock");}
    private int dp(int n){return WorkbenchUi.dp(activity,n);}
    private void require(boolean ok,String message)throws Exception{if(!ok)throw new IOException(message);checks.put(message);save();}
    private Map<Integer,String> identities()throws Exception{Map<Integer,String> map=new LinkedHashMap<>();for(Object w:windows())map.put((Integer)field(w,"id"),field(w,"display")+":"+System.identityHashCode(field(w,"activeSurface"))+":"+field(w,"canvasWidth")+"x"+field(w,"canvasHeight")+"@"+field(w,"canvasDensity"));return map;}
    private void same(String label)throws Exception{require(resources.equals(identities()),label+" preserves displays, Surfaces and canvases");}
    private void layout(StageDock view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(dp(StageDock.HEIGHT_DP),View.MeasureSpec.EXACTLY));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
    private void bounds(StageDock view,String label)throws Exception{
        View add=(View)field(view,"addButton");require(add.getWidth()==dp(48)&&add.getHeight()==dp(48),label+" add target is 48dp");
        require(add.getRight()==view.getWidth()-view.getPaddingRight()&&add.getBottom()<=view.getHeight()-view.getPaddingBottom(),label+" add action fits without trailing blank space");
        View scroll=(View)field(view,"scroll");require(scroll.getVisibility()==View.GONE||scroll.getRight()<=add.getLeft(),label+" app strip does not overlap add action");
        require(!contains(view,"窗口总览"),label+" has no overview entry");
    }
    private boolean contains(View view,String label){if((view.getContentDescription()!=null&&view.getContentDescription().toString().contains(label))||(view instanceof TextView&&((TextView)view).getText().toString().contains(label)))return true;if(view instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),label))return true;}return false;}
    private void fixtures()throws Exception{
        StageDock test=new StageDock(activity);List<StageDock.Item> items=new ArrayList<>();
        for(int count:new int[]{0,1,4,24}){
            items.clear();for(int i=0;i<count;i++)items.add(new StageDock.Item(i,"test"+i+"/.Main","Window "+i,null,i==0,i==2,i<2?DockModel.PINNED:i<5?DockModel.RUNNING:DockModel.RECENT));
            test.render(items,item->{},item->{},()->{});layout(test,320);bounds(test,"Fixture "+count);
            require(test.getWidth()<=dp(320),"Fixture "+count+" respects available width");
        }
        HorizontalScrollView scroll=(HorizontalScrollView)field(test,"scroll");LinearLayout row=(LinearLayout)field(test,"row");View first=row.getChildAt(0);
        scroll.scrollTo(row.getWidth(),0);int previous=scroll.getScrollX();require(previous>0,"Overflow apps can scroll");
        test.render(new ArrayList<>(items),item->{},item->{},()->{});layout(test,320);
        require(first==row.getChildAt(0)&&scroll.getScrollX()==previous,"Metadata refresh preserves tiles and scroll position");bounds(test,"Scrolled fixture");
    }
    private void start()throws Exception{
        baseline=draft().snapshot();resources=identities();result.put("phase","running");fixtures();bounds(dock(),"Live normal Dock");menus(0);
    }
    private void menus(int index)throws Exception{
        if(index==3){modes();return;}
        if(index==0)call("windowMenu");else if(index==1)call("windowActions",call("primary"));else call("showShortcuts");
        later(200,()->{WorkbenchDialog dialog=(WorkbenchDialog)field(activity,"activeDialog");require(!contains(dialog.body,"总览")&&!contains(dialog.body,"此应用所有窗口"),"Menu "+index+" has no overview entry or instructions");dialog.dismiss();later(200,()->menus(index+1));});
    }
    private void modes()throws Exception{
        call("setFullscreen",true);later(700,()->{bounds(dock(),"Live fullscreen Dock");same("Workspace fullscreen");call("setFullscreen",false);
            later(700,()->{same("Fullscreen exit");call("enterAppFullscreen");later(700,()->{require(dock().getVisibility()==View.GONE,"App fullscreen hides Dock");same("App fullscreen");call("leaveAppFullscreen");later(700,this::switchWindow);});});});
    }
    @SuppressWarnings("unchecked") private void switchWindow()throws Exception{
        same("App fullscreen exit");Map<String,View> tiles=(Map<String,View>)field(dock(),"tiles");CardSession slots=(CardSession)field(activity,"slots");
        for(Object w:windows())if(!slots.primaryCard((Integer)field(w,"id"))){int id=(Integer)field(w,"id");tiles.get("window:"+id).performClick();later(700,()->{
            require(slots.primary()==id,"Dock click selects side card");same("Dock switch");restore();later(700,this::picker);});return;}
        picker();
    }
    private void picker()throws Exception{
        ((View)field(dock(),"addButton")).performClick();later(400,()->{AppPanel panel=(AppPanel)field(activity,"appPanel");require(panel!=null&&panel.isShowing(),"Pinned add action opens app picker");panel.dismiss();later(400,()->{
            same("Picker dismissal");call("updateOptions",baseline.options.autoHide(true));later(2900,()->{
                require(dock().getVisibility()==View.GONE,"Auto-hide still hides Dock");DockAutoHide visibility=(DockAutoHide)field(activity,"dockVisibility");visibility.reveal();later(200,()->{
                    require(dock().getVisibility()==View.VISIBLE,"Auto-hide reveal restores Dock");same("Auto-hide and reveal");restore();ready(()->{bounds(dock(),"Restored Dock");require(draft().snapshot().cards.get(0).id.equals(baseline.cards.get(0).id),"Original primary card restored");result.put("phase","complete");save();});
                });
            });
        });});
    }
    private void restore()throws Exception{if(baseline!=null){draft().replace(baseline);call("groupChanged");}}
    private void fail(Exception e){try{result.put("phase","failed").put("error",e.toString());restore();save();}catch(Exception ignored){}}
    private void save()throws Exception{result.put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"dock-validation-result.json"))){out.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}}
}
