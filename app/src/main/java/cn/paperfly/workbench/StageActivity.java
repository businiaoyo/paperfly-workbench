package cn.paperfly.workbench;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import cn.paperfly.workbench.model.WorkspaceCard;
import cn.paperfly.workbench.model.AppTarget;
import cn.paperfly.workbench.model.WorkspaceOptions;
import cn.paperfly.workbench.model.WindowPlacement;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.paperfly.workbench.data.WorkspacePersistence;
import cn.paperfly.workbench.model.WorkspaceDraft;
import cn.paperfly.workbench.model.WorkspaceDefinition;
import cn.paperfly.workbench.model.WorkspaceStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent live surfaces share one scene. A swap never restores an OEM freeform task. */
public final class StageActivity extends Activity {
    private static java.lang.ref.WeakReference<StageActivity> active=new java.lang.ref.WeakReference<>(null);
    static void holdForPin(){StageActivity current=active.get();if(current==null)return;current.externalPicker=true;
        current.main.postDelayed(()->{if(current.imagePicking||current.ending)return;current.externalPicker=false;if(current.paused)current.leave();},1500);}
    static boolean resumeRetained(android.content.Context from){StageActivity current=active.get();
        if(current==null||current.ending||current.isFinishing()||!current.started||BridgeState.service()==null)return false;
        from.startActivity(new Intent(from,StageActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));return true;
    }
    static void closeForLaunch(){StageActivity current=active.get();if(current!=null&&!current.ending)current.leave();}
    private final IBinder session=new Binder();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService sampler=Executors.newSingleThreadExecutor();
    private final ExecutorService icons=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CardSession slots=new CardSession();
    private final List<AppWindow> windows=new ArrayList<>();
    private final WindowLoadQueue<LoadRequest> loads=new WindowLoadQueue<>();
    private final Runnable loadTick=this::advanceLoads;
    private boolean restoring;
    private final Runnable bridgeListener=this::connect;
    private IWorkbenchService service;
    private FrameLayout scene;
    private InsetBackdropHost backdropHost;
    private LinearLayout addCard;
    private SplitDivider splitDivider;
    private WorkspaceCard splitPreview;
    // Retain the displayed rectangle across a close and its asynchronous canvas commit.
    private boolean liveReflow;
    private final StageCardPose addCardPose=new StageCardPose();
    private Button restoreButton;
    private AppPanel appPanel;
    private android.app.Dialog activeDialog,memoryDialog;
    private LinearLayout memoryRows;
    private StageControls toolbar;
    private StageDock dock;
    private DockCatalog dockCatalog;
    private PackageEvents packageEvents;
    private StageTouchGestures touchGestures;private boolean touchGesturesEnabled=true;
    private final RecentEntry dockRecents=new RecentEntry();
    private final List<String> recentDockApps=new ArrayList<>();
    private final DockAutoHide dockVisibility=new DockAutoHide(this::dockVisibilityChanged);
    private View dockReveal;
    private View railReveal;
    private boolean railAutoHide;
    private final Runnable hideRailAgain=()->{if(this.railAutoHide&&!this.railCollapsed&&this.activeDialog==null){toggleRail();this.railAutoHide=true;}};

