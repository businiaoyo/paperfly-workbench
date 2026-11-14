package cn.paperfly.workbench.activation;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** Read-only settings adapter. OEM keys may be unavailable; unknown is distinct from disabled. */
final class AdbDebugState {
    final int usb,wireless,charging;
    private AdbDebugState(int usb,int wireless,int charging){this.usb=usb;this.wireless=wireless;this.charging=charging;}
    static AdbDebugState read(Context context){return new AdbDebugState(read(context,Settings.Global.ADB_ENABLED),
            read(context,"adb_wifi_enabled"),read(context,"allow_charging_adb"));}
    private static int read(Context context,String key){try{return Settings.Global.getInt(context.getContentResolver(),key,-1);}catch(RuntimeException inaccessible){return -1;}}
    String diagnostic(){return "usb="+usb+" wireless="+wireless+" charging="+charging;}
    String connectionHint(){return AdbPrerequisites.connectionHint(Build.MANUFACTURER,usb,wireless,charging);}
}
