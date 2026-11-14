package cn.paperfly.workbench.activation;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.paperfly.workbench.MainActivity;

/** Phone/tablet-only pairing entry. Settings + notification reply avoids losing the pairing dialog. */
public final class WirelessActivationActivity extends Activity implements WirelessActivation.Listener {
    private WirelessActivation activation;
    private TextView status,discovered;
    private EditText pairingPort,code,connectPort;
    private Button prepare,pair,connect,automaticConnect;
    private boolean launchSettingsAfterPermission,resumed,returnAfterActivation,navigatingHome;
    static final String RETURN_AFTER_ACTIVATION="return_after_activation";
    private static final int INK=0xff1a2e2b,TEAL=0xff196c59;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);returnAfterActivation=(state!=null&&state.getBoolean(RETURN_AFTER_ACTIVATION))||getIntent().getBooleanExtra(RETURN_AFTER_ACTIVATION,false);activation=WirelessActivation.get(this);getWindow().setStatusBarColor(0xfff4f7f5);getWindow().setNavigationBarColor(0xfff4f7f5);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff4f7f5);
        LinearLayout outer=new LinearLayout(this);outer.setGravity(Gravity.CENTER_HORIZONTAL);outer.setOrientation(LinearLayout.VERTICAL);scroll.addView(outer);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(24),dp(24),dp(24));box.setOrientation(LinearLayout.VERTICAL);outer.addView(box,new LinearLayout.LayoutParams(Math.min(getWindowManager().getCurrentWindowMetrics().getBounds().width(),dp(700)),-2));setContentView(scroll);
        add(box,text("无线激活",28),8);add(box,text("直接在本机完成，不需要电脑或其他 App。",15),8);
        status=text("",15);add(box,status,18);discovered=text("",12);add(box,discovered,8);
        add(box,text("首次使用",20),22);
        add(box,text("1. 开启开发者选项中的无线调试。\n2. 点击“使用配对码配对”，保持系统弹窗打开。\n3. 下拉通知，在“开放工作台 · 无线激活”中输入配对码。",14),10);
        if(AdbPrerequisites.needsChargingGuide(android.os.Build.MANUFACTURER))add(box,text(
                "荣耀 / 华为：请同时开启“仅充电模式下允许 ADB 调试”和“USB 调试”，避免拔线后系统关闭调试服务。开启这些选项后，激活时无需连接电脑。",13),10);
        prepare=button("开始配对并打开系统设置",()->{
            if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                launchSettingsAfterPermission=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},92);
            }else prepare();
        });add(box,prepare,14);
        prepare.setTextColor(Color.WHITE);prepare.setBackground(round(TEAL));
        automaticConnect=button("已配对，直接激活",()->service(WirelessActivationService.CONNECT,null,0));add(box,automaticConnect,10);
        add(box,text("已配对后，无线调试每次开启的端口可能不同，App 会自动寻找本机端口。设备重启或服务结束后，可在这里重新激活。",12),10);
        add(box,text("手动配对 / 连接",20),24);
        add(box,text("自动发现不可用时填写端口。手动输入配对码时，请把系统设置和本页分屏显示，保持配对弹窗打开。",13),8);
        pairingPort=field(box,"配对端口（通常自动发现）",false);code=field(box,"6 位配对码",true);
        code.setSaveEnabled(false);code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        pair=button("配对并激活",()->{
            try{int port=PairingInput.port(pairingPort.getText().toString());String input=code.getText().toString();PairingInput.parse(input,port);code.setText("");service(WirelessActivationService.PAIR,input,port);}
            catch(IllegalArgumentException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
        });add(box,pair,10);
        connectPort=field(box,"连接端口（不是配对端口）",false);
        connect=button("使用连接端口激活",()->{try{service(WirelessActivationService.CONNECT,null,PairingInput.port(connectPort.getText().toString()));}
            catch(IllegalArgumentException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}});add(box,connect,10);
        add(box,button("只打开无线调试设置",this::openSettings),14);
        add(box,button("取消当前操作",()->service(WirelessActivationService.CANCEL,null,0)),8);
        add(box,button("返回工作台首页",()->{startActivity(new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_SHOW_HOME,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}),14);
        add(box,text("配对密钥仅保存在本机 App 私有目录。激活成功后会关闭 App 的调试连接。关闭系统无线调试后服务能否保留，请以设备上的实际测试为准。",12),16);
        add(box,button("开源组件",()->{
            try(java.io.InputStream in=getAssets().open("third_party_notices.txt")){
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;
                while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
                TextView notice=text(bytes.toString("UTF-8"),12);notice.setPadding(dp(16),dp(12),dp(16),dp(12));ScrollView view=new ScrollView(this);view.addView(notice);
                new android.app.AlertDialog.Builder(this).setTitle("开源组件许可").setView(view).setPositiveButton("完成",null).show();
            }catch(Exception error){Toast.makeText(this,"无法读取组件许可",Toast.LENGTH_SHORT).show();}
        }),8);
    }
    private void prepare(){service(WirelessActivationService.PREPARE,null,0);openSettings();}
    private void openSettings(){
        Intent direct=new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{startActivity(direct);}catch(Exception error){try{
            startActivity(new Intent().setClassName("com.android.settings","com.android.settings.Settings$WirelessDebuggingActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch(Exception unavailable){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key","toggle_adb_wireless").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}}}
    }
    private void service(String action,String code,int port){
        returnAfterActivation=!WirelessActivationService.CANCEL.equals(action);
        getIntent().putExtra(RETURN_AFTER_ACTIVATION,returnAfterActivation);
        Intent intent=new Intent(this,WirelessActivationService.class).setAction(action).putExtra("port",port);
        if(code!=null)intent.putExtra("code",code);try{startForegroundService(intent);}catch(RuntimeException error){Toast.makeText(this,"无法启动配对服务，请保持 App 在前台后重试",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);if(request==92&&launchSettingsAfterPermission){launchSettingsAfterPermission=false;
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)prepare();else Toast.makeText(this,"通知未开启，可用下方手动分屏配对",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean(RETURN_AFTER_ACTIVATION,returnAfterActivation);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);returnAfterActivation|=intent.getBooleanExtra(RETURN_AFTER_ACTIVATION,false);returnHomeIfReady(activation.snapshot());}
    @Override protected void onResume(){super.onResume();resumed=true;returnHomeIfReady(activation.snapshot());}
    @Override protected void onPause(){resumed=false;super.onPause();}
    private void returnHomeIfReady(WirelessActivation.Snapshot state){
        if(!resumed||!returnAfterActivation||!state.ready||state.busy||navigatingHome)return;
        navigatingHome=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
    }
    @Override protected void onStart(){super.onStart();activation.discover(this,true);activation.observe(this);}
    @Override protected void onStop(){activation.unobserve(this);activation.discover(this,false);super.onStop();}
    @Override public void changed(WirelessActivation.Snapshot state){
        status.setText(state.message);status.setTextColor(state.ready?TEAL:INK);
        discovered.setText("本机端口 · 配对："+(state.pairingPort==0?"等待发现":state.pairingPort)+"  · 连接："+(state.connectPort==0?"等待发现":state.connectPort));
        prepare.setEnabled(!state.busy);pair.setEnabled(!state.busy);connect.setEnabled(!state.busy);automaticConnect.setEnabled(!state.busy);returnHomeIfReady(state);
    }
    private TextView text(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(INK);v.setLineSpacing(dp(3),1);return v;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEAL);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(48));b.setBackground(round(Color.WHITE));b.setOnClickListener(v->action.run());return b;}
    private android.graphics.drawable.GradientDrawable round(int color){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private EditText field(LinearLayout parent,String hint,boolean password){EditText v=new EditText(this);v.setHint(hint);v.setSingleLine();v.setTextColor(INK);v.setInputType(InputType.TYPE_CLASS_NUMBER|(password?InputType.TYPE_NUMBER_VARIATION_PASSWORD:0));add(parent,v,10);return v;}
    private void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);p.addView(v,lp);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
