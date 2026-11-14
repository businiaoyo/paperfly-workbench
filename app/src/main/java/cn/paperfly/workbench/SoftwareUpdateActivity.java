package cn.paperfly.workbench;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.paperfly.workbench.update.UpdateClient;
import cn.paperfly.workbench.update.UpdateRelease;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground update flow, separate from activation, workspace storage and shell permissions. */
public final class SoftwareUpdateActivity extends Activity {
    private static final int INK=0xff203d35,MUTED=0xff65736e;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UpdateClient client;
    private UpdateRelease release;
    private File downloaded;
    private LinearLayout box;
    private TextView title,detail,notes;
    private ProgressBar progress;
    private Button action;
    private boolean busy,closed,resumed,awaitingPermission;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);client=new UpdateClient(this);
        getWindow().setStatusBarColor(HomeScreen.PAPER);getWindow().setNavigationBarColor(HomeScreen.PAPER);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(HomeScreen.PAPER);
        LinearLayout outer=WorkbenchUi.column(this);outer.setGravity(Gravity.CENTER);outer.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(outer);
        box=WorkbenchUi.column(this);box.setPadding(dp(28),dp(24),dp(28),dp(28));box.setBackground(WorkbenchUi.shape(this,0xffffffff,24));outer.addView(box,new LinearLayout.LayoutParams(-1,-2));
        Button back=button("返回主页",false);back.setOnClickListener(v->finish());box.addView(back,new LinearLayout.LayoutParams(-2,dp(48)));space(24);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_workbench);box.addView(icon,new LinearLayout.LayoutParams(dp(72),dp(72)));space(20);
        box.addView(WorkbenchUi.text(this,"软件更新",28,INK));space(10);
        box.addView(WorkbenchUi.text(this,"开放工作台 · 当前版本 "+BuildConfig.VERSION_NAME,14,MUTED));space(28);
        title=WorkbenchUi.text(this,"正在检查更新…",20,INK);box.addView(title);space(10);
        detail=WorkbenchUi.text(this,"连接 NAS 更新中心",14,MUTED);detail.setLineSpacing(dp(3),1);box.addView(detail);space(16);
        notes=WorkbenchUi.text(this,"",15,INK);notes.setLineSpacing(dp(4),1);box.addView(notes);space(18);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));space(20);
        action=button("检查更新",true);box.addView(action,new LinearLayout.LayoutParams(-1,dp(52)));space(16);
        box.addView(WorkbenchUi.text(this,"连接到 NAS 所在网络即可更新。更新后按提示重新激活，应用组合和工作台设置会保留。",12,MUTED));
        setContentView(scroll);fit();check();
    }
    private void fit(){android.view.ViewGroup.LayoutParams lp=box.getLayoutParams();lp.width=Math.max(dp(240),Math.min(dp(660),getWindowManager().getCurrentWindowMetrics().getBounds().width()-dp(40)));box.setLayoutParams(lp);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);fit();}
    private Button button(String text,boolean primary){Button b=WorkbenchUi.button(this,text,primary);b.setTextColor(primary?0xffffffff:INK);b.setBackground(WorkbenchUi.touch(this,primary?0xff187969:0xffedf2ee,14));return b;}
    private int dp(float value){return WorkbenchUi.dp(this,value);}
    private void space(int size){WorkbenchUi.space(box,size);}
    private void idle(String label,Runnable next){busy=false;action.setEnabled(true);action.setText(label);action.setOnClickListener(v->next.run());progress.setVisibility(View.GONE);}
    private void working(String message){busy=true;title.setText(message);action.setEnabled(false);progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);}
    private void check(){
        if(busy||closed)return;release=null;downloaded=null;notes.setText("");working("正在检查更新…");detail.setText("连接 NAS 更新中心");
        worker.execute(()->{
            try{UpdateRelease found=client.check();main.post(()->{
                if(closed)return;release=found;
                if(found==null){title.setText("已是最新版本");detail.setText("当前版本 "+BuildConfig.VERSION_NAME);idle("重新检查",this::check);}
                else{title.setText("发现新版本 "+found.version);detail.setText(String.format(java.util.Locale.CHINA,"安装包 %.1f MB",found.size/1048576d));notes.setText(found.notes);idle("下载并安装",this::download);}
            });}catch(Exception error){error("暂时无法检查更新",error,this::check);}
        });
    }
    private void download(){
        if(busy||release==null||closed)return;UpdateRelease selected=release;working("正在下载 "+selected.version);progress.setIndeterminate(false);progress.setProgress(0);
        worker.execute(()->{
            try{File file=client.download(selected,bytes->main.post(()->{if(!closed){int percent=(int)(bytes*100/selected.size);progress.setProgress(percent);detail.setText("已下载 "+percent+"%");}}));
                main.post(()->{if(closed)return;downloaded=file;title.setText("更新包已就绪");detail.setText("已完成完整性与应用签名核对");idle("安装更新",this::install);if(resumed)install();});
            }catch(Exception error){error("下载未完成",error,this::download);}
        });
    }
    private void install(){
        if(busy||downloaded==null||release==null||closed)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            title.setText("允许安装工作台更新");detail.setText("请在下一页允许“开放工作台”安装应用，返回后继续安装。");
            idle("前往允许安装",()->{
                awaitingPermission=true;
                try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),41);}
                catch(RuntimeException error){awaitingPermission=false;title.setText("无法打开安装设置");detail.setText("可在系统设置中允许开放工作台安装应用后重试。");idle("重试安装",this::install);}
            });return;
        }
        working("正在准备安装…");
        worker.execute(()->{
            try{client.verify(downloaded,release);main.post(()->{
                if(closed)return;idle("再次安装",this::install);
                if(!resumed){title.setText("更新包已就绪");return;}
                try{
                    Uri uri=Uri.parse("content://"+getPackageName()+".updates/verified.apk");
                    Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
                    startActivityForResult(intent,42);title.setText("请在系统页面完成安装");detail.setText("安装完成后重新打开开放工作台。");
                }catch(RuntimeException error){title.setText("无法打开系统安装程序");detail.setText("请稍后重试安装。");}
            });}catch(Exception error){downloaded=null;error("更新包不可安装",error,this::download);}
        });
    }
    private void error(String heading,Exception error,Runnable retry){
        main.post(()->{if(closed)return;title.setText(heading);
            String message=error instanceof java.net.ConnectException||error instanceof java.net.SocketTimeoutException||error instanceof java.net.UnknownHostException?"无法连接 NAS，请确认平板与 NAS 在同一网络后重试。":error.getMessage();
            detail.setText(message==null?"请稍后重试。":message);idle("重试",retry);
        });
    }
    @Override protected void onResume(){super.onResume();resumed=true;
        if(awaitingPermission){awaitingPermission=false;if(getPackageManager().canRequestPackageInstalls())install();}
    }
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==42&&result!=RESULT_OK){title.setText("安装尚未完成");detail.setText("更新包已保留，可以再次安装。");}
    }
    @Override protected void onDestroy(){closed=true;client.close();worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
