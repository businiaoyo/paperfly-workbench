package cn.paperfly.workbench;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

/** System-composited home wallpaper, without wallpaper file access or a shell theme API. */
final class WallpaperBackdrop implements AutoCloseable {
    private Window window;
    private WindowManager manager;
    private Consumer<Boolean> listener;
    void apply(Window target,WindowManager wm,View scene){
        apply(target,wm,scene,false);
    }
    void apply(Window target,WindowManager wm,View scene,boolean picker){
        close();window=target;manager=wm;
        target.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        target.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        target.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=31){
            target.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            int radius=Math.min(150,Math.round(60*scene.getResources().getDisplayMetrics().density));
            WindowManager.LayoutParams attrs=target.getAttributes();attrs.setBlurBehindRadius(radius);target.setAttributes(attrs);
            listener=enabled->scene.setBackgroundColor(picker?(enabled?0xc4101928:0xed101928):(enabled?0x75202b3b:0xc0202b3b));
            manager.addCrossWindowBlurEnabledListener(listener);
        }else scene.setBackgroundColor(picker?0xed101928:0xb0202b3b);
    }
    @Override public void close(){
        if(Build.VERSION.SDK_INT>=31&&manager!=null&&listener!=null)manager.removeCrossWindowBlurEnabledListener(listener);
        if(window!=null){
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER|WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            if(Build.VERSION.SDK_INT>=31){WindowManager.LayoutParams attrs=window.getAttributes();attrs.setBlurBehindRadius(0);window.setAttributes(attrs);}
        }
        window=null;manager=null;listener=null;
    }
}
