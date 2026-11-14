package cn.paperfly.workbench;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One-shot, shell-armed offline QA restoration. No Binder method or normal app UI can arm it. */
final class ServerValidation {
    static void attach(Context context){
        File arm=new File("/data/local/tmp/paperfly-workbench-offline-restore.once");
        String runId;
        try(BufferedReader reader=new BufferedReader(new FileReader(arm))){runId=reader.readLine();}catch(Exception error){return;}
        if(runId==null)return;String[] args=runId.split(" ");String nonce=args[0];boolean unplugged=args.length==2&&args[1].equals("unplugged");
        if(!nonce.matches("[0-9a-f]{32}")||!arm.delete())return;
        new Thread(()->{
            File log=new File("/data/local/tmp/paperfly-workbench-offline-restore.log");
            long until=SystemClock.elapsedRealtime()+28800000;
            try{
                while(SystemClock.elapsedRealtime()<until){
                    String usbDump=WorkbenchService.run("/system/bin/dumpsys","usb").output;
                    java.util.regex.Matcher usb=java.util.regex.Pattern.compile("(?m)^\\s*connected=(true|false)\\s*$").matcher(usbDump);
                    if(!usb.find()){Thread.sleep(500);continue;}
                    boolean connected=Boolean.parseBoolean(usb.group(1));
                    String usbSetting=setting("adb_enabled"),wifiSetting=setting("adb_wifi_enabled");
                    if(wifiSetting.equals("0")&&(unplugged?!connected:usbSetting.equals("0"))){
                        append(log,(unplugged?"wifi_off_usb_unplugged ":"both_off ")+System.currentTimeMillis()+" helper_pid="+android.os.Process.myPid());
                        Thread.sleep(8000);String adbd=WorkbenchService.run("/system/bin/pidof","adbd").output.trim();append(log,"adbd_after_8s="+adbd);
                        org.json.JSONObject signal=new org.json.JSONObject().put("run_id",nonce).put("usb_debugging",Integer.parseInt(usbSetting)).put("wireless_debugging",0)
                            .put("usb_connected",connected).put("condition",unplugged?"wireless_off_usb_unplugged":"both_debug_settings_off").put("helper_pid",android.os.Process.myPid()).put("adbd_pid_after_8s",adbd).put("at",System.currentTimeMillis());
                        File external=new File("/sdcard/Android/data/cn.paperfly.workbench/files/offline-transports-off.json");
                        try(FileOutputStream out=new FileOutputStream(external)){out.write(signal.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                        Thread.sleep(42000);
                        if(!unplugged)WorkbenchService.run("/system/bin/settings","put","global","adb_enabled","1");
                        WorkbenchService.run("/system/bin/settings","put","global","adb_wifi_enabled","1");
                        append(log,"restored "+System.currentTimeMillis()+" helper_pid="+android.os.Process.myPid());return;
                    }
                    Thread.sleep(2000);
                }
                append(log,"expired without changing settings");
            }catch(Exception e){try{append(log,"error "+e.toString());}catch(Exception ignored){}}
        },"offline-validation-restore").start();
    }
    private static String setting(String name){return WorkbenchService.run("/system/bin/settings","get","global",name).output.trim();}
    private static void append(File file,String value)throws IOException{
        try(FileOutputStream output=new FileOutputStream(file,true)){output.write((value+"\n").getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
    }
}
