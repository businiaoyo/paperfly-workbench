package cn.paperfly.workbench;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.util.*;

/** Tracks only keys explicitly forwarded by this host, so focus changes cannot strand modifiers. */
final class StageKeyboard {
    private final Map<Integer,Map<Integer,KeyEvent>> held=new HashMap<>();
    private final Set<Integer> commands=new HashSet<>();
    boolean commandRelease(KeyEvent event){return event.getAction()==KeyEvent.ACTION_UP&&commands.remove(event.getKeyCode());}
    void commandDown(KeyEvent event){commands.add(event.getKeyCode());}
    boolean forward(IWorkbenchService service,IBinder session,int display,KeyEvent event){
        if(service==null||display<0||!InputPolicy.allowedKey(event.getKeyCode()))return false;
        try{service.liveKeyEvent(display,event,session);
            Map<Integer,KeyEvent> keys=held.computeIfAbsent(display,key->new HashMap<>());
            if(event.getAction()==KeyEvent.ACTION_DOWN)keys.put(event.getKeyCode(),new KeyEvent(event));else keys.remove(event.getKeyCode());return true;
        }catch(Exception ignored){return false;}
    }
    void release(IWorkbenchService service,IBinder session){
        for(Map.Entry<Integer,Map<Integer,KeyEvent>> display:held.entrySet())for(KeyEvent down:display.getValue().values())try{
            if(service!=null)service.liveKeyEvent(display.getKey(),new KeyEvent(down.getDownTime(),SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,down.getKeyCode(),0,0,down.getDeviceId(),down.getScanCode(),down.getFlags(),down.getSource()),session);
        }catch(Exception ignored){}held.clear();
    }
}
