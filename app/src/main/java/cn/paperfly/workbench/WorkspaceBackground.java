package cn.paperfly.workbench;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.Toast;
import cn.paperfly.workbench.model.WorkspaceStyle;
import java.util.concurrent.*;

/** Image decoding stays off the UI/input queue; stale requests cannot replace a newer background. */
final class WorkspaceBackground implements AutoCloseable {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int generation;
    private final WallpaperBackdrop wallpaper=new WallpaperBackdrop();
    void apply(Activity activity,View scene,WorkspaceStyle style){
        wallpaper.close();
        int request=++generation;scene.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,style.backgroundColors()));
        if(style.wallpaperBackground){wallpaper.apply(activity.getWindow(),activity.getWindowManager(),scene);return;}
        activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        if(style.backgroundUri.isEmpty())return;
        decoder.execute(()->{try{
            Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.getContentResolver(),Uri.parse(style.backgroundUri)),(d,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();float factor=Math.min(1f,2048f/Math.max(w,h));
                d.setTargetSize(Math.max(1,Math.round(w*factor)),Math.max(1,Math.round(h*factor)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            main.post(()->{if(request!=generation||activity.isDestroyed()){bitmap.recycle();return;}
                scene.setBackground(new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                    public void draw(Canvas canvas){android.graphics.Rect b=getBounds();float scale=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());
                        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),paint);}
                    public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
                    public int getOpacity(){return PixelFormat.OPAQUE;}
                });});
        }catch(Exception error){main.post(()->{if(request==generation&&!activity.isDestroyed())Toast.makeText(activity,"背景图片无法读取，请重新选择",Toast.LENGTH_LONG).show();});}});
    }
    public void close(){generation++;wallpaper.close();decoder.shutdown();}
}
