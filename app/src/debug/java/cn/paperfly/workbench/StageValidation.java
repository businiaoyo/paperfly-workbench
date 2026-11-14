package cn.paperfly.workbench;

import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.graphics.Bitmap;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Offline device QA, armed only by a file in this debuggable app's private directory. No exported entry point. */
final class StageValidation {
    private final StageActivity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final JSONObject result=new JSONObject();
    private final File directory;
    private final long deadline=SystemClock.elapsedRealtime()+28800000;
    private String runId;
    private final Runnable bridgeObserver;
    private Object calculator,clock,original;
    private StageValidation(StageActivity activity){this.activity=activity;directory=new File(activity.getFilesDir(),"offline-validation");directory.mkdirs();
        bridgeObserver=()->{if(!ActivationHost.ready())fail(new IOException("Helper Binder died during offline validation"));};}
    static void attach(StageActivity activity){
        File arm=new File(activity.getFilesDir(),"offline-validation-pending");String id;try(BufferedReader reader=new BufferedReader(new FileReader(arm))){id=reader.readLine();}catch(Exception e){return;}
        if(id==null||!id.matches("[0-9a-f]{32}")||!arm.delete())return;
        StageValidation validation=new StageValidation(activity);validation.runId=id;try{validation.result.put("phase","waiting").put("run_id",id);validation.save();}catch(Exception e){validation.fail(e);return;}
        ActivationHost.observe(validation.bridgeObserver);validation.poll();
    }
    private void poll(){
        try{
            File signal=new File(activity.getExternalFilesDir(null),"offline-transports-off.json");
            if(signal.exists()){
                StringBuilder data=new StringBuilder();try(BufferedReader reader=new BufferedReader(new FileReader(signal))){String line;while((line=reader.readLine())!=null)data.append(line);}
                JSONObject state=new JSONObject(data.toString());
                if(runId.equals(state.optString("run_id"))){
                    for(String key:new String[]{"usb_debugging","wireless_debugging","helper_pid","adbd_pid_after_8s","at","usb_connected","condition"})result.put(key,state.get(key));
                    run();return;
                }
            }
            if(SystemClock.elapsedRealtime()>deadline){fail(new IOException("Timed out waiting for the offline validation signal"));return;}
            main.postDelayed(this::poll,500);
        }catch(Exception e){fail(e);}
    }
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void select(Object window)throws Exception{Method m=StageActivity.class.getDeclaredMethod("select",window.getClass());m.setAccessible(true);m.invoke(activity,window);}
    private void step(long delay,Checked action){main.postDelayed(()->{try{action.run();}catch(Exception e){fail(e);}},delay);}
    interface Checked{void run()throws Exception;}
    private void run()throws Exception{
        result.put("started_at",System.currentTimeMillis()).put("binder_after_debug_off",ActivationHost.ready());
        if(!ActivationHost.ready())throw new IOException("Helper Binder died after debugging was disabled");
        List<?> windows=(List<?>)field(activity,"windows");Object slots=field(activity,"slots");Method primary=slots.getClass().getDeclaredMethod("primary");primary.setAccessible(true);int primaryId=(int)primary.invoke(slots);
        for(Object window:windows){String component=(String)field(window,"component");
            if(component.startsWith("com.hihonor.calculator/"))calculator=window;
            if(component.startsWith("com.hihonor.deskclock/"))clock=window;
            if((int)field(window,"id")==primaryId)original=window;
        }
        if(calculator==null||clock==null||original==null)throw new IOException("Required existing QA windows are not ready");
        result.put("calculator_display",field(calculator,"display")).put("clock_display",field(clock,"display"));select(calculator);
        step(900,()->{picture(calculator,"before");tap(.14f,.52f);step(180,()->{
            tap(.14f,.82f);step(180,()->{tap(.86f,.82f);step(180,()->{tap(.14f,.82f);step(180,()->{
                tap(.86f,.93f);step(500,()->{
                    picture(calculator,"after-input");result.put("binder_after_input",ActivationHost.ready());
                    Method back=StageActivity.class.getDeclaredMethod("sendBack");back.setAccessible(true);back.invoke(activity);
                    step(800,()->{picture(calculator,"after-back");select(clock);step(900,()->{
                        picture(clock,"clock-swapped");select(calculator);step(900,()->{
                            tap(.14f,.52f);step(200,()->{select(original);step(900,()->{
                                result.put("binder_after_back_and_swap",ActivationHost.ready()).put("completed_at",System.currentTimeMillis()).put("phase","complete")
                                    .put("calculator_display_after",field(calculator,"display")).put("clock_display_after",field(clock,"display"));save();
                            });});
                        });
                    });});
                });
            });});});
        });});
    }
    private void tap(float x,float y)throws Exception{
        View window=(View)calculator;float width=(int)field(calculator,"canvasWidth"),height=(int)field(calculator,"canvasHeight");long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x*width,y*height,0);window.dispatchTouchEvent(down);down.recycle();
        MotionEvent up=MotionEvent.obtain(now,now+35,MotionEvent.ACTION_UP,x*width,y*height,0);window.dispatchTouchEvent(up);up.recycle();
    }
    private void picture(Object window,String name)throws Exception{
        TextureView texture=(TextureView)field(window,"texture");Bitmap bitmap=texture.getBitmap();if(bitmap==null)throw new IOException("Missing live surface");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        result.put(name+"_frames",field(window,"frames"));
    }
    private void fail(Exception error){try{result.put("phase","failed").put("error",error.toString()).put("binder_at_failure",ActivationHost.ready());save();}catch(Exception ignored){}}
    private void save()throws Exception{if(result.has("completed_at")||result.has("error")){ActivationHost.unobserve(bridgeObserver);main.removeCallbacksAndMessages(null);}try(FileOutputStream out=new FileOutputStream(new File(directory,"result.json"))){out.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
}
