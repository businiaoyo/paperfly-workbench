package cn.paperfly.workbench.activation;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.app.RemoteInput;
import cn.paperfly.workbench.MainActivity;
import cn.paperfly.workbench.R;

/** A user-started pairing session keeps discovery alive while the system pairing dialog is open. */
public final class WirelessActivationService extends Service implements WirelessActivation.Listener {
    public static final String PREPARE="prepare",PAIR="pair",CONNECT="connect",CANCEL="cancel";
    private static final String CHANNEL="wireless_activation",REPLY="pairing_code";
    private static final int NOTIFICATION=41;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WirelessActivation activation;
    private boolean listening;
    private final Runnable expire=()->{if(activation!=null)activation.cancel();stopSelf();};
    @Override public void onCreate(){
        super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL,"无线激活",NotificationManager.IMPORTANCE_DEFAULT));
        activation=WirelessActivation.get(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startForeground(NOTIFICATION,notification(activation.snapshot()));
        if(!listening){listening=true;activation.discover(this,true);activation.observe(this);}
        main.removeCallbacks(expire);main.postDelayed(expire,180000);
        String action=intent==null?PREPARE:intent.getAction();
        if(CANCEL.equals(action)){activation.cancel();stopSelf();return START_NOT_STICKY;}
        if(PAIR.equals(action)){
            android.os.Bundle reply=RemoteInput.getResultsFromIntent(intent);
            String code=reply!=null&&reply.getCharSequence(REPLY)!=null?reply.getCharSequence(REPLY).toString():intent.getStringExtra("code");
            activation.pair(code,intent.getIntExtra("port",0));intent.removeExtra("code");
        }else if(CONNECT.equals(action))activation.activate(intent.getIntExtra("port",0));else activation.prepare();
        return START_NOT_STICKY;
    }
    @Override public void changed(WirelessActivation.Snapshot state){
        if(state.ready&&!state.busy){
            // No ADB session or foreground pairing service is required after bootstrap.
            stopForeground(STOP_FOREGROUND_REMOVE);
            PendingIntent open=PendingIntent.getActivity(this,3,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            getSystemService(NotificationManager.class).notify(42,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_workbench)
                    .setContentTitle("工作台已激活").setContentText("点击回到开放工作台，无需保持调试连接")
                    .setContentIntent(open).setAutoCancel(true).build());stopSelf();
        }else getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(state));
    }
    private Notification notification(WirelessActivation.Snapshot state){
        Intent open=new Intent(this,WirelessActivationActivity.class).putExtra(WirelessActivationActivity.RETURN_AFTER_ACTIVATION,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent reply=new Intent(this,WirelessActivationService.class).setAction(PAIR);
        PendingIntent pairing=PendingIntent.getForegroundService(this,1,reply,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        RemoteInput input=new RemoteInput.Builder(REPLY).setLabel(state.pairingPort!=0?"输入 6 位配对码":"输入配对码，或“端口 配对码”").build();
        Notification.Action action=new Notification.Action.Builder(null,"输入配对码",pairing).addRemoteInput(input).build();
        Intent cancel=new Intent(this,WirelessActivationService.class).setAction(CANCEL);
        PendingIntent stopping=PendingIntent.getService(this,2,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_workbench).setContentTitle("开放工作台 · 无线激活")
                .setContentText(state.message).setStyle(new Notification.BigTextStyle().bigText(state.message+"\n请保持系统配对弹窗打开，再下拉通知输入配对码。"))
                .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true)
                .addAction(action).addAction(new Notification.Action.Builder(null,"取消",stopping).build()).build();
    }
    @Override public void onDestroy(){
        main.removeCallbacks(expire);if(listening){activation.unobserve(this);activation.discover(this,false);}super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
