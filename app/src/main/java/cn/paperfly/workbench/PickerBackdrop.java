package cn.paperfly.workbench;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Hide host content while the app drawer is visible, keeping every live TextureView attached. */
final class PickerBackdrop implements AutoCloseable {
    private final Window host;
    private final View content;
    private final Drawable background;
    private final float alpha,windowAlpha;
    private final int wallpaperFlag,format;
    PickerBackdrop(Activity activity){
        host=activity.getWindow();content=activity.findViewById(android.R.id.content);
        background=host.getDecorView().getBackground();alpha=content.getAlpha();
        wallpaperFlag=host.getAttributes().flags&WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;format=host.getAttributes().format;windowAlpha=host.getAttributes().alpha;
        host.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);host.setFormat(PixelFormat.TRANSLUCENT);
        host.getDecorView().setBackgroundColor(Color.TRANSPARENT);content.setAlpha(0f);
        WindowManager.LayoutParams attrs=host.getAttributes();attrs.alpha=0f;host.setAttributes(attrs);
    }
    @Override public void close(){
        content.setAlpha(alpha);host.getDecorView().setBackground(background);host.setFormat(format);
        host.setFlags(wallpaperFlag,WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        WindowManager.LayoutParams attrs=host.getAttributes();attrs.alpha=windowAlpha;host.setAttributes(attrs);
    }
}
