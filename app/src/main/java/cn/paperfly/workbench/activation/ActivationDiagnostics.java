package cn.paperfly.workbench.activation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Locale;

/** Bounded private lifecycle evidence. Never pass codes, keys, credentials or command input here. */
final class ActivationDiagnostics {
    private final SharedPreferences preferences;
    private final ArrayDeque<String> events=new ArrayDeque<>();
    ActivationDiagnostics(Context context){preferences=context.getSharedPreferences("wireless_activation",Context.MODE_PRIVATE);}
    synchronized void record(String event){
        events.addLast(SystemClock.elapsedRealtime()+" "+event);
        while(events.size()>80)events.removeFirst();
        preferences.edit().putString("activation_trace",String.join("\n",events)).apply();
    }
    synchronized void freezeFailure(){preferences.edit().putString("failure_trace",String.join("\n",events)).apply();}
    static String fingerprint(String service){
        if(service==null)return "none";
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(service.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();for(int i=0;i<6;i++)result.append(String.format(Locale.ROOT,"%02x",hash[i]));return result.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static String candidates(AdbEndpointCatalog.Endpoint[] endpoints){
        StringBuilder out=new StringBuilder();for(AdbEndpointCatalog.Endpoint endpoint:endpoints){
            if(out.length()>400)break;out.append(fingerprint(endpoint.service)).append(':').append(endpoint.port).append(' ');
        }return out.toString();
    }
}
