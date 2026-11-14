package cn.paperfly.workbench.activation;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

/** Discover only this device's ADB endpoints; all actual sockets use loopback. */
final class LocalAdbDiscovery {
    interface Listener{void changed(int pairingPort,AdbEndpointCatalog.Endpoint[] connectPorts);}
    private static final class Tracked {
        final NsdServiceInfo info;final String key;final long token;
        Runnable unregister;
        Tracked(NsdServiceInfo info,String key,long token){this.info=info;this.key=key;this.token=token;}
    }
    private final NsdManager manager;
    private final Listener listener;
    private final android.content.SharedPreferences diagnostics;
    private final ActivationDiagnostics trace;
    private final Map<String,String> observations=new TreeMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> discoveries=new ArrayList<>();
    private final Map<String,Tracked> tracked=new HashMap<>();
    private final ArrayDeque<Tracked> queue=new ArrayDeque<>();
    private final AdbEndpointCatalog catalog=new AdbEndpointCatalog();
    private boolean active,resolving;
    private int generation;
    private final Runnable refresh=new Runnable(){public void run(){
        if(!active)return;
        for(Tracked item:tracked.values())if(item.unregister==null)enqueue(item);
        main.postDelayed(this,2000);
    }};
    LocalAdbDiscovery(Context context,Listener listener,ActivationDiagnostics trace){manager=context.getSystemService(NsdManager.class);this.listener=listener;this.trace=trace;diagnostics=context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE);}
    void start(){if(active)return;active=true;generation++;trace.record("discovery start "+generation);observations.clear();discover("_adb-tls-pairing._tcp.");discover("_adb-tls-connect._tcp.");main.postDelayed(refresh,2000);}
    void stop(){
        if(!active)return;active=false;generation++;trace.record("discovery stop "+generation);main.removeCallbacks(refresh);
        for(NsdManager.DiscoveryListener discovery:discoveries)try{manager.stopServiceDiscovery(discovery);}catch(RuntimeException ignored){}
        for(Tracked item:tracked.values())unregister(item);
        discoveries.clear();tracked.clear();queue.clear();catalog.clear();resolving=false;publish();
    }
    private void discover(String type){
        int token=generation;
        NsdManager.DiscoveryListener discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int code){trace.record("discovery failed "+t+" code="+code);}
            public void onStopDiscoveryFailed(String t,int code){}
            public void onServiceFound(NsdServiceInfo info){main.post(()->{if(active&&token==generation)found(info);});}
            public void onServiceLost(NsdServiceInfo info){main.post(()->{
                if(!active||token!=generation)return;
                String key=AdbEndpointCatalog.key(info.getServiceType(),info.getServiceName());
                Tracked item=tracked.remove(key);if(item!=null){unregister(item);queue.remove(item);}
                catalog.lost(key);publish();
                trace.record("lost "+type+" "+ActivationDiagnostics.fingerprint(info.getServiceName()));
            });}
        };
        discoveries.add(discovery);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,discovery);}catch(RuntimeException ignored){}
    }
    private void found(NsdServiceInfo info){
        String key=AdbEndpointCatalog.key(info.getServiceType(),info.getServiceName());
        if(tracked.containsKey(key))return;
        trace.record("found "+info.getServiceType()+" "+ActivationDiagnostics.fingerprint(info.getServiceName()));
        Tracked item=new Tracked(info,key,catalog.found(key));tracked.put(key,item);
        if(Build.VERSION.SDK_INT>=34)monitor(item);else enqueue(item);
    }
    @android.annotation.TargetApi(34)
    private void monitor(Tracked item){
        NsdManager.ServiceInfoCallback callback=new NsdManager.ServiceInfoCallback(){
            public void onServiceInfoCallbackRegistrationFailed(int code){trace.record("monitor failed code="+code);if(valid(item)){item.unregister=null;enqueue(item);}}
            public void onServiceUpdated(NsdServiceInfo info){if(valid(item))updated(item,info);}
            public void onServiceLost(){if(valid(item)){trace.record("monitor lost "+ActivationDiagnostics.fingerprint(item.info.getServiceName()));catalog.update(item.key,item.token,0,false);publish();}}
            public void onServiceInfoCallbackUnregistered(){}
        };
        item.unregister=()->manager.unregisterServiceInfoCallback(callback);
        try{manager.registerServiceInfoCallback(item.info,command->main.post(command),callback);}
        catch(RuntimeException error){item.unregister=null;enqueue(item);}
    }
    private void unregister(Tracked item){if(item.unregister!=null)try{item.unregister.run();}catch(RuntimeException ignored){}}
    private boolean valid(Tracked item){return active&&tracked.get(item.key)==item;}
    private void enqueue(Tracked item){if(!queue.contains(item))queue.add(item);resolve();}
    private void resolve(){
        if(!active||resolving||queue.isEmpty())return;
        Tracked item=queue.remove();if(!valid(item)){resolve();return;}
        resolving=true;int token=generation;
        try{manager.resolveService(item.info,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo s,int code){main.post(()->complete(item,null,token));}
            public void onServiceResolved(NsdServiceInfo s){main.post(()->complete(item,s,token));}
        });}catch(RuntimeException error){complete(item,null,token);}
    }
    private void complete(Tracked item,NsdServiceInfo info,int token){
        if(token!=generation)return;resolving=false;
        if(valid(item)&&info!=null)updated(item,info);
        resolve();
    }
    private void updated(Tracked item,NsdServiceInfo info){
        List<InetAddress> addresses=Build.VERSION.SDK_INT>=34?info.getHostAddresses():Collections.singletonList(info.getHost());
        boolean localAddress=false;for(InetAddress address:addresses)if(isLocal(address)){localAddress=true;break;}
        String model=attribute(info,"name"),api=attribute(info,"api");
        boolean accepted=AdbEndpointIdentity.local(localAddress,Build.MODEL,Build.VERSION.SDK_INT,model,api);
        catalog.update(item.key,item.token,info.getPort(),accepted);
        // A few private, bounded diagnostic fields explain rejected candidates; never pairing codes or keys.
        String observation="port="+info.getPort()+" model="+model+" api="+api+" local_address="+localAddress+" accepted="+accepted;
        if(!observation.equals(observations.get(item.key))){
            trace.record("update "+info.getServiceType()+" "+ActivationDiagnostics.fingerprint(info.getServiceName())+" port="+info.getPort()+" accepted="+accepted);
            if(observations.size()<12||observations.containsKey(item.key))observations.put(item.key,observation);
            diagnostics.edit().putString("discovery_endpoints",observations.toString()).apply();
        }
        publish();
    }
    private static String attribute(NsdServiceInfo info,String key){byte[] value=info.getAttributes().get(key);
        return value==null?null:new String(value,0,Math.min(value.length,256),java.nio.charset.StandardCharsets.UTF_8).replace('\n',' ').replace('\r',' ');}
    private boolean isLocal(InetAddress address){
        if(address==null)return false;if(address.isLoopbackAddress())return true;
        try{return NetworkInterface.getByInetAddress(address)!=null;}catch(Exception ignored){return false;}
    }
    private void publish(){int[] pairing=catalog.ports(true);listener.changed(pairing.length==0?0:pairing[0],catalog.connections());}
}
