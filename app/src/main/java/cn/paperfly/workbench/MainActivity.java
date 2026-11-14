package cn.paperfly.workbench;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowInsetsController;
import android.widget.Toast;
import cn.paperfly.workbench.data.WorkspacePersistence;
import cn.paperfly.workbench.model.WorkspaceLibrary;

/** A short entry screen. App selection and window management happen inside the workbench. */
public final class MainActivity extends Activity {
    public static final String EXTRA_SHOW_HOME="show_workbench_home";
    private EntryRoute route;
    private WorkbenchController controller;
    private HomeScreen home;
    private boolean recentLoading;
    private final RecentEntry recentEntry=new RecentEntry();
    private WorkspaceLibrary library;
    private Dialog activeDialog;
    private boolean active,launching,routingStage;
    private int loadGeneration;
    private int entryGeneration;
    private boolean authorized,workspaceLoaded,shortcutLoaded;
    private String shortcutId;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);shortcutId=getIntent().getStringExtra(WorkspaceShortcuts.EXTRA_ID);
        route=new EntryRoute(saved==null?!getIntent().getBooleanExtra(EXTRA_SHOW_HOME,false):saved.getBoolean("auto_entry"));
        try{Activation.writeScript(this);}catch(Exception e){Toast.makeText(this,"激活脚本未生成，请重开 App 重试",Toast.LENGTH_LONG).show();}
        getWindow().setStatusBarColor(HomeScreen.PAPER);getWindow().setNavigationBarColor(HomeScreen.PAPER);
        home=new HomeScreen(this,new HomeScreen.Actions(){
            public void resume(){enterWorkspace();}
            public void recent(){enterRecent();}
            public void open(String id){openSet(id);}
            public void create(){createSet();}
            public void manage(){manageSets();}
            public void activate(){wireless();}
            public void help(){helpPanel();}
            public void retry(){reloadLibrary();}
        });
        setContentView(home);
        renderEntry();
        if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        controller=new WorkbenchController(this,new WorkbenchController.Listener(){
            @Override public void onStatus(String message,boolean ready){authorized=ready;updateEnter();}
            @Override public void onResult(String title,String detail){new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(detail).setPositiveButton("完成",null).show();}
        });
    }
    private void enterWorkspace(){
        if(!active||!authorized||!workspaceLoaded||launching)return;
        route.stayHome();
        if(library.current.cards.isEmpty())enterRecent();
        else{launching=true;updateEnter();startStage();}
    }
    private void renderEntry(){
        boolean routing=(route.pending()||routingStage)&&BridgeState.service()!=null;
        home.setVisibility(routing?android.view.View.INVISIBLE:android.view.View.VISIBLE);
        if(routing){getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);getWindow().setBackgroundDrawableResource(android.R.color.transparent);}
        else{getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(HomeScreen.PAPER));}
    }
    private void tryShortcut(){
        if(!authorized||!workspaceLoaded||!active||shortcutLoaded||shortcutId==null||launching)return;
        shortcutLoaded=true;openSet(shortcutId);
    }
    private void enterRecent(){
        if(!active||launching||!authorized||!workspaceLoaded)return;
        int request=++entryGeneration;launching=true;recentLoading=true;updateEnter();
        recentEntry.load(components->{
            if(!active||isFinishing()||isDestroyed()||request!=entryGeneration)return;
            WorkspacePersistence.get(this).enterRecent(components,definition->{
                if(active&&!isFinishing()&&!isDestroyed()&&request==entryGeneration)startStage();
            },message->{if(request==entryGeneration)recentFailed(message);});
        },message->{if(request==entryGeneration)recentFailed(message);});
    }
    private void recentFailed(String message){
        launching=false;recentLoading=false;routingStage=false;route.stayHome();
        if(active){Toast.makeText(this,message,Toast.LENGTH_LONG).show();reloadLibrary();updateEnter();}
    }
    private void openSet(String id){
        if(!active||launching||!workspaceLoaded||!authorized)return;
        launching=true;updateEnter();StageActivity.closeForLaunch();
        WorkspacePersistence.get(this).activate(id,definition->{
            if(!active||isFinishing()||isDestroyed()){launching=false;return;}startStage();
        },message->{launching=false;shortcutId=null;if(active){Toast.makeText(this,message,Toast.LENGTH_LONG).show();reloadLibrary();}});
    }
    private void startStage(){
        if(!active||isFinishing()||isDestroyed()){launching=false;return;}
        route.stayHome();routingStage=true;renderEntry();
        if(activeDialog!=null&&activeDialog.isShowing())activeDialog.dismiss();
        startActivity(new Intent(this,StageActivity.class));
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);route.launch(intent.getBooleanExtra(EXTRA_SHOW_HOME,false));
        shortcutId=intent.getStringExtra(WorkspaceShortcuts.EXTRA_ID);shortcutLoaded=false;launching=false;routingStage=false;
        workspaceLoaded=false;StageActivity.closeForLaunch();renderEntry();reloadLibrary();if(controller!=null)controller.refresh();
    }
    @Override protected void onStart(){
        super.onStart();active=true;launching=false;recentLoading=false;routingStage=false;StageActivity.closeForLaunch();workspaceLoaded=false;updateEnter();if(controller!=null)controller.refresh();reloadLibrary();
    }
    private void reloadLibrary(){
        int generation=++loadGeneration;
        WorkspacePersistence.get(this).library(loaded->{
            if(!active||isFinishing()||isDestroyed()||generation!=loadGeneration)return;
            library=loaded;home.library(loaded);workspaceLoaded=true;updateEnter();
        },message->{if(active&&!isFinishing()&&!isDestroyed()&&generation==loadGeneration){route.stayHome();home.error(message);workspaceLoaded=false;updateEnter();}});
    }
    private void createSet(){if(library!=null)new ApplicationSetEditor(this,library.current.style,this::showDialog,this::reloadLibrary).display();}
    private void manageSets(){if(library!=null)new WorkspaceManager(this,library.current,this::showDialog,definition->{
        if(!active)return;if(authorized){launching=true;updateEnter();startStage();}else{reloadLibrary();Toast.makeText(this,"已选择应用组合，请先完成无线或 USB 激活",Toast.LENGTH_LONG).show();}
    }).display();}
    private void showDialog(Dialog dialog){
        activeDialog=dialog;dialog.setOnDismissListener(d->{if(activeDialog==dialog){activeDialog=null;if(active&&!launching)reloadLibrary();}});dialog.show();
    }
    private void updateEnter(){
        renderEntry();
        home.state(authorized,workspaceLoaded,launching,recentLoading);
        if(authorized&&workspaceLoaded&&!launching)tryShortcut();
        if(route.take(active,authorized,workspaceLoaded,launching,shortcutId!=null)){routingStage=true;enterWorkspace();}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("auto_entry",route.pending());}
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);home.requestLayout();
        if(activeDialog instanceof AppPanel)((AppPanel)activeDialog).configurationChanged();
        if(activeDialog instanceof WorkbenchDialog)((WorkbenchDialog)activeDialog).fit();
    }
    private void wireless(){startActivity(new Intent(this,cn.paperfly.workbench.activation.WirelessActivationActivity.class));}
    private void helpPanel(){
        WorkbenchDialog dialog=new WorkbenchDialog(this,"帮助与设置","开放工作台 · "+BuildConfig.VERSION_NAME,400);
        dialog.row(WorkbenchIcon.Kind.NEXT,"软件更新","从 NAS 检查并安装新版本",true,false,()->{dialog.dismiss();startActivity(new Intent(this,SoftwareUpdateActivity.class));});
        dialog.row(WorkbenchIcon.Kind.CHECK,"无线激活",authorized?"已激活，可直接使用":"在这台平板上完成，无需电脑",true,false,this::wireless);
        dialog.row(WorkbenchIcon.Kind.SHORTCUT,"USB 激活说明","使用电脑连接平板激活",true,false,this::activation);
        dialog.divider();dialog.row(WorkbenchIcon.Kind.MEMORY,"诊断记录","查看最近的窗口运行情况",true,false,this::report);
        showDialog(dialog);
    }
    @Override protected void onStop(){active=false;entryGeneration++;recentEntry.cancel();super.onStop();}
    private void activation(){new AlertDialog.Builder(this).setTitle("USB 激活一次")
        .setMessage("1. 平板开启 USB 调试，连接电脑并允许调试。\n2. 在电脑运行随安装包提供的激活脚本，或运行：\n\n"+Activation.COMMAND+"\n\n显示已激活后即可拔线使用。设备重启、更新 APK 或服务被系统终止后，需要再次激活。")
        .setPositiveButton("检查激活",(d,w)->controller.refresh()).setNeutralButton("复制命令",(d,w)->getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("USB 激活",Activation.COMMAND))).show();}
    private void report(){String details=getSharedPreferences("workspace",MODE_PRIVATE).getString("last_stage_report","尚无窗口记录。");new AlertDialog.Builder(this).setTitle("最近的窗口记录").setMessage(details)
        .setPositiveButton("完成",null).setNeutralButton("设备诊断",(d,w)->controller.diagnose()).show();}
    @Override protected void onDestroy(){active=false;loadGeneration++;recentEntry.close();if(activeDialog!=null)activeDialog.dismiss();if(controller!=null)controller.close();if(home!=null)home.close();super.onDestroy();}
}
