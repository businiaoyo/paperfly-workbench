package cn.paperfly.workbench;

import android.content.Context;

/** Narrow boundary used by the local wireless client; no arbitrary shell interface is exposed. */
public final class ActivationHost {
    private ActivationHost(){}
    public static boolean ready(){return BridgeState.service()!=null;}
    public static void observe(Runnable observer){BridgeState.add(observer);}
    public static void unobserve(Runnable observer){BridgeState.remove(observer);}
    public static String startupCommand(Context context){return Activation.scriptBody();}
}
