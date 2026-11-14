package cn.paperfly.workbench.activation;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import cn.paperfly.workbench.ActivationHost;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.PairingConnectionCtx;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** User-initiated local ADB bootstrap. Window operations continue exclusively through the Binder. */
public final class WirelessActivation {
    public interface Listener{void changed(Snapshot snapshot);}
    public static final class Snapshot {
        public final String message;public final boolean busy,ready;public final int pairingPort,connectPort;
        Snapshot(String message,boolean busy,int pairing,int connect){this.message=message;this.busy=busy;pairingPort=pairing;connectPort=connect;ready=ActivationHost.ready();}
    }
    private static WirelessActivation instance;
    public static synchronized WirelessActivation get(Context context){if(instance==null)instance=new WirelessActivation(context.getApplicationContext());return instance;}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private final Set<Object> discoveryOwners=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Object ports=new Object();
    private final LocalAdbDiscovery discovery;
    private final ActivationDiagnostics diagnostics;
    private volatile int pairingPort,connectPort,generation;
    private volatile AdbEndpointCatalog.Endpoint[] connectCandidates=new AdbEndpointCatalog.Endpoint[0];
    private volatile Closeable current;
    private boolean busy;
    private volatile String phase="idle";
    private String message="开启系统无线调试后，可在本机配对并激活";
    private WirelessActivation(Context context){
        this.context=context;diagnostics=new ActivationDiagnostics(context);discovery=new LocalAdbDiscovery(context,(pairing,connect)->{
            pairingPort=pairing;connectCandidates=connect;connectPort=connect.length==0?0:connect[0].port;
            synchronized(ports){ports.notifyAll();}publish();},diagnostics);
        ActivationHost.observe(()->{if(!busy)message=ActivationHost.ready()?"已激活 · 工作台通过本机服务运行":"尚未激活或服务已停止，请开启无线调试后重新激活";publish();});
    }
    public void observe(Listener listener){listeners.add(listener);listener.changed(snapshot());}
    public void unobserve(Listener listener){listeners.remove(listener);}
    public void discover(Object owner,boolean enabled){
        if(enabled)discoveryOwners.add(owner);else discoveryOwners.remove(owner);
        diagnostics.record("owner "+owner.getClass().getSimpleName()+" enabled="+enabled+" count="+discoveryOwners.size());
        if(discoveryOwners.isEmpty())discovery.stop();else discovery.start();
    }
    public Snapshot snapshot(){return new Snapshot(message,busy,pairingPort,connectPort);}
    public void prepare(){if(!busy){message="等待配对码：打开系统无线调试，点“使用配对码配对”，然后在通知中输入";publish();}}
    public void pair(String input,int explicitPort){
        PairingInput parsed;try{parsed=PairingInput.parse(input,explicitPort);}catch(IllegalArgumentException error){message=error.getMessage();publish();return;}
        if(!begin("正在与本机安全配对…"))return;int token=generation;
        worker.execute(()->{
            try{
                phase="pairing";int port=parsed.port!=0?parsed.port:awaitPort(true,token);AdbIdentity identity=AdbIdentity.load(context);check(token);
                byte[] password=parsed.code.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                String peer;
                try(PairingConnectionCtx pairing=new PairingConnectionCtx("127.0.0.1",port,password,identity.key,identity.certificate,"OpenWorkbench")){
                    current=pairing;check(token);pairing.start();peer=pairing.getPairedDeviceGuid();check(token);
                }finally{Arrays.fill(password,(byte)0);current=null;}
                context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit().putBoolean("paired",true).apply();
                diagnostics.record("paired peer="+ActivationDiagnostics.fingerprint(peer)+" port="+port);
                update(token,"配对成功，正在启动本机工作台服务…");start(identity,0,token,peer);
            }catch(Exception error){fail(token,error,true);}
        });
    }
    public void activate(int explicitPort){
        if(explicitPort<0||explicitPort>65535){message="连接端口无效";publish();return;}
        if(ActivationHost.ready()){message="已经激活，可以直接进入工作台";publish();return;}
        if(!begin("正在连接本机无线调试…"))return;int token=generation;
        worker.execute(()->{try{start(AdbIdentity.load(context),explicitPort,token,null);}catch(Exception error){fail(token,error,false);}});
    }
    private void start(AdbIdentity identity,int explicitPort,int token,String expectedPeer)throws Exception{
        phase="connect";
        try(AdbConnection connection=connect(identity,explicitPort,token,expectedPeer)){
            phase="launch";try(AdbStream stream=connection.open("shell,v2,raw:"+ActivationHost.startupCommand(context))){
                String output=ShellV2Result.read(stream.openInputStream());check(token);
                context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit().putString("startup_output",output).apply();
            }
            phase="binder";long deadline=SystemClock.elapsedRealtime()+12000;
            while(!ActivationHost.ready()&&SystemClock.elapsedRealtime()<deadline){check(token);Thread.sleep(150);}
            if(!ActivationHost.ready())throw new IOException("本机服务未连接");
        }finally{current=null;}
        // Success requires the authenticated Binder to survive closing the ADB transport.
        Thread.sleep(600);check(token);if(!ActivationHost.ready())throw new IOException("本机服务随调试连接断开，请重新激活");
        context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit().putString("method","wireless")
                .putLong("activated_at",System.currentTimeMillis()).putBoolean("adb_connection_closed",true)
                .remove("last_error_type").remove("last_error_phase").remove("last_error_detail").apply();
        main.post(()->{if(token==generation){busy=false;message="无线激活成功 · 调试连接已断开，可以进入工作台";publish();}});
    }
    private AdbConnection connect(AdbIdentity identity,int explicitPort,int token,String expectedPeer)throws Exception{
        Set<Integer> attempted=new LinkedHashSet<>();Exception failure=null;
        diagnostics.record("connect begin expected="+ActivationDiagnostics.fingerprint(expectedPeer));
        context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit()
                .putString("connection_mode",explicitPort==0?"automatic":"manual").putBoolean("paired_peer_filter",expectedPeer!=null)
                .putInt("connection_attempts",0).putString("attempted_ports","").remove("connected_port")
                .remove("startup_output").putBoolean("adb_connection_closed",false).apply();
        long deadline=SystemClock.elapsedRealtime()+20000;
        String observed=null;
        String environment=null;long nextEnvironmentCheck=0;
        while(attempted.size()<4){
            check(token);int port=explicitPort;
            if(port==0){
                synchronized(ports){
                    while(port==0){
                        long now=SystemClock.elapsedRealtime();
                        if(now>=nextEnvironmentCheck){String state=AdbDebugState.read(context).diagnostic();
                            if(!state.equals(environment)){environment=state;diagnostics.record("debug state "+state);}nextEnvironmentCheck=now+1000;}
                        String candidates=ActivationDiagnostics.candidates(connectCandidates);
                        if(!candidates.equals(observed)){observed=candidates;diagnostics.record("connect candidates="+candidates);}
                        check(token);port=AdbEndpointCatalog.nextPort(connectCandidates,expectedPeer,attempted);
                        long remaining=deadline-SystemClock.elapsedRealtime();
                        if(port!=0||remaining<=0)break;
                        ports.wait(Math.min(remaining,250));
                    }
                }
            }
            if(port==0||SystemClock.elapsedRealtime()>=deadline)break;
            attempted.add(port);AdbConnection connection=null;boolean connected=false;
            diagnostics.record("connect attempt port="+port);
            context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit()
                    .putString("attempted_ports",attempted.toString()).putInt("connection_attempts",attempted.size()).apply();
            try{
                connection=new AdbConnection.Builder("127.0.0.1",port).setApi(Build.VERSION.SDK_INT)
                        .setPrivateKey(identity.key).setCertificate(identity.certificate).setDeviceName("OpenWorkbench").build();
                current=connection;check(token);
                long timeout=Math.min(12000,Math.max(1,deadline-SystemClock.elapsedRealtime()));
                if(!connection.connect(timeout,TimeUnit.MILLISECONDS,true))throw new IOException("连接超时");
                check(token);connected=true;
                context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit()
                        .putInt("connected_port",port).putInt("connection_attempts",attempted.size()).apply();
                return connection;
            }catch(InterruptedException error){throw error;}
            catch(Exception error){failure=error;if(explicitPort!=0)throw error;
                update(token,"正在更新本机调试端口并重试…");
            }finally{if(!connected){if(connection!=null)try{connection.close();}catch(Exception ignored){}current=null;}}
        }
        if(failure!=null)throw failure;
        diagnostics.record("connect exhausted candidates="+ActivationDiagnostics.candidates(connectCandidates));
        throw new IOException("未发现连接端口");
    }
    private int awaitPort(boolean pairing,int token)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+10000;
        synchronized(ports){for(;;){check(token);int port=pairing?pairingPort:connectPort;if(port!=0)return port;
            long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)throw new IOException(pairing?"未发现配对端口":"未发现连接端口");ports.wait(Math.min(remaining,250));}}
    }
    private boolean begin(String status){if(busy)return false;busy=true;generation++;diagnostics.record("begin operation="+generation);
        diagnostics.record("debug state "+AdbDebugState.read(context).diagnostic());
        context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit().putLong("operation_started_at",System.currentTimeMillis())
                .remove("last_error_type").remove("last_error_phase").remove("last_error_detail").remove("last_error_at").apply();
        message=status;publish();return true;}
    public void cancel(){generation++;Closeable c=current;if(c!=null)new Thread(()->{try{c.close();}catch(Exception ignored){}},"wireless-cancel").start();busy=false;message="已取消无线激活";synchronized(ports){ports.notifyAll();}publish();}
    private void check(int token)throws InterruptedException{if(token!=generation)throw new InterruptedException("Cancelled");}
    private void update(int token,String value){main.post(()->{if(token==generation){message=value;publish();}});}
    private void fail(int token,Exception error,boolean pairing){main.post(()->{if(token!=generation)return;busy=false;
        diagnostics.record("failed phase="+phase+" type="+error.getClass().getSimpleName());
        diagnostics.freezeFailure();
        message=pairing?"配对或启动未完成。请保持系统配对弹窗打开，检查配对码；已显示配对成功时可直接点“激活工作台”。":"激活未完成。请开启无线调试；首次使用先配对，也可手动填写连接端口。";
        if(pairing&&!"pairing".equals(phase))message="配对已完成，但连接或启动服务未完成。无需重复输入配对码，可点“已配对，直接激活”重试。";
        if(error instanceof io.github.muntashirakon.adb.AdbPairingRequiredException)
            message="本机调试连接未接受当前配对。请核对系统显示的连接端口；端口正确时，再点“开始配对”重新授权。";
        if(error instanceof IOException&&error.getMessage()!=null&&error.getMessage().startsWith("未发现"))message=error.getMessage()+"。可在本页填写系统显示的端口重试。";
        if("connect".equals(phase)&&error instanceof IOException&&"未发现连接端口".equals(error.getMessage()))message=AdbDebugState.read(context).connectionHint();
        StringBuilder chain=new StringBuilder();for(Throwable e=error;e!=null&&chain.length()<1500;e=e.getCause())chain.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE).edit().putString("last_error_type",error.getClass().getSimpleName())
                .putString("last_error_phase",phase).putString("last_error_detail",chain.toString()).putLong("last_error_at",System.currentTimeMillis()).apply();publish();});}
    private void publish(){Snapshot snapshot=snapshot();for(Listener listener:new ArrayList<>(listeners))listener.changed(snapshot);}
}