    private boolean railCollapsed,dockShown=true,railDragArmed,dockDragging;
    private final Runnable armRailDrag=()->{if(this.trackingRail&&this.touchedSide!=null&&this.railGesture.tap()){railDragArmed=true;this.touchedSide.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);notice("拖到主区域加入当前组");}};
    private AppWindow movingWindow;
    private WindowPlacement dragOriginal;
    private float dragX,dragY;

    private boolean resizing;
    private final StagePresentation presentation=new StagePresentation();
    private final StageKeyboard keyboard=new StageKeyboard();
    private int canvasGeneration;
    private Bundle memoryReport;
    private boolean sampling;
    private int memoryGeneration;
    private final Runnable memoryTick=this::sampleMemory;
    private RailGesture railGesture;
    private AppWindow touchedSide;
    private boolean trackingRail,touchedAddCard;
    private float railOffset,gestureOffset,gestureWidth;
    private boolean animating;
    private long lastFrameNanos;
    private int animationFrames;
    private final Choreographer.FrameCallback animation=this::animateFrame;
    private int logicalWidth,logicalHeight,density,nextId,pendingSelection=-1;
    private boolean ending,started,stopped;
    private final Runnable expireBackground=()->{if(this.stopped&&!this.ending)leave();};
    private AppTarget[] initial;
    private WorkspacePersistence persistence;
    private WorkspaceDraft draft;
    private WorkspaceDefinition lastQueuedDraft;
    private WorkspaceStyle style=WorkspaceStyle.DEFAULT;
    private StageLayout geometry;
    private StageLayout canvasGeometry;
    private WorkspaceDefinition namingDefinition;
    private int namingCount=-1;
    private final java.util.Map<String,String> cardNames=new java.util.HashMap<>();
    private boolean saveErrorShown,externalPicker,layoutDirty,imagePicking,paused;
    private final WorkspaceBackground background=new WorkspaceBackground();
    private StageBackGesture backGesture;
    private HostImeLayer imeLayer;
    private int backRequests;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);active=new java.lang.ref.WeakReference<>(this);
        // Keep a focused host for physical-display navigation; live apps retain input ownership.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        persistence=WorkspacePersistence.get(this);
        android.graphics.Rect bounds=getWindowManager().getMaximumWindowMetrics().getBounds();
        CanvasSize initialSize=CanvasSize.from(bounds.width(),bounds.height(),getResources().getDisplayMetrics().densityDpi);
        logicalWidth=initialSize.width;logicalHeight=initialSize.height;density=initialSize.density;
        railGesture=new RailGesture(android.view.ViewConfiguration.get(this).getScaledTouchSlop());
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
        getWindow().setDecorFitsSystemWindows(false);
        backdropHost=new InsetBackdropHost(this);
        scene=backdropHost.scene;scene.setClipChildren(false);
        backdropHost.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                style.backgroundColors()));setContentView(backdropHost);
        imeLayer=new HostImeLayer(this);
        configureFullscreenWindow();
        if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        toolbar=new StageControls(this,this::returnHome,this::windowMenu);
        scene.addView(toolbar,new FrameLayout.LayoutParams(dp(190),dp(48),Gravity.TOP));toolbar.setTranslationZ(dp(30));
        addCard=new LinearLayout(this);addCard.setOrientation(LinearLayout.VERTICAL);addCard.setGravity(Gravity.CENTER);
        GradientDrawable addBackground=new GradientDrawable();addBackground.setColor(0x20202b3b);addBackground.setCornerRadius(dp(14));
        addBackground.setStroke(dp(1),0x65ffffff);addCard.setBackground(addBackground);
        addCard.addView(WorkbenchUi.icon(this,WorkbenchIcon.Kind.PLUS,AppPanel.ACCENT),new LinearLayout.LayoutParams(dp(30),dp(30)));
        TextView addLabel=WorkbenchUi.text(this,"添加应用",11,AppPanel.MUTED);addLabel.setGravity(Gravity.CENTER);addLabel.setPadding(0,dp(8),0,0);addCard.addView(addLabel);
        addCard.setContentDescription("添加应用卡片");addCard.setOnClickListener(v->pickApp());
        addCard.setOnTouchListener((v,e)->{handleRailTouch(null,e,true);return true;});
        scene.addView(addCard,new FrameLayout.LayoutParams(dp(140),dp(100)));addCard.setTranslationZ(dp(4));
        restoreButton=WorkbenchUi.button(this,"菜单",false);restoreButton.setTextSize(12);restoreButton.setContentDescription("显示工作台菜单");
        GradientDrawable restoreBackground=new GradientDrawable();restoreBackground.setColor(Color.argb(195,20,29,43));restoreBackground.setCornerRadius(dp(22));
        restoreBackground.setStroke(dp(1),Color.argb(65,235,243,246));restoreButton.setBackground(restoreBackground);restoreButton.setPadding(0,0,0,0);
        restoreButton.setStateListAnimator(null);restoreButton.setElevation(dp(40));
        restoreButton.setOnClickListener(v->{if(presentation.appFullscreen())leaveAppFullscreen();else windowMenu();});restoreButton.setVisibility(View.GONE);restoreButton.setTranslationZ(dp(60));
        FrameLayout.LayoutParams restoreLayout=new FrameLayout.LayoutParams(dp(64),dp(44),Gravity.BOTTOM|Gravity.LEFT);restoreLayout.bottomMargin=dp(8);restoreLayout.leftMargin=dp(8);scene.addView(restoreButton,restoreLayout);
        scene.setOnApplyWindowInsetsListener((v,insets)->{positionRestore(insets);return insets;});
        scene.setOnTouchListener((v,e)->{
            if(presentation.appFullscreen()||railCollapsed)return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&((style.railRight?e.getX()<scene.getWidth()-railWidth()-dp(20):e.getX()>railWidth()+dp(20))||e.getY()<railTop()||e.getY()>railBottom()))return false;
            return handleRailTouch(null,e);
        });
        scene.setOnDragListener((v,event)->handleDockDrag(event));
        // System-bar visibility changes the scene, not the application's logical canvas.
        scene.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)relayout();});
        backGesture=new StageBackGesture(this,scene,this::backFromGesture,()->{for(AppWindow w:windows)w.cancelTouch();},
                ()->ending||resizing||externalPicker,
                event->getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("last_back_event",event).apply());
        BridgeState.add(bridgeListener);
        persistence.loadCurrent(definition->{
            if(ending||isFinishing()||isDestroyed())return;
            try{draft=new WorkspaceDraft(definition);}catch(IllegalArgumentException error){configurationFailed(error.getMessage());return;}
            slots.bind(draft);lastQueuedDraft=definition;style=definition.style;initial=draft.targets();
            background.apply(this,backdropHost,style);
            relayout();connect();
        },this::configurationFailed);
        dockCatalog=new DockCatalog(this);dock=new StageDock(this);FrameLayout.LayoutParams dockParams=new FrameLayout.LayoutParams(-2,dp(StageDock.HEIGHT_DP),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dockParams.bottomMargin=dp(6);dockParams.leftMargin=dockParams.rightMargin=dp(24);
        scene.addView(dock,dockParams);dock.setTranslationZ(dp(65));refreshDock();
        splitDivider=new SplitDivider(this,new SplitDivider.Actions(){
            public void begin(WorkspaceCard card){liveReflow=false;splitPreview=card;for(AppWindow w:windows)w.cancelTouch();focusPicker();refreshDock();}
            public void preview(WorkspaceCard card,List<Float> weights){splitPreview=card.withSplit(weights,card.axis);arrange(false);}
            public void end(WorkspaceCard card,List<Float> weights,boolean commit){
                AppWindow w=primary();WorkspaceCard current=w==null?null:slots.card(w.id);
                if(commit&&current!=null&&current.id.equals(card.id)&&current.weights.equals(card.weights)&&!weights.equals(card.weights)){
                    slots.splitOptions(weights,card.axis);groupChanged();
                }else{splitPreview=null;arrange(false);focusCurrent();}
                refreshDock();
            }
            public void edit(WorkspaceCard card,int boundary,boolean horizontal,View anchor){splitWindowActions(card,boundary,horizontal,anchor);}
        });
        scene.addView(splitDivider,new FrameLayout.LayoutParams(-1,-1));splitDivider.setTranslationZ(dp(50));splitDivider.setVisibility(View.GONE);
        Button revealDock=WorkbenchUi.button(this,"显示 Dock",false);revealDock.setTextSize(12);revealDock.setPadding(0,0,0,0);revealDock.setMinHeight(0);revealDock.setMinimumHeight(0);revealDock.setIncludeFontPadding(false);dockReveal=revealDock;dockReveal.setOnClickListener(v->dockVisibility.reveal());
        FrameLayout.LayoutParams revealDockParams=new FrameLayout.LayoutParams(dp(112),dp(32),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);revealDockParams.bottomMargin=dp(6);scene.addView(dockReveal,revealDockParams);dockReveal.setTranslationZ(dp(66));dockReveal.setVisibility(View.GONE);
        dock.setOnTouchListener((v,e)->{dockVisibility.interact();return false;});
        railReveal=new View(this);railReveal.setContentDescription("显示侧栏");railReveal.setBackgroundColor(0x447ebdc9);railReveal.setFocusable(true);
        railReveal.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){revealRail();return true;}return true;});
        railReveal.setOnHoverListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER)revealRail();return true;});
        FrameLayout.LayoutParams revealParams=new FrameLayout.LayoutParams(dp(12),dp(100),Gravity.CENTER_VERTICAL|Gravity.LEFT);scene.addView(railReveal,revealParams);railReveal.setTranslationZ(dp(66));railReveal.setVisibility(View.GONE);
        touchGestures=new StageTouchGestures(getResources().getDisplayMetrics().density,()->!touchGesturesEnabled||ending||paused||externalPicker||activeDialog!=null&&activeDialog.isShowing(),()->{for(AppWindow w:windows)w.cancelTouch();},command->{
            if(command==MultitouchGesture.NEXT)cycleWindow(false);else if(command==MultitouchGesture.PREVIOUS)cycleWindow(true);else if(command==MultitouchGesture.HOME)moveTaskToBack(true);
        });
        packageEvents=new PackageEvents(this,this::packageChanged);StageValidation.attach(this);ParityValidation.attach(this);TabletValidation.attach(this);SplitValidation.attach(this);DockValidation.attach(this);WindowCloseValidation.attach(this);
    }
    private void connect(){
        if(ending)return;service=BridgeState.service();
        if(service==null){
            notice("服务已断开 · 返回 App 后重新激活");
            if(started){Toast.makeText(this,"连接已断开，应用已交还系统，请重新激活",Toast.LENGTH_LONG).show();leave();}
            return;
        }
        if(draft==null)return;
        if(!started&&scene.getWidth()==0){scene.post(this::connect);return;}
        if(!started){started=true;restoring=true;
            try{if(initial!=null)for(AppTarget c:initial)addTarget(c);}finally{restoring=false;}
            loadDockRecents();arrange(false);changed();
            if(windows.isEmpty())pickApp();}
    }
    private void addApp(String component){
        String pkg=new AppTarget(component).packageName;
        for(AppWindow w:windows)if(new AppTarget(w.component).packageName.equals(pkg)){select(w);return;}
        addTarget(new AppTarget(component));
    }
    private void addTarget(AppTarget target){
        String component=target.component;
        ComponentName name=ComponentName.unflattenFromString(component);if(name==null||name.getPackageName().equals(getPackageName()))return;
        for(AppWindow w:windows)if(w.windowId.equals(target.id)){select(w);return;}
        String label;boolean available=true;
        try{android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(name,0);
            if(!info.enabled||!info.exported||!info.applicationInfo.enabled)throw new IllegalArgumentException("App unavailable");
            label=info.loadLabel(getPackageManager()).toString();}
        catch(Exception e){available=false;label=name.getPackageName()+" · 暂不可用";}
        AppWindow w=new AppWindow(nextId++,target,label);windows.add(w);draft.addInstance(target);slots.add(w.id,target.id);if(draft.snapshot().options.minimizedWindows.contains(target.id))presentation.minimize(w.id);resizeSurface(w);
        w.available=available;scene.addView(w,new FrameLayout.LayoutParams(w.canvasWidth,w.canvasHeight));
        scene.addView(w.badge,new FrameLayout.LayoutParams(dp(30),dp(30)));
        scene.addView(w.chrome,new FrameLayout.LayoutParams(1,1));
        icons.execute(()->{try{android.graphics.drawable.Drawable icon=getPackageManager().getActivityIcon(name);main.post(()->{if(!ending&&!w.removed){w.badge.icon.setImageDrawable(icon);w.cover.icon(icon);refreshDock();}});}catch(Exception ignored){}});
        if(!restoring){arrange(true);changed();}
    }
    private final class AppWindow extends FrameLayout implements TextureView.SurfaceTextureListener {
        final int id;final String component,windowId;String name;final boolean additionalInstance;TextureView texture;final StageLoadingView cover;final RailAppBadge badge;final WindowChrome chrome;
        SurfaceTexture activeSurface;
        int canvasWidth=logicalWidth,canvasHeight=logicalHeight,canvasDensity=density;
        int bufferEdge;
        CanvasSize requestedCanvas;
        final CanvasCommitQueue canvasCommits=new CanvasCommitQueue();
        final StageMotion x=new StageMotion(0),y=new StageMotion(0),width=new StageMotion(0),height=new StageMotion(0),sweep=new StageMotion(0),tilt=new StageMotion(0);
        final float[] badgeAnchor=new float[2];
        final StageCardPose pose=new StageCardPose();
        int display=-1,frames,metadataGeneration;boolean available=true,requested,ready,touching,removed,failed,closing,retrying;MotionEvent lastTouch;
        AppWindow(int id,AppTarget target,String name){
            super(StageActivity.this);this.id=id;this.component=target.component;this.windowId=target.id;this.additionalInstance=target.additionalInstance();this.name=name+(additionalInstance?" · 新窗口":"");
            setPivotX(0);setPivotY(0);setClipToOutline(true);setBackgroundColor(Color.rgb(39,48,61));
            setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){
                o.setRoundRect(0,0,canvasWidth,canvasHeight,presentation.immersive()&&slots.primaryCard(id)?0:dp(13)/Math.max(.1f,getScaleX()));o.setAlpha(0f);}});
            texture=new TextureView(StageActivity.this);texture.setOpaque(true);texture.setSurfaceTextureListener(this);
            addView(texture,new FrameLayout.LayoutParams(-1,-1));
            cover=new StageLoadingView(StageActivity.this,name);addView(cover,new FrameLayout.LayoutParams(-1,-1));
            badge=new RailAppBadge(StageActivity.this);badge.describe(name,1);badge.setOnClickListener(v->select(this));badge.setOnTouchListener((v,e)->handleRailTouch(this,e));
            chrome=new WindowChrome(StageActivity.this,name,new WindowChrome.Actions(){
                public void focus(){select(AppWindow.this);}public void minimize(){minimizeWindow(AppWindow.this);}
                public void fullscreen(){select(AppWindow.this);enterAppFullscreen();}public void close(){remove(AppWindow.this);}
                public void drag(MotionEvent event,boolean resize){dragWindow(AppWindow.this,event,resize);}
            });chrome.setVisibility(View.GONE);
            setOnGenericMotionListener((v,e)->genericMotion(AppWindow.this,e));
            setOnHoverListener((v,e)->genericMotion(AppWindow.this,e));
            setContentDescription("应用窗口："+name);setOnClickListener(v->select(this));setOnTouchListener((v,event)->handleTouch(this,event));
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e){
            if(!isWindowShown(AppWindow.this)||(railCollapsed&&!slots.primaryCard(id)))return false;
            return super.dispatchTouchEvent(e);
        }
        LoadRequest load;
        @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int width,int height){
            activeSurface=st;frames=0;
            st.setDefaultBufferSize(bufferEdge>0?bufferEdge:canvasWidth,bufferEdge>0?bufferEdge:canvasHeight);if(service==null||ending)return;
            if(load!=null)loads.cancel(load,android.os.SystemClock.uptimeMillis());
            load=new LoadRequest(this,st);loads.add(load);
            if(slots.primary()==id||pendingSelection==id)loads.prioritize(load);
            cover.showStatus("准备接续 · 点按优先打开");scheduleLoads();
        }
        void showFrame(){if(ready&&frames>0&&!resizing&&requestedCanvas==null)cover.reveal();}
        @Override public void onSurfaceTextureUpdated(SurfaceTexture st){if(st==activeSurface){frames++;commitCanvasFrame();if(frames==1)android.util.Log.i("WorkbenchLoad","FRAME id="+id+" "+component);if(load!=null)loads.frame(load,android.os.SystemClock.uptimeMillis());showFrame();}}
        void commitCanvasFrame(){
            if(activeSurface==null||removed||ending)return;
            CanvasSize size=canvasCommits.ready(activeSurface.getTimestamp());if(size==null)return;
            applyCanvasSize(size);
            if(requestedCanvas!=null&&requestedCanvas.width==size.width&&requestedCanvas.height==size.height&&requestedCanvas.density==size.density)requestedCanvas=null;
            // TextureView reports the acquired frame during drawing. Update the clipped parent
            // bounds in this same frame instead of exposing new pixels under last frame's scale.
            layout(getLeft(),getTop(),getLeft()+canvasWidth,getTop()+canvasHeight);applyFrame();showFrame();
        }
        void applyCanvasSize(CanvasSize size){
            canvasWidth=size.width;canvasHeight=size.height;canvasDensity=size.density;
            setLayoutParams(new FrameLayout.LayoutParams(canvasWidth,canvasHeight));sizeRenderTarget();
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){}
        void sizeRenderTarget(){
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)texture.getLayoutParams();
            int edge=bufferEdge>0?bufferEdge:FrameLayout.LayoutParams.MATCH_PARENT;
            if(params.width!=edge||params.height!=edge)texture.setLayoutParams(new FrameLayout.LayoutParams(edge,edge));
            SurfaceTexture surface=texture.getSurfaceTexture();
            if(surface!=null)surface.setDefaultBufferSize(bufferEdge>0?bufferEdge:canvasWidth,bufferEdge>0?bufferEdge:canvasHeight);
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){if(st==activeSurface){if(load!=null)loads.cancel(load,android.os.SystemClock.uptimeMillis());activeSurface=null;android.util.Log.i("WorkbenchScene","SURFACE_DESTROY "+name);cancelTouch();ready=false;frames=0;cover.showStatus("正在接续");}return true;}
        void renewRenderTarget(){
            // Some OEMs leave an off-screen consumer with a stale queue after canvas resize.
            // Replace only its render target, keeping the app task and display alive.
            cancelTouch();ready=false;frames=0;activeSurface=null;cover.showStatus("正在接续");
            removeView(texture);texture=new TextureView(StageActivity.this);texture.setOpaque(true);texture.setSurfaceTextureListener(this);
            addView(texture,0,new FrameLayout.LayoutParams(bufferEdge>0?bufferEdge:-1,bufferEdge>0?bufferEdge:-1));
        }
        void cancelTouch(){
            if(touching&&lastTouch!=null){MotionEvent cancel=MotionEvent.obtain(lastTouch);cancel.setAction(MotionEvent.ACTION_CANCEL);forward(this,cancel);cancel.recycle();}
            touching=false;if(lastTouch!=null){lastTouch.recycle();lastTouch=null;}
        }
    }
    private final class LoadRequest {
        final AppWindow window;final SurfaceTexture texture;int metadataGeneration;
        LoadRequest(AppWindow window,SurfaceTexture texture){this.window=window;this.texture=texture;}
        void start(){
            AppWindow w=window;
            if(ending||w.removed||w.activeSurface!=texture){loads.failed(this,android.os.SystemClock.uptimeMillis());return;}
            metadataGeneration=w.metadataGeneration;
            if(!w.available){w.failed=true;w.cover.showStatus("应用暂不可用 · 配置已保留");loads.failed(this,SystemClock.uptimeMillis());return;}
            boolean rebind=w.requested;w.requested=true;w.failed=false;
            w.cover.showStatus("正在接续");
            Surface surface=new Surface(texture);IWorkbenchService remote=service;
            int width=w.canvasWidth,height=w.canvasHeight,dpi=w.canvasDensity;
            android.util.Log.i("WorkbenchLoad","START id="+w.id+" rebind="+rebind+" "+w.component);
            worker.execute(()->{
                Bundle response;
                try{response=rebind?remote.rebindInstanceSurface(w.windowId,surface,session):remote.openLiveInstance(w.windowId,w.component,surface,width,height,dpi,getTaskId(),session);}
                catch(Exception error){response=new Bundle();response.putString("detail",error.toString());}
                finally{surface.release();}
                Bundle result=response;main.post(()->{
                    long now=android.os.SystemClock.uptimeMillis();
                    if(result.getBoolean("success")){loads.opened(this,now,result.getBoolean("reusedTask"));if(w.activeSurface==texture&&w.frames>0)loads.frame(this,now);}else loads.failed(this,now);
                    android.util.Log.i("WorkbenchLoad","OPENED id="+w.id+" ok="+result.getBoolean("success")+" reused="+result.getBoolean("reusedTask")+" frames="+w.frames);
                    if(ending)return;
                    if(w.removed){if(result.getBoolean("success"))worker.execute(()->{try{remote.closeLiveWindow(result.getInt("displayId"),session);}catch(Exception ignored){}});scheduleLoads();return;}
                    if(result.getBoolean("success")){
                        w.display=result.getInt("displayId");
                        w.bufferEdge=result.getInt("bufferEdge");w.sizeRenderTarget();
                        if(!w.available||metadataGeneration!=w.metadataGeneration){w.failed=true;w.ready=false;if(w.available)retryWindow(w);scheduleLoads();return;}
                        if(w.activeSurface==texture){w.ready=true;w.showFrame();
                            if(pendingSelection==w.id){pendingSelection=-1;select(w);}updateTitle();focusCurrent();}
                    }else{
                        w.requested=false;w.failed=true;
                        if(w.activeSurface==texture){w.ready=false;w.cover.showStatus(result.getString("userMessage","暂时无法打开 · 点按重试"));}
                    }
                    getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("last_stage_report",result.getString("detail")).apply();
                    changed();scheduleLoads();
                });
            });
        }
    }
    private void scheduleLoads(){main.removeCallbacks(loadTick);if(!ending&&!stopped&&loads.busy())main.postDelayed(loadTick,100);}
    private void advanceLoads(){
        if(ending||stopped)return;long now=android.os.SystemClock.uptimeMillis();LoadRequest expired=loads.expire(now);
        if(expired!=null&&!expired.window.removed&&expired.window.frames==0){
            expired.window.cover.showStatus("正在等待画面 · 点按查看");
            android.util.Log.w("WorkbenchLoad","FRAME_TIMEOUT "+expired.window.component);
        }
        if(!paused&&!resizing&&!externalPicker&&(activeDialog==null||!activeDialog.isShowing())){
            LoadRequest next=loads.next(now);if(next!=null)next.start();
        }
        scheduleLoads();
    }
    private boolean handleTouch(AppWindow w,MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)dockVisibility.interact();
        if(ending||w.closing||resizing||!isWindowShown(w)||(railCollapsed&&!slots.primaryCard(w.id)))return false;
        if(!slots.primaryCard(w.id))return handleRailTouch(w,event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){if(slots.primary()!=w.id){keyboard.release(service,session);slots.select(w.id);arrange(false);saveState();updateTitle();refreshDock();}focusCurrent();}
        if(!w.ready)return true;if(event.getActionMasked()==MotionEvent.ACTION_DOWN)w.touching=true;if(!w.touching)return true;
        if(w.lastTouch!=null)w.lastTouch.recycle();w.lastTouch=MotionEvent.obtain(event);forward(w,event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)w.touching=false;return true;
    }
    private void forward(AppWindow w,MotionEvent event){try{if(service!=null&&w.display>=0)service.liveTouch(w.display,event,session);}catch(Exception e){notice("输入连接中断");}}
    private void select(AppWindow w){
        if(ending||w.removed||w.closing)return;
        keyboard.release(service,session);
        if(presentation.appFullscreen()&&!presentation.isFullscreenApp(w.id))leaveAppFullscreen();
        boolean wasMinimized=presentation.minimized(w.id);presentation.restore(w.id);if(draft!=null)draft.options(draft.snapshot().options.minimized(w.windowId,false).remember(w.component));
        if(!w.ready){saveState();arrange(true);refreshDock();pendingSelection=w.id;if(w.failed){retryWindow(w);return;}if(w.load!=null)loads.prioritize(w.load);scheduleLoads();notice("正在打开 "+w.name);return;}pendingSelection=-1;
        for(AppWindow other:windows)other.cancelTouch();
        if(slots.select(w.id)||wasMinimized){arrange(true);android.util.Log.i("WorkbenchScene","SELECT id="+w.id+" display="+w.display+" at="+SystemClock.uptimeMillis());}focusCurrent();updateTitle();refreshDock();saveState();if(appPanel!=null)appPanel.refresh();
    }
    private void notice(String message){if(!ending)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void updateTitle(){for(AppWindow w:windows)if(w.id==slots.primary())toolbar.appName(cardName(w));}
    private StageLayout layout(){
        float uiDensity=getResources().getDisplayMetrics().density;
        if(geometry==null||!geometry.matches(scene.getWidth(),scene.getHeight(),uiDensity,presentation.workspaceFullscreen(),slots.size(),style))
            geometry=new StageLayout(scene.getWidth(),scene.getHeight(),uiDensity,presentation.workspaceFullscreen(),slots.size(),style);
        geometry.railHidden=railCollapsed;
        geometry.dockInset=dockShown&&!presentation.appFullscreen()?dp(StageLayout.DOCK_INSET_DP):0;
        return geometry;
    }
    private StageLayout canvasLayout(){
        // Use the ordinary workspace's safe area even when bars/rail are hidden. Changing
        // presentation must never resize a VirtualDisplay and restart an app's Activity.
        android.view.WindowMetrics metrics=getWindowManager().getCurrentWindowMetrics();
        android.graphics.Rect bounds=metrics.getBounds();
        android.graphics.Insets safe=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int width=Math.max(1,bounds.width()-safe.left-safe.right),height=Math.max(1,bounds.height()-safe.top-safe.bottom);
        float uiDensity=getResources().getDisplayMetrics().density;
        if(canvasGeometry==null||!canvasGeometry.matches(width,height,uiDensity,false,slots.size(),style))
            canvasGeometry=new StageLayout(width,height,uiDensity,false,slots.size(),style);
        // Stable normal-mode baseline. Dock visibility/fullscreen only transform this canvas.
        canvasGeometry.dockInset=dp(StageLayout.DOCK_INSET_DP);
        return canvasGeometry;
    }
    private float railTop(){return layout().railTop();}
    private int sceneGap(){return layout().gap();}
    private float railBottom(){return layout().railBottom();}
    private float railWidth(){return layout().railWidth();}
    private int railEntries(){return layout().railEntries();}
    private void clampRail(){railOffset=layout().clampOffset(railOffset);}
    private StageLayout.Bounds target(int slot){return layout().target(slot,railOffset);}
    private void arrange(boolean animate){
        if(scene==null||scene.getWidth()==0)return;
        if(!presentation.appFullscreen())clampRail();if(!animate)stopAnimation();
        for(AppWindow w:windows){StageLayout.Bounds to=windowTarget(w);
            if(w.width.value==0){w.x.snap(to.left);w.y.snap(to.top+dp(12));w.width.snap(to.width()*.96f);w.height.snap(to.height()*.96f);}
            // A split card keeps a common flat plane so its members never develop visible seams.
            float tilt=slots.primaryCard(w.id)||slots.card(w.id).apps.size()>1?0:(style.railRight?-StageLayout.SIDE_YAW:StageLayout.SIDE_YAW);
            if(animate){w.x.target(to.left);w.y.target(to.top);w.width.target(to.width());w.height.target(to.height());w.tilt.target(tilt);}
            else{w.x.snap(to.left);w.y.snap(to.top);w.width.snap(to.width());w.height.snap(to.height());w.tilt.snap(tilt);}
            w.setTranslationZ(presentation.isFullscreenApp(w.id)?dp(24):slots.primaryCard(w.id)?dp(12):dp(4));w.badge.setTranslationZ(dp(20));
            w.badge.describe(cardName(w),slots.card(w.id).apps.size());}
        updateTitle();if(!animate){applyFrame();return;}startAnimation();
    }
    private void startAnimation(){
        if(animating||ending)return;
        if(lastFrameNanos==0){lastFrameNanos=System.nanoTime();animationFrames=0;}
        animating=true;Choreographer.getInstance().postFrameCallback(animation);
    }
    private void animateFrame(long time){
        if(!animating||ending)return;animating=false;
        double dt=(time-lastFrameNanos)/1e9;lastFrameNanos=time;boolean moving=false;animationFrames++;
        List<AppWindow> finished=new ArrayList<>();
        for(AppWindow w:windows){moving|=w.x.step(dt);moving|=w.y.step(dt);moving|=w.width.step(dt);moving|=w.height.step(dt);moving|=w.tilt.step(dt);boolean swiping=w.sweep.step(dt);moving|=swiping;
            if(w.closing&&!swiping)finished.add(w);}
        applyFrame();for(AppWindow w:finished)remove(w);
        if(moving)startAnimation();else if(!animating){lastFrameNanos=0;if(!resizing)liveReflow=false;updateSplitDivider();saveState();}
    }
    private void stopAnimation(){animating=false;lastFrameNanos=0;Choreographer.getInstance().removeFrameCallback(animation);}
    private void applyFrame(){
        for(AppWindow w:windows){
            boolean visible=isWindowShown(w)&&(!railCollapsed||slots.primaryCard(w.id));
            // INVISIBLE/GONE detaches a TextureView's SurfaceTexture. Keep it attached,
            // transparent and excluded from input/accessibility while another app fills the screen.
            float baseZ=slots.primaryCard(w.id)?dp(12)+dp(24)*slots.card(w.id).placements.get(w.windowId).order/Math.max(1,slots.card(w.id).apps.size()):dp(4);
            if(slots.primary()==w.id)baseZ=dp(40);
            w.setTranslationZ(baseZ);
            boolean chromeVisible=visible&&!presentation.appFullscreen()&&slots.primaryCard(w.id)&&slots.card(w.id).freeform;
            w.chrome.setVisibility(chromeVisible?View.VISIBLE:View.GONE);
            if(chromeVisible)w.chrome.position((float)(w.x.value+w.sweep.value),(float)w.y.value,(float)w.width.value,(float)w.height.value,baseZ+dp(1));
            w.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            float scale=(float)w.width.value/w.canvasWidth;if(scale<=0)continue;
            float x=(float)(w.x.value+w.sweep.value),y=(float)w.y.value;
            if(previewing(w))w.pose.applyBounds(w,windowTarget(w),w.canvasWidth,w.canvasHeight);
            else w.pose.applyWindow(w,x,y,(float)w.width.value,(float)w.height.value,w.canvasWidth,w.canvasHeight,(float)w.tilt.value,layout().perspectiveHorizon());w.invalidateOutline();
            boolean side=!slots.primaryCard(w.id);
            boolean settledSide=side&&Math.abs(w.width.value-windowTarget(w).width())<1;
            if(settledSide){int top=Math.max(0,Math.min(w.canvasHeight,(int)Math.ceil((railTop()-y)/scale)));
                int bottom=Math.max(top,Math.min(w.canvasHeight,(int)Math.floor((railBottom()-y)/scale)));
                w.setClipBounds(new android.graphics.Rect(0,top,w.canvasWidth,bottom));}
            else w.setClipBounds(null);
            float alpha=1-Math.min(.96f,(float)Math.abs(w.sweep.value)/Math.max(dp(40),(float)w.width.value+dp(50)));
            w.setAlpha(visible?alpha:0);w.badge.setAlpha(alpha*Math.min(1,Math.abs((float)w.tilt.value)/StageLayout.SIDE_YAW));
            StageLayout.Bounds cardBounds=target(slots.slotOf(w.id));boolean split=slots.card(w.id).apps.size()>1;
            w.badgeAnchor[0]=0;w.badgeAnchor[1]=w.canvasHeight;w.getMatrix().mapPoints(w.badgeAnchor);
            float badgeY=(split?cardBounds.bottom:w.getTop()+w.badgeAnchor[1])-dp(30),badgeX=(split?cardBounds.left:w.getLeft()+w.badgeAnchor[0])+dp(2);
            if(split)w.badge.setAlpha(alpha);
            w.badge.setVisibility(!railCollapsed&&!presentation.appFullscreen()&&slots.representative(w.id)==w.id&&side&&badgeY>=railTop()&&badgeY+dp(30)<=railBottom()?View.VISIBLE:View.INVISIBLE);
            w.badge.setTranslationX(badgeX);w.badge.setTranslationY(badgeY);
        }
        StageLayout.Bounds card=target(Math.max(1,slots.size()));
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)addCard.getLayoutParams();int width=Math.max(1,Math.round(card.width())),height=Math.max(1,Math.round(card.height()));
        if(layout.width!=width||layout.height!=height){layout.width=width;layout.height=height;addCard.setLayoutParams(layout);}
        addCardPose.apply(addCard,card.left,card.top,1,width,style.railRight?-StageLayout.SIDE_YAW:StageLayout.SIDE_YAW,layout().perspectiveHorizon());
        int top=Math.max(0,Math.min(height,(int)Math.ceil(railTop()-card.top))),bottom=Math.max(top,Math.min(height,(int)Math.floor(railBottom()-card.top)));
        addCard.setClipBounds(new android.graphics.Rect(0,top,width,bottom));addCard.setVisibility(!railCollapsed&&!presentation.appFullscreen()&&bottom>top?View.VISIBLE:View.INVISIBLE);
        updateSplitDivider();
    }
    private void updateSplitDivider(){
        if(splitDivider==null)return;AppWindow w=primary();WorkspaceCard card=w==null?null:slots.card(w.id);
        boolean visible=card!=null&&card.apps.size()>1&&!card.freeform&&!presentation.appFullscreen()&&!resizing&&lastFrameNanos==0;
        if(visible)for(AppWindow member:cardMembers(w))if(presentation.minimized(member.id)){visible=false;break;}
        if(!visible){splitDivider.setVisibility(View.GONE);splitDivider.cancel();return;}
        StageLayout canvas=canvasLayout();StageLayout.Bounds reference=canvas.target(0,0),area=StageLayout.mapMember(target(0),reference,reference);
        StageLayout.Bounds first=canvas.member(reference,0,card.weights,card.axis);
        boolean horizontal=first.height()==reference.height();float scale=area.width()/reference.width();
        splitDivider.configure(card,area,horizontal,dp(4)*scale);splitDivider.setVisibility(View.VISIBLE);
    }
    private boolean handleRailTouch(AppWindow window,MotionEvent event){
        return handleRailTouch(window,event,false);
    }
    private boolean handleRailTouch(AppWindow window,MotionEvent event,boolean adding){
        if(ending||resizing||presentation.appFullscreen()||railCollapsed)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(window!=null&&(window.closing||slots.primaryCard(window.id)))return false;
            int[] origin=new int[2];scene.getLocationOnScreen(origin);float localY=event.getRawY()-origin[1];
            if(localY<railTop()||localY>railBottom())return false;
            touchedSide=window;touchedAddCard=adding;trackingRail=true;gestureOffset=railOffset;gestureWidth=window==null?0:target(slots.slotOf(window.id)).width();
            railGesture.begin(event.getRawX(),event.getRawY());railDragArmed=false;main.removeCallbacks(armRailDrag);if(window!=null)main.postDelayed(armRailDrag,450);return true;
        }
        if(!trackingRail)return true;
        railGesture.move(event.getRawX(),event.getRawY());
        if(action==MotionEvent.ACTION_MOVE){
            if(railDragArmed&&touchedSide!=null){for(AppWindow member:cardMembers(touchedSide)){member.sweep.snap(railGesture.dx);}applyFrame();return true;}
            if(!railGesture.tap())main.removeCallbacks(armRailDrag);
            if(railGesture.axis==RailGesture.VERTICAL){railOffset=gestureOffset-railGesture.dy;arrange(false);}
            else if(railGesture.axis==RailGesture.HORIZONTAL&&touchedSide!=null){for(AppWindow member:cardMembers(touchedSide)){member.sweep.snap(railGesture.dx);member.setTranslationZ(dp(22));}applyFrame();}
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            main.removeCallbacks(armRailDrag);
            if(railDragArmed){AppWindow dragged=touchedSide;railDragArmed=false;trackingRail=false;touchedSide=null;
                if(dragged!=null){for(AppWindow member:cardMembers(dragged))member.sweep.snap(0);
                    int[] origin=new int[2];scene.getLocationOnScreen(origin);float x=event.getRawX()-origin[0];
                    if(action==MotionEvent.ACTION_UP&&(style.railRight?x<scene.getWidth()-railWidth():x>railWidth())){slots.mergeIntoPrimary(dragged.id);groupChanged();}else arrange(true);}
                return true;}
            AppWindow chosen=touchedSide;boolean addChosen=touchedAddCard;trackingRail=false;touchedSide=null;touchedAddCard=false;
            if(chosen!=null){
                if(action==MotionEvent.ACTION_UP&&railGesture.dismiss(gestureWidth,dp(54))){
                    for(AppWindow member:cardMembers(chosen)){member.closing=true;member.sweep.target(Math.signum(railGesture.dx)*(gestureWidth+dp(100)));}startAnimation();
                }else if(action==MotionEvent.ACTION_UP&&railGesture.tap()){chosen.performClick();}
                else{for(AppWindow member:cardMembers(chosen))member.sweep.target(0);startAnimation();}
            }
            else if(addChosen&&action==MotionEvent.ACTION_UP&&railGesture.tap())addCard.performClick();
            if(railGesture.axis==RailGesture.VERTICAL)saveState();
        }
        return true;
    }
    private AppWindow primary(){for(AppWindow w:windows)if(w.id==slots.primary())return w;return null;}
    private void focusPrimary(){AppWindow w=primary();try{if(w!=null&&w.ready&&!presentation.minimized(w.id)&&service!=null)service.focusLiveWindow(w.display,session);else if(service!=null)service.focusLiveWindow(0,session);}catch(Exception e){notice("输入连接中断");}}
    private void focusPicker(){try{if(service!=null)service.focusLiveWindow(0,session);}catch(Exception ignored){}}
    private void sendBack(){
        AppWindow w=primary();if(w==null||!w.ready||presentation.minimized(w.id)||service==null)return;w.cancelTouch();
        getSharedPreferences("workspace",MODE_PRIVATE).edit().putInt("back_requests",++backRequests).putInt("back_display",w.display).apply();
        IWorkbenchService remote=service;int display=w.display;
        worker.execute(()->{try{Bundle result=remote.backLiveWindow(display,session);main.post(()->{if(!ending){focusCurrent();if(!result.getBoolean("success"))Toast.makeText(this,"返回未完成，请重试",Toast.LENGTH_SHORT).show();}});}
            catch(Exception error){main.post(()->{if(!ending)notice("返回连接中断");});}});
    }
    private void backFromGesture(){
        if(activeDialog!=null&&activeDialog.isShowing()){
            getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("last_back_event","DIALOG_BACK").apply();
            activeDialog.onBackPressed();
        }else sendBack();
    }
    @Override public void onBackPressed(){if(backGesture!=null)backGesture.systemBack();else sendBack();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){loads.interaction(android.os.SystemClock.uptimeMillis());dockVisibility.interact();}
        return touchGestures==null?dispatchBackTouch(event):touchGestures.dispatch(event,this::dispatchBackTouch);
    }
    private boolean dispatchBackTouch(MotionEvent event){return backGesture==null?super.dispatchTouchEvent(event):backGesture.dispatch(event,super::dispatchTouchEvent);}
    private void windowMenu(){AppWindow w=primary();showWorkbenchDialog(StageMenu.create(this,presentation.immersive()?restoreButton:toolbar.menuAnchor(),w==null?null:w.name,memoryTotal()<0?"正在采样":"约 "+memorySize(memoryTotal()),presentation.workspaceFullscreen(),new StageMenu.Actions(){
        public void freeform(){arrangementPanel();}public void minimize(){minimizeWindow(primary());}
        public void rail(){toggleRail();}public void dock(){dockSettings();}public void shortcuts(){showShortcuts();}
        public void back(){sendBack();}public void fullscreen(){setFullscreen(!presentation.workspaceFullscreen());}public void appFullscreen(){enterAppFullscreen();}public void apps(){pickApp();}
        public void combinations(){manageWorkspaces();}public void appearance(){editStyle();}public void split(){editGroup();}
        public void memory(){showMemory();}public void remove(){if(w!=null)StageActivity.this.remove(w);}public void exit(){returnHome();}
    }));}
    private void setFullscreen(boolean value){
        if(ending||presentation.workspaceFullscreen()==value)return;
        if(value&&primary()==null){pickApp();return;}
        cancelPresentationTouches();presentation.setWorkspaceFullscreen(value);updatePresentation();
    }
    private void enterAppFullscreen(){
        AppWindow w=primary();if(ending||w==null)return;
        if(!w.ready){notice("应用正在打开，请稍后进入全屏");return;}
        cancelPresentationTouches();pendingSelection=-1;
        if(presentation.enterApp(w.id))updatePresentation();
    }
    private void leaveAppFullscreen(){
        if(ending||!presentation.leaveApp())return;
        cancelPresentationTouches();updatePresentation();
    }
    private void cancelPresentationTouches(){
        if(backGesture!=null)backGesture.cancel();trackingRail=false;touchedSide=null;touchedAddCard=false;
        for(AppWindow w:new ArrayList<>(windows)){w.cancelTouch();if(w.closing)remove(w);else w.sweep.snap(0);}
    }
    private void updatePresentation(){
        toolbar.setVisibility(presentation.immersive()||railCollapsed?View.GONE:View.VISIBLE);
        restoreButton.setVisibility(presentation.immersive()||railCollapsed?View.VISIBLE:View.GONE);
        restoreButton.setText(presentation.appFullscreen()?getString(R.string.stage_return_workspace):"菜单");
        restoreButton.setContentDescription(presentation.appFullscreen()?getString(R.string.stage_return_workspace_description):"显示工作台菜单");
        FrameLayout.LayoutParams button=(FrameLayout.LayoutParams)restoreButton.getLayoutParams();button.width=dp(presentation.appFullscreen()?112:64);restoreButton.setLayoutParams(button);
        scene.setBackgroundColor(presentation.appFullscreen()?Color.BLACK:Color.TRANSPARENT);
        configureFullscreenWindow();
        // Lay out after the inset change; a full-screen toggle is a View transform only.
        scene.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){scene.getViewTreeObserver().removeOnPreDrawListener(this);if(!ending){arrange(false);saveState();}return true;}
        });
        scene.requestLayout();refreshDock();updateRailReveal();focusCurrent();
    }
    private void configureFullscreenWindow(){
        getWindow().setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams attributes=getWindow().getAttributes();
        // Keep cutout policy constant so fullscreen does not change the host configuration.
        attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(attributes);
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null){
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if(presentation.immersive())controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());
        }
        backdropHost.fullscreen(presentation.immersive());
    }
    private void positionRestore(WindowInsets insets){
        android.graphics.Insets cutout=insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)restoreButton.getLayoutParams();
        int bottom=cutout.bottom+dp(8),left=cutout.left+dp(8);
        if(layout.bottomMargin!=bottom||layout.leftMargin!=left){layout.bottomMargin=bottom;layout.leftMargin=left;restoreButton.setLayoutParams(layout);}
    }
    private void remove(AppWindow w){
        if(w.removed)return;
        liveReflow=true;
        w.removed=true;if(w.load!=null)loads.cancel(w.load,android.os.SystemClock.uptimeMillis());w.cancelTouch();windows.remove(w);slots.remove(w.id);layoutDirty=true;scene.removeView(w);scene.removeView(w.badge);scene.removeView(w.chrome);
        if(presentation.windowRemoved(w.id))updatePresentation();
        if(pendingSelection==w.id)pendingSelection=-1;
        if(touchedSide==w){touchedSide=null;trackingRail=false;}
        IWorkbenchService remote=service;if(remote!=null&&w.display>=0)worker.execute(()->{try{remote.closeLiveWindow(w.display,session);}catch(Exception ignored){}finally{main.post(this::focusCurrent);}});
        arrange(true);focusCurrent();changed();
    }
    private AppWindow byId(int id){for(AppWindow w:windows)if(w.id==id)return w;return null;}
    private void pickApp(){
        if(ending)return;if(appPanel!=null&&appPanel.isShowing())return;
        appPanel=new AppPanel(this,new AppPanel.Host(){
            public List<AppPanel.Item> selected(){
                List<AppPanel.Item> result=new ArrayList<>();
                for(int i=0;i<slots.size();i++)for(AppWindow w:windows)if(slots.slotOf(w.id)==i)
                    result.add(new AppPanel.Item(w.id,w.component,w.name+(slots.card(w.id).apps.size()>1?" · 组合":""),i==0,w.ready,w.failed,i,slots.size()));
                return result;
            }
            public void add(String component){addApp(component);}
            public void remove(int id){AppWindow w=byId(id);if(w!=null)StageActivity.this.remove(w);}
            public void move(int id,int delta){if(slots.moveSide(id,delta)){arrange(true);changed();}}
            public void select(int id){AppWindow w=byId(id);if(w!=null)StageActivity.this.select(w);}
        });
        AppPanel panel=appPanel;activeDialog=panel;
        panel.setOnDismissListener(d->{if(activeDialog==panel)activeDialog=null;if(appPanel==panel)appPanel=null;recoverAfterDialog();});
        panel.show();backGesture.watchDialog(panel);focusPicker();
    }
    private void focusCurrent(){if(ending||externalPicker||paused||stopped)return;if(activeDialog!=null&&activeDialog.isShowing())focusPicker();else focusPrimary();}
    private void recoverAfterDialog(){
        focusCurrent();
    }
    private void changed(){
        refreshDock();memoryGeneration++;saveState();if(appPanel!=null)appPanel.refresh();renderMemory();
        if(presentation.immersive()||layoutDirty)main.post(this::relayout);
        main.removeCallbacks(memoryTick);if(!ending)main.postDelayed(memoryTick,1200);
    }
    private void sampleMemory(){
        if(ending||stopped||sampling||service==null)return;
        if(loads.busy()){main.postDelayed(memoryTick,1500);return;}sampling=true;int generation=memoryGeneration;IWorkbenchService remote=service;
        sampler.execute(()->{Bundle result;try{result=remote.liveMemory(session);}catch(Exception error){result=new Bundle();}
            Bundle received=result;main.post(()->{sampling=false;if(ending)return;memoryReport=received;renderMemory();
                main.removeCallbacks(memoryTick);main.postDelayed(memoryTick,generation==memoryGeneration?15000:1200);});});
    }
    private static String memorySize(long kb){
        if(kb<0)return "暂不可用";
        return kb>=1024*1024?String.format(java.util.Locale.ROOT,"%.2f GB",kb/1048576d):Math.round(kb/1024d)+" MB";
    }
    private Bundle memoryUsage(){return memoryReport==null?null:memoryReport.getBundle("usage");}
    private long memoryTotal(){Bundle values=memoryUsage();if(values==null)return -1;long sum=0;boolean any=false;
        List<String> keys=new ArrayList<>();keys.add(MemorySampler.HOST);keys.add(MemorySampler.HELPER);
        for(AppWindow w:windows)keys.add(ComponentName.unflattenFromString(w.component).getPackageName());
        for(String key:keys){long value=values.getLong(key,-1);if(value>=0){sum+=value;any=true;}}
        return any?sum:-1;
    }
    private boolean memoryComplete(){Bundle values=memoryUsage();if(values==null)return false;
        if(values.getLong(MemorySampler.HOST,-1)<0||values.getLong(MemorySampler.HELPER,-1)<0)return false;
        for(AppWindow w:windows)if(values.getLong(ComponentName.unflattenFromString(w.component).getPackageName(),-1)<0)return false;
        return memoryReport.getBoolean("complete");
    }
    private void renderMemory(){
        long total=memoryTotal();
        if(memoryRows!=null){
            memoryRows.removeAllViews();addMemoryRow("工作台与已加入应用",total<0?"暂不可用":"约 "+memorySize(total));
            if(memoryReport!=null)addMemoryRow("系统可用 / 总内存",memorySize(memoryReport.getLong("availableKb",-1))+" / "+memorySize(memoryReport.getLong("deviceKb",-1)));
            Bundle usage=memoryUsage();addMemoryRow("开放工作台",usage==null?"暂不可用":memorySize(usage.getLong(MemorySampler.HOST,-1)));
            addMemoryRow("内置服务",usage==null?"暂不可用":memorySize(usage.getLong(MemorySampler.HELPER,-1)));
            for(int i=0;i<slots.size();i++)for(AppWindow w:windows)if(slots.slotOf(w.id)==i)addMemoryRow(w.name+(i==0?" · 主窗":""),usage==null?"暂不可用":memorySize(usage.getLong(ComponentName.unflattenFromString(w.component).getPackageName(),-1)));
        }
        if(memoryReport!=null){
            org.json.JSONObject report=new org.json.JSONObject();try{report.put("totalKb",total).put("complete",memoryComplete()).put("durationMs",memoryReport.getLong("durationMs")).put("cached",memoryReport.getBoolean("cached"));Bundle usage=memoryUsage();if(usage!=null)for(String key:usage.keySet())report.put(key,usage.getLong(key));}catch(Exception ignored){}
            getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("memory_report",report.toString()).apply();
        }
    }
    private void addMemoryRow(String name,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(14),dp(12),dp(14));
        TextView label=WorkbenchUi.text(this,name,14,WorkbenchUi.INK);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount=WorkbenchUi.text(this,value,14,WorkbenchUi.ACCENT);amount.setPadding(dp(12),0,0,0);row.addView(amount);memoryRows.addView(row);
    }
    private void showMemory(){
        if(ending)return;WorkbenchDialog dialog=new WorkbenchDialog(this,"内存占用","后台采样 · 约每 15 秒更新",480);
        memoryRows=WorkbenchUi.column(dialog.getContext());dialog.body.addView(memoryRows);dialog.divider();
        TextView note=WorkbenchUi.text(dialog.getContext(),"按进程估计占用，包含应用原有内存。共享内存按比例计入（PSS），不等于加入工作台后新增的开销。系统可能缓存读数；部分图形和系统进程开销未包含。",12,WorkbenchUi.MUTED);note.setPadding(dp(12),0,dp(12),dp(8));dialog.body.addView(note);
        dialog.action("完成",false,dialog::dismiss);memoryDialog=dialog;renderMemory();showWorkbenchDialog(dialog);
    }
    private void leave(){if(ending)return;ending=true;release();finish();}
    private void returnHome(){
        if(ending)return;
        startActivity(new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_SHOW_HOME,true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        leave();
    }
    private void release(){
        keyboard.release(service,session);stopAnimation();main.removeCallbacks(expireBackground);main.removeCallbacks(armRailDrag);main.removeCallbacks(hideRailAgain);loads.clear(android.os.SystemClock.uptimeMillis());main.removeCallbacks(loadTick);main.removeCallbacks(memoryTick);saveState();
        if(activeDialog!=null&&activeDialog.isShowing())activeDialog.dismiss();
        for(AppWindow w:windows)w.cancelTouch();IWorkbenchService remote=service;
        if(remote!=null&&!worker.isShutdown())worker.execute(()->{try{remote.closeLiveStage(session);}catch(Exception ignored){}});
    }
    @Override protected void onPause(){if(splitDivider!=null)splitDivider.cancel();keyboard.release(service,session);paused=true;dockVisibility.hold(true);super.onPause();}
    @Override protected void onResume(){super.onResume();paused=false;stopped=false;main.removeCallbacks(expireBackground);if(!imagePicking)externalPicker=false;if(started){main.post(this::focusCurrent);scheduleLoads();main.post(memoryTick);loadDockRecents();}}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(stopped&&level>=TRIM_MEMORY_BACKGROUND&&!ending)leave();}
    @Override protected void onStop(){super.onStop();stopped=true;if(!ending&&!externalPicker&&!isChangingConfigurations()){
        stopAnimation();for(AppWindow w:windows)w.cancelTouch();main.removeCallbacks(loadTick);main.removeCallbacks(memoryTick);saveState();main.postDelayed(expireBackground,60000);
    }}
    @Override protected void onDestroy(){if(imeLayer!=null)imeLayer.close();if(backGesture!=null)backGesture.close();if(active.get()==this)active.clear();BridgeState.remove(bridgeListener);if(!ending)release();ending=true;worker.shutdown();sampler.shutdown();icons.shutdownNow();background.close();dockRecents.close();dockVisibility.close();if(dockCatalog!=null)dockCatalog.close();if(packageEvents!=null)packageEvents.close();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration config){
        liveReflow=false;
        if(splitDivider!=null)splitDivider.cancel();
        super.onConfigurationChanged(config);if(backGesture!=null)backGesture.cancel();configureFullscreenWindow();layoutDirty=true;scene.requestLayout();
    }
    private void relayout(){
        if(ending||scene.getWidth()==0||scene.getHeight()==0)return;
        FrameLayout.LayoutParams bar=(FrameLayout.LayoutParams)toolbar.getLayoutParams();int barWidth=Math.round(railWidth());
        if(bar.width!=barWidth){bar.width=barWidth;toolbar.setLayoutParams(bar);}
        toolbar.fit(barWidth);
        toolbar.setTranslationX(style.railRight?scene.getWidth()-barWidth:0);
        android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();
        CanvasSize size=CanvasSize.from(bounds.width(),bounds.height(),getResources().getDisplayMetrics().densityDpi);
        if(size.width!=logicalWidth||size.height!=logicalHeight||size.density!=density||layoutDirty){layoutDirty=false;resizeCanvas(size);}
        arrange(liveReflow);
        saveState();
        refreshDock();if(appPanel!=null&&appPanel.isShowing())appPanel.configurationChanged();
        if(activeDialog instanceof WorkbenchDialog)((WorkbenchDialog)activeDialog).fit();
    }
    private void resizeCanvas(CanvasSize size){
        boolean liveSplit=splitPreview!=null,keepLive=liveSplit||liveReflow;
        for(AppWindow w:new ArrayList<>(windows))if(w.closing)remove(w);
        int oldWidth=logicalWidth,oldHeight=logicalHeight,oldDensity=density;
        logicalWidth=size.width;logicalHeight=size.height;density=size.density;
        trackingRail=false;touchedSide=null;if(!liveReflow)stopAnimation();
        List<AppWindow> changedWindows=new ArrayList<>();
        for(AppWindow w:windows){w.cancelTouch();w.sweep.snap(0);if(resizeSurface(w))changedWindows.add(w);}
        IWorkbenchService remote=service;if(remote==null||windows.isEmpty()){splitPreview=null;return;}
        // Card removal and rail reflow need new positions, but usually leave every app canvas
        // unchanged. Keep their live frames visible and avoid even a no-op resize request.
        List<CanvasUpdate> updates=new ArrayList<>();
        for(AppWindow w:changedWindows)if(w.display>=0)updates.add(new CanvasUpdate(w));
        if(updates.isEmpty()){splitPreview=null;return;}
        resizing=true;int generation=++canvasGeneration;
        for(AppWindow w:changedWindows)if(w.ready&&!keepLive){w.cover.showStatus("正在调整窗口");}
        worker.execute(()->{
            Bundle result=new Bundle();result.putBoolean("success",true);
            for(CanvasUpdate update:updates)try{
                Bundle resized=remote.resizeLiveWindow(update.display,update.size.width,update.size.height,update.size.density,session);
                if(!resized.getBoolean("success"))result.putBoolean("success",false);
                main.post(()->{
                    AppWindow w=update.window;if(ending||w.removed)return;
                    if(resized.getBoolean("success")&&w.bufferEdge>0){w.canvasCommits.add(update.size,resized.getLong("frameAfterNanos"));w.commitCanvasFrame();}
                    else if(w.requestedCanvas==update.size)w.requestedCanvas=null;
                });
            }
                catch(Exception e){result.putBoolean("success",false);result.putString("detail",e.toString());}
            Bundle response=result;main.post(()->{
                if(ending||generation!=canvasGeneration)return;resizing=false;if(liveSplit)splitPreview=null;
                if(!response.getBoolean("success")){
                    logicalWidth=oldWidth;logicalHeight=oldHeight;density=oldDensity;
                    for(CanvasUpdate update:updates)if(update.window.requestedCanvas==update.size)update.window.requestedCanvas=null;
                    for(AppWindow w:windows)if(w.bufferEdge==0)resizeSurface(w);
                    Toast.makeText(this,"应用方向调整失败，请退出后重新进入",Toast.LENGTH_LONG).show();
                }
                
                for(AppWindow w:windows)w.showFrame();arrange(liveReflow);saveState();focusCurrent();
            });
        });
    }
    private final class CanvasUpdate{
        final AppWindow window;final int display;final CanvasSize size;
        CanvasUpdate(AppWindow w){window=w;display=w.display;size=w.requestedCanvas!=null?w.requestedCanvas:CanvasSize.nativeDisplay(w.canvasWidth,w.canvasHeight,w.canvasDensity);}
    }
    private boolean resizeSurface(AppWindow w){
        StageLayout canvas=canvasLayout();WorkspaceCard card=slots.card(w.id);
        StageLayout.Bounds pane=card.freeform?canvas.freeMember(canvas.target(0,0),card.placements.get(w.windowId)):canvas.member(canvas.target(0,0),memberIndex(w,card),card.weights,card.axis);
        StageLayout.Bounds cardCanvas=canvas.target(0,0);
        CanvasSize size=CanvasSize.paneWithin(Math.max(1,Math.round(pane.width())),Math.max(1,Math.round(pane.height())),getResources().getDisplayMetrics().densityDpi,
                Math.max(1,Math.round(cardCanvas.width())),Math.max(1,Math.round(cardCanvas.height())));
        android.graphics.Rect nativeBounds=getWindowManager().getMaximumWindowMetrics().getBounds();
        size=WindowCanvasPolicy.resolve(android.os.Build.MANUFACTURER,w.component,size,nativeBounds.width(),nativeBounds.height(),getResources().getDisplayMetrics().densityDpi);
        CanvasSize prior=w.requestedCanvas;
        if(w.ready&&w.bufferEdge>0)size=size.preservingDensityOnExpansion(prior==null?w.canvasWidth:prior.width,prior==null?w.canvasHeight:prior.height,prior==null?w.canvasDensity:prior.density);
        boolean changed=(prior==null?w.canvasWidth:prior.width)!=size.width||(prior==null?w.canvasHeight:prior.height)!=size.height||(prior==null?w.canvasDensity:prior.density)!=size.density;
        if(!changed&&w.getLayoutParams()!=null)return false;
        if(w.bufferEdge>0&&w.ready&&w.display>=0)w.requestedCanvas=size;
        else w.applyCanvasSize(size);
        return changed;
    }
    private StageLayout.Bounds memberBounds(AppWindow w,StageLayout.Bounds cardBounds){
        WorkspaceCard card=slots.card(w.id);if(card==null)return cardBounds;
        if(previewing(w))card=splitPreview;
        if(card.freeform)return layout().freeMember(cardBounds,card.placements.get(w.windowId));
        if(card.apps.size()>1){StageLayout canvas=canvasLayout();StageLayout.Bounds reference=canvas.target(0,0);
            return StageLayout.mapMember(cardBounds,reference,canvas.member(reference,memberIndex(w,card),card.weights,card.axis));}
        return layout().member(cardBounds,memberIndex(w,card),card.weights,card.axis);
    }
    private boolean previewing(AppWindow w){return splitPreview!=null&&!presentation.appFullscreen()&&slots.primaryCard(w.id)&&splitPreview.id.equals(slots.card(w.id).id);}
    private int memberIndex(AppWindow w,WorkspaceCard card){for(int i=0;i<card.apps.size();i++)if(card.apps.get(i).id.equals(w.windowId))return i;return 0;}
    private StageLayout.Bounds windowTarget(AppWindow w){
        StageLayout.Bounds pane=presentation.isFullscreenApp(w.id)?layout().fullscreenApp():memberBounds(w,target(slots.slotOf(w.id)));
        if(previewing(w))return pane;
        CanvasSize size=w.requestedCanvas;
        return StageLayout.fitCanvas(pane,size==null?w.canvasWidth:size.width,size==null?w.canvasHeight:size.height);
    }
    private List<AppWindow> cardMembers(AppWindow w){
        List<AppWindow> result=new ArrayList<>();WorkspaceCard card=slots.card(w.id);for(AppWindow member:windows)if(slots.card(member.id)==card)result.add(member);return result;
    }
    private String cardName(AppWindow w){
        WorkspaceCard card=slots.card(w.id);if(card==null)return w.name;
        if(namingDefinition!=draft.snapshot()||namingCount!=windows.size()){
            namingDefinition=draft.snapshot();namingCount=windows.size();cardNames.clear();
            for(WorkspaceCard item:namingDefinition.cards){List<String> names=new ArrayList<>();
                for(cn.paperfly.workbench.model.AppTarget app:item.apps)for(AppWindow member:windows)if(member.windowId.equals(app.id))names.add(member.name);
                cardNames.put(item.id,android.text.TextUtils.join(" + ",names));}
        }
        return cardNames.getOrDefault(card.id,w.name);
    }
    private void saveState(){
        List<String> components=new ArrayList<>();org.json.JSONArray stats=new org.json.JSONArray();
        for(int i=0;i<slots.size();i++)for(AppWindow w:windows)if(slots.slotOf(w.id)==i){
            components.add(w.windowId);
            try{stats.put(new org.json.JSONObject().put("name",w.name).put("slot",i).put("display",w.display).put("frames",w.frames)
                    .put("component",w.component).put("windowId",w.windowId).put("logicalWidth",w.canvasWidth).put("logicalHeight",w.canvasHeight).put("bufferEdge",w.bufferEdge).put("cardId",slots.card(w.id).id).put("focused",slots.primary()==w.id).put("x",w.x.value).put("y",w.y.value).put("width",w.width.value)
                    .put("corners",projectedCorners(w,w.canvasWidth,w.canvasHeight)));}catch(Exception ignored){}
        }
        if(draft!=null&&started){
            draft.orderVisible(components);
            WorkspaceDefinition snapshot=draft.snapshot();
            if(snapshot!=lastQueuedDraft){lastQueuedDraft=snapshot;persistence.saveCurrent(snapshot,this::saveFailed);}
        }
        getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("visible_components",new org.json.JSONArray(components).toString())
                .putString("live_scene",stats.toString()).putBoolean("canvas_resizing",resizing).putInt("animation_frames",animationFrames)
                .putBoolean("live_fullscreen",presentation.workspaceFullscreen()).putBoolean("live_app_fullscreen",presentation.appFullscreen()).putInt("scene_width",scene.getWidth()).putInt("scene_height",scene.getHeight()).apply();
        org.json.JSONObject card=new org.json.JSONObject();try{StageLayout.Bounds b=target(Math.max(1,slots.size()));card.put("x",b.left).put("y",b.top).put("width",addCard.getLayoutParams().width).put("height",addCard.getLayoutParams().height).put("visible",addCard.getVisibility()==View.VISIBLE)
                    .put("corners",projectedCorners(addCard,addCard.getLayoutParams().width,addCard.getLayoutParams().height));}catch(Exception ignored){}
        getSharedPreferences("workspace",MODE_PRIVATE).edit().putString("rail_add",card.toString()).apply();
    }
    private org.json.JSONArray projectedCorners(View view,int width,int height)throws org.json.JSONException{
        float[] points={0,0,width,0,width,height,0,height};view.getMatrix().mapPoints(points);
        org.json.JSONArray result=new org.json.JSONArray();for(int i=0;i<points.length;i++)result.put(points[i]+(i%2==0?view.getLeft():view.getTop()));return result;
    }
    private void showWorkbenchDialog(android.app.Dialog dialog){
        if(dialog instanceof WorkbenchDialog)((WorkbenchDialog)dialog).preferHostSurface();
        activeDialog=dialog;dialog.setOnDismissListener(d->{if(activeDialog==dialog)activeDialog=null;if(memoryDialog==dialog){memoryDialog=null;memoryRows=null;}recoverAfterDialog();});dialog.show();backGesture.watchDialog(dialog);focusPicker();
    }
    private void manageWorkspaces(){if(draft==null)return;saveState();new WorkspaceManager(this,draft.snapshot(),this::showWorkbenchDialog,this::applyDefinition).display();}
    private void editStyle(){if(draft!=null)WorkspaceStylePanel.show(this,style,this::showWorkbenchDialog,this::applyStyle,this::chooseBackground);}
    private void applyStyle(WorkspaceStyle value){
        style=value;draft.style(value);background.apply(this,backdropHost,style);layoutDirty=true;relayout();saveState();
    }
    private void chooseBackground(){
        externalPicker=true;imagePicking=true;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,501);}catch(Exception e){externalPicker=false;imagePicking=false;Toast.makeText(this,"无法打开图片选择器",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=501)return;externalPicker=false;imagePicking=false;
        if(result==RESULT_OK&&data!=null&&data.getData()!=null){
            try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
                applyStyle(new WorkspaceStyle(style.backgroundStart,style.backgroundMiddle,style.backgroundEnd,style.portraitRailDp,style.landscapeRailDp,
                        style.maxRailFraction,style.railRight,style.compactCards,style.primaryScale,data.getData().toString(),style.aspectRatio));}
            catch(Exception e){Toast.makeText(this,"未能保存图片访问权限，请重新选择",Toast.LENGTH_LONG).show();}
        }
        focusCurrent();
    }
    private void applyDefinition(WorkspaceDefinition definition){
        if(ending)return;stopAnimation();loads.clear(android.os.SystemClock.uptimeMillis());main.removeCallbacks(loadTick);pendingSelection=-1;trackingRail=false;railOffset=0;
        if(presentation.leaveApp())updatePresentation();
        if(activeDialog!=null)activeDialog.dismiss();
        for(AppWindow w:windows){w.cancelTouch();w.removed=true;scene.removeView(w);scene.removeView(w.badge);scene.removeView(w.chrome);}windows.clear();presentation.resetWindows();
        draft=new WorkspaceDraft(definition);slots.bind(draft);lastQueuedDraft=definition;persistence.saveCurrent(definition,this::saveFailed);style=definition.style;background.apply(this,backdropHost,style);
        initial=draft.targets();started=false;IWorkbenchService remote=service;
        worker.execute(()->{try{if(remote!=null)remote.closeLiveStage(session);}catch(Exception ignored){}
            main.post(()->{if(!ending){layoutDirty=true;relayout();connect();}});});
    }
    private void editGroup(){
        AppWindow current=primary();if(current==null){pickApp();return;}
        WorkspaceCard card=slots.card(current.id);List<SplitCardPanel.Item> choices=new ArrayList<>();
        for(AppWindow w:windows)if(!slots.primaryCard(w.id)&&slots.representative(w.id)==w.id)choices.add(new SplitCardPanel.Item(w.id,cardName(w)));
        showWorkbenchDialog(SplitCardPanel.create(this,card,cardName(current),choices,
                ()->{slots.splitPrimary();groupChanged();},id->{slots.mergeIntoPrimary(id);groupChanged();},
                (ratio,axis)->{slots.splitOptions(ratio,axis);groupChanged();}));
    }
    private void groupChanged(){layoutDirty=true;relayout();arrange(true);focusCurrent();changed();}

    private void splitWindowActions(WorkspaceCard card,int boundary,boolean horizontal,View anchor){
        AppWindow current=primary();
        if(ending||resizing||presentation.appFullscreen()||current==null||!slots.card(current.id).id.equals(card.id)
                ||boundary<0||boundary+1>=card.apps.size())return;
        List<SplitWindowMenu.Item> items=new ArrayList<>();
        for(int i=boundary;i<=boundary+1;i++){
            String windowId=card.apps.get(i).id;
            for(AppWindow w:windows)if(w.windowId.equals(windowId)&&!w.removed&&!w.closing){
                String position=horizontal?(i==boundary?"左侧窗口":"右侧窗口"):(i==boundary?"上方窗口":"下方窗口");
                items.add(new SplitWindowMenu.Item(w.name,position,w.badge.icon.getDrawable(),()->{if(!ending&&!w.removed)remove(w);}));break;
            }
        }
        if(items.size()==2)showWorkbenchDialog(SplitWindowMenu.create(this,anchor,items,this::editGroup));
    }

    private void refreshDock(){
        if(dock==null||draft==null)return;WorkspaceOptions options=draft.snapshot().options;dockVisibility.configure(options.dockVisible,options.dockAutoHide);
        dockVisibility.hold(paused||dockDragging||splitDivider!=null&&splitDivider.dragging()||activeDialog!=null&&activeDialog.isShowing());dockShown=dockVisibility.visible();
        dock.setVisibility(dockShown&&!presentation.appFullscreen()?View.VISIBLE:View.GONE);
        if(dockReveal!=null)dockReveal.setVisibility(dockVisibility.revealAvailable()&&!presentation.appFullscreen()?View.VISIBLE:View.GONE);
        List<DockModel.Window> running=new ArrayList<>();for(AppWindow w:windows)running.add(new DockModel.Window(w.id,w.component));
        List<String> recent=new ArrayList<>(options.recentApps);recent.addAll(recentDockApps);
        recent.removeIf(component->dockCatalog.get(component,this::refreshDock).unavailable);
        List<StageDock.Item> items=new ArrayList<>();
        for(DockModel.Entry entry:DockModel.entries(options.pinnedApps,running,recent)){AppWindow w=byId(entry.id);DockCatalog.Asset asset=dockCatalog.get(entry.component,this::refreshDock);
            items.add(new StageDock.Item(entry.id,entry.component,w==null?asset.name:w.name,w==null?asset.icon:w.badge.icon.getDrawable(),w!=null&&slots.primary()==w.id&&!presentation.minimized(w.id),w!=null&&presentation.minimized(w.id),entry.section));}
        dock.render(items,item->{AppWindow w=byId(item.id);if(w!=null){select(w);arrange(true);}else addApp(item.component);dockVisibility.reveal();},item->{AppWindow w=byId(item.id);if(w!=null)windowActions(w);else launcherActions(item.component);},this::pickApp);
    }
    private void dockVisibilityChanged(boolean visible){dockShown=visible;if(dock!=null){refreshDock();arrange(true);}}
    private void loadDockRecents(){if(ending||service==null)return;dockRecents.load(apps->{if(ending)return;recentDockApps.clear();for(String app:apps)if(!new AppTarget(app).packageName.equals(getPackageName()))recentDockApps.add(app);refreshDock();},message->{});}
    private void updateOptions(WorkspaceOptions options){if(draft==null)return;draft.options(options);refreshDock();arrange(true);saveState();}
    private void dockSettings(){new DockPreferencesPanel(this,()->draft.snapshot().options,this::updateOptions,this::showWorkbenchDialog,component->dockCatalog.get(component,()->{}).name).display();}
    private void pinApp(String component,boolean pinned){try{updateOptions(draft.snapshot().options.pin(component,pinned));}catch(IllegalArgumentException error){notice("最多固定 24 个应用");}}
    private void launcherActions(String component){WorkbenchDialog dialog=new WorkbenchDialog(this,dockCatalog.get(component,()->{}).name,"Dock 应用",320);
        dialog.row(WorkbenchIcon.Kind.APPS,"打开应用",null,false,false,()->addApp(component));boolean pinned=draft.snapshot().options.pinnedApps.contains(component);
        dialog.row(WorkbenchIcon.Kind.COLLECTION,pinned?"取消固定":"固定到 Dock",null,false,false,()->pinApp(component,!pinned));showWorkbenchDialog(dialog);
    }
    private boolean handleDockDrag(android.view.DragEvent event){
        if(!(event.getLocalState() instanceof StageDock.Drag)||ending)return false;
        StageDock.Drag drag=(StageDock.Drag)event.getLocalState();AppWindow window=byId(drag.id);
        if(event.getAction()==android.view.DragEvent.ACTION_DRAG_STARTED){dockDragging=true;dockVisibility.hold(true);}
        if(event.getAction()==android.view.DragEvent.ACTION_DRAG_ENDED){dockDragging=false;refreshDock();if(!drag.moved){if(window!=null)windowActions(window);else launcherActions(drag.component);}return true;}
        if(event.getAction()==android.view.DragEvent.ACTION_DROP){
            if(event.getY()>scene.getHeight()-dp(72))return false;
            if(window==null){addTarget(new AppTarget(drag.component));for(AppWindow candidate:windows)if(candidate.component.equals(drag.component)){window=candidate;break;}if(window==null)return false;pendingSelection=window.id;}
            boolean inRail=!railCollapsed&&(style.railRight?event.getX()>scene.getWidth()-railWidth():event.getX()<railWidth());
            if(inRail){AppWindow destination=null;for(AppWindow candidate:windows)if(!slots.primaryCard(candidate.id)){StageLayout.Bounds bounds=target(slots.slotOf(candidate.id));if(event.getY()>=bounds.top&&event.getY()<=bounds.bottom){destination=candidate;break;}}
                if(destination==null)slots.detach(window.id);else slots.moveMember(window.id,destination.id);
            }else if(!slots.primaryCard(window.id)){
                AppWindow current=primary();if(current!=null)slots.moveMember(window.id,current.id);
            }
            presentation.restore(window.id);draft.options(draft.snapshot().options.minimized(window.windowId,false));groupChanged();drag.moved=true;return true;
        }
        
        return true;
    }
    private boolean isWindowShown(AppWindow window){return presentation.showsWindow(window.id);}
    private void windowActions(AppWindow w){
        WorkbenchDialog d=new WorkbenchDialog(this,w.name,"窗口操作",320);
        d.row(WorkbenchIcon.Kind.APPS,"切换到窗口",null,false,false,()->select(w));
        d.row(WorkbenchIcon.Kind.FULLSCREEN,"当前应用全屏",null,false,false,()->{select(w);enterAppFullscreen();});
        d.row(WorkbenchIcon.Kind.PLUS,"新建此应用窗口",null,false,false,()->newInstance(w));
        boolean pinned=draft.snapshot().options.pinnedApps.contains(w.component);d.row(WorkbenchIcon.Kind.COLLECTION,pinned?"取消 Dock 固定":"固定到 Dock",null,false,false,()->pinApp(w.component,!pinned));
        d.row(WorkbenchIcon.Kind.BACK,"最小化",null,false,false,()->minimizeWindow(w));
        if(slots.card(w.id).apps.size()>1)d.row(WorkbenchIcon.Kind.SPLIT,"移出分组，保留窗口",null,false,false,()->{slots.detach(w.id);groupChanged();});
        d.row(WorkbenchIcon.Kind.CLOSE,"关闭此窗口","仅移出这个应用，其他窗口保留",false,true,()->remove(w));showWorkbenchDialog(d);
    }
    private void newInstance(AppWindow w){
        try{android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(ComponentName.unflattenFromString(w.component),0);
            if(!InstancePolicy.supports(info.launchMode)){notice("此应用限制单一任务，无法新建独立窗口");return;}
            addTarget(new AppTarget(java.util.UUID.randomUUID().toString(),w.component));
        }catch(Exception error){notice("暂时无法新建窗口");}
    }
    private void minimizeWindow(AppWindow w){
        if(w==null)return;w.cancelTouch();presentation.minimize(w.id);draft.options(draft.snapshot().options.minimized(w.windowId,true).visible(true));if(presentation.isFullscreenApp(w.id))leaveAppFullscreen();
        if(slots.primary()==w.id){
            List<AppWindow> candidates=cardMembers(w);for(AppWindow other:windows)if(!candidates.contains(other))candidates.add(other);
            for(AppWindow next:candidates)if(next!=w&&next.ready&&!next.closing&&!presentation.minimized(next.id)){select(next);break;}
        }
        dockVisibility.reveal();arrange(true);refreshDock();saveState();focusCurrent();
    }
    private void toggleFreeform(){AppWindow w=primary();if(w==null)return;slots.freeform(w.id,!slots.card(w.id).freeform);groupChanged();}
    private void arrangementPanel(){AppWindow current=primary();if(current==null)return;
        showWorkbenchDialog(WindowArrangementPanel.create(this,slots.card(current.id).freeform,new WindowArrangementPanel.Actions(){
            public void split(){editGroup();}
            public void freeform(){toggleFreeform();}
            public void half(boolean trailing,boolean horizontal){slots.freeform(current.id,true);slots.place(current.id,cn.paperfly.workbench.model.WindowArrangements.half(trailing,horizontal));groupChanged();}
            public void tile(){arrangeGroup(false);}public void cascade(){arrangeGroup(true);}
        }));
    }
    private void arrangeGroup(boolean cascade){AppWindow current=primary();if(current==null)return;List<AppWindow> members=cardMembers(current);
        if(members.size()>36&&!cascade){notice("请先将此组拆分后平铺");return;}
        slots.freeform(current.id,true);for(int i=0;i<members.size();i++)slots.place(members.get(i).id,cascade?WindowPlacement.initial(i,members.size()):cn.paperfly.workbench.model.WindowArrangements.tile(i,members.size(),scene.getWidth()>scene.getHeight()));groupChanged();
    }
    private void toggleRail(){railCollapsed=!railCollapsed;geometry=null;toolbar.setVisibility(railCollapsed?View.GONE:presentation.immersive()?View.GONE:View.VISIBLE);
        restoreButton.setVisibility(railCollapsed||presentation.immersive()?View.VISIBLE:View.GONE);railAutoHide=railCollapsed;updateRailReveal();arrange(true);}
    private void updateRailReveal(){if(railReveal==null)return;railReveal.setVisibility(railCollapsed&&!presentation.appFullscreen()?View.VISIBLE:View.GONE);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)railReveal.getLayoutParams();lp.gravity=Gravity.CENTER_VERTICAL|(style.railRight?Gravity.RIGHT:Gravity.LEFT);railReveal.setLayoutParams(lp);}
    private void revealRail(){if(!railCollapsed)return;toggleRail();railAutoHide=true;main.removeCallbacks(hideRailAgain);main.postDelayed(hideRailAgain,3000);}
    private void packageChanged(String packageName){
        if(ending)return;dockCatalog.invalidate();loadDockRecents();refreshDock();
        for(AppWindow w:new ArrayList<>(windows))if(new AppTarget(w.component).packageName.equals(packageName)){int generation=++w.metadataGeneration;icons.execute(()->{
            String label;android.graphics.drawable.Drawable icon=null;boolean available;
            try{android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(ComponentName.unflattenFromString(w.component),0);available=info.enabled&&info.exported&&info.applicationInfo.enabled;label=info.loadLabel(getPackageManager()).toString();icon=info.loadIcon(getPackageManager());}
            catch(Exception ignored){available=false;label=packageName;}
            boolean readyToRetry=available;String title=label+(available?"":" · 暂不可用")+(w.additionalInstance?" · 新窗口":"");android.graphics.drawable.Drawable loadedIcon=icon;
            main.post(()->{if(ending||w.removed||generation!=w.metadataGeneration)return;w.available=readyToRetry;w.name=title;w.chrome.name(title);w.cover.name(title);w.setContentDescription("应用窗口："+title);namingDefinition=null;
                if(loadedIcon!=null){w.badge.icon.setImageDrawable(loadedIcon);w.cover.icon(loadedIcon);}
                if(!readyToRetry){w.cancelTouch();if(w.load!=null)loads.cancel(w.load,SystemClock.uptimeMillis());w.failed=true;w.ready=false;w.cover.showStatus("应用暂不可用 · 配置已保留");}
                else if(w.failed)retryWindow(w);updateTitle();refreshDock();arrange(false);
            });
        });}
    }
    private void retryWindow(AppWindow w){
        if(ending||w.removed||w.activeSurface==null||w.retrying||!w.available)return;
        if(w.failed&&w.display>=0&&service!=null){w.retrying=true;IWorkbenchService remote=service;int display=w.display;
            worker.execute(()->{boolean closed=false;try{closed=!"会话已结束".equals(remote.closeLiveWindow(display,session));}catch(Exception ignored){}boolean completed=closed;
                main.post(()->{w.retrying=false;if(ending||w.removed)return;if(completed){w.display=-1;w.requested=false;w.frames=0;enqueueRetry(w);}else w.cover.showStatus("连接中断 · 点按重试");});});
        }else enqueueRetry(w);
    }
    private void enqueueRetry(AppWindow w){if(w.activeSurface==null||ending||w.removed||!w.available)return;
        if(w.load!=null)loads.cancel(w.load,SystemClock.uptimeMillis());w.failed=false;w.load=new LoadRequest(w,w.activeSurface);loads.add(w.load);loads.prioritize(w.load);scheduleLoads();
    }
    private void dragWindow(AppWindow w,MotionEvent e,boolean resize){
        int action=e.getActionMasked();if(ending||resizing||presentation.appFullscreen()||!slots.primaryCard(w.id))return;
        if(action==MotionEvent.ACTION_DOWN){select(w);w.cancelTouch();movingWindow=w;dragOriginal=slots.card(w.id).placements.get(w.windowId);dragX=e.getRawX();dragY=e.getRawY();return;}
        if(movingWindow!=w||dragOriginal==null)return;
        if(action==MotionEvent.ACTION_MOVE){StageLayout.Bounds area=target(0);float dx=(e.getRawX()-dragX)/area.width(),dy=(e.getRawY()-dragY)/area.height();
            slots.place(w.id,resize?WindowPlacement.bounded(dragOriginal.x,dragOriginal.y,dragOriginal.width+dx,dragOriginal.height+dy):WindowPlacement.bounded(dragOriginal.x+dx,dragOriginal.y+dy,dragOriginal.width,dragOriginal.height));arrange(false);}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(action==MotionEvent.ACTION_CANCEL)slots.place(w.id,dragOriginal);
            else if(!resize&&!railCollapsed){int[] origin=new int[2];scene.getLocationOnScreen(origin);float x=e.getRawX()-origin[0],y=e.getRawY()-origin[1];
                if(style.railRight?x>scene.getWidth()-railWidth():x<railWidth()){
                    AppWindow destination=null;for(AppWindow candidate:windows)if(!slots.primaryCard(candidate.id)){StageLayout.Bounds b=target(slots.slotOf(candidate.id));if(y>=b.top&&y<=b.bottom){destination=candidate;break;}}
                    if(destination!=null)slots.moveMember(w.id,destination.id);else slots.detach(w.id);layoutDirty=true;
                }}
            movingWindow=null;dragOriginal=null;if(resize||layoutDirty){layoutDirty=true;relayout();}arrange(false);changed();focusCurrent();
        }
    }
    private void cycleWindow(boolean reverse){
        if(windows.isEmpty())return;int index=windows.indexOf(primary());int next=(index+(reverse?-1:1)+windows.size())%windows.size();select(windows.get(next));arrange(true);
    }
    private void showShortcuts(){WorkbenchDialog d=new WorkbenchDialog(this,"手势与快捷键",null,420);
        d.body.addView(WorkbenchUi.text(d.getContext(),"工作台内四指或五指：左右滑动切换窗口，捏合回到桌面。\n多指手势开关仅对本次工作台有效。",15,WorkbenchUi.INK));
        d.action(touchGesturesEnabled?"暂停本次多指手势":"开启本次多指手势",false,()->{touchGesturesEnabled=!touchGesturesEnabled;d.dismiss();});
        d.body.addView(WorkbenchUi.text(d.getContext(),"Alt + Tab：下一个窗口\nAlt + Shift + Tab：上一个窗口\nCtrl + Alt + F：当前应用全屏 / 返回\nCtrl + Alt + M：最小化\nCtrl + Alt + R：显示 / 隐藏侧栏\nCtrl + Alt + D：显示 / 隐藏 Dock\nCtrl + Alt + N：添加应用",15,WorkbenchUi.INK));d.action("完成",false,d::dismiss);showWorkbenchDialog(d);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(keyboard.commandRelease(e))return true;
        if(activeDialog!=null&&activeDialog.isShowing()||externalPicker)return super.dispatchKeyEvent(e);
        boolean command=e.isAltPressed()&&(e.getKeyCode()==KeyEvent.KEYCODE_TAB||e.isCtrlPressed()&&(e.getKeyCode()==KeyEvent.KEYCODE_F||e.getKeyCode()==KeyEvent.KEYCODE_M||e.getKeyCode()==KeyEvent.KEYCODE_R||e.getKeyCode()==KeyEvent.KEYCODE_D||e.getKeyCode()==KeyEvent.KEYCODE_N));
        if(command){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){keyboard.commandDown(e);keyboard.release(service,session);switch(e.getKeyCode()){
            case KeyEvent.KEYCODE_TAB:cycleWindow(e.isShiftPressed());break;
            case KeyEvent.KEYCODE_F:if(presentation.appFullscreen())leaveAppFullscreen();else enterAppFullscreen();break;
            case KeyEvent.KEYCODE_M:minimizeWindow(primary());break;case KeyEvent.KEYCODE_R:toggleRail();break;
            case KeyEvent.KEYCODE_D:updateOptions(draft.snapshot().options.visible(!draft.snapshot().options.dockVisible));break;case KeyEvent.KEYCODE_N:pickApp();break;}}return true;}
        AppWindow w=primary();if(w!=null&&w.ready&&!presentation.minimized(w.id)&&InputPolicy.allowedKey(e.getKeyCode())&&service!=null){
            if(keyboard.forward(service,session,w.display,e))return true;
        }
        return super.dispatchKeyEvent(e);
    }
    private boolean genericMotion(AppWindow w,MotionEvent e){
        if(ending||!w.ready||service==null||!slots.primaryCard(w.id)||!isWindowShown(w))return false;
        try{service.liveTouch(w.display,e,session);return true;}catch(Exception error){return false;}
    }

    private void configurationFailed(String message){
        if(ending||isFinishing()||isDestroyed())return;
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();leave();
    }
    private void saveFailed(String message){
        lastQueuedDraft=null;
        if(ending||isFinishing()||isDestroyed()||saveErrorShown)return;
        saveErrorShown=true;Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.rgb(232,241,242));t.setGravity(Gravity.CENTER_VERTICAL);return t;}
}
