package cn.paperfly.workbench;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/** A modal surface in the existing window; never changes wallpaper or live Surface ownership. */
final class HostModalSurface {
    private final FrameLayout host;
    private final FrameLayout overlay;
    private final View panel;
    private final View previousFocus;
    private final List<View> hidden=new ArrayList<>();
    private final List<Integer> accessibility=new ArrayList<>();
    private boolean showing;

    HostModalSurface(Activity activity,View panel,Runnable dismiss,Runnable fit){
        host=activity.findViewById(android.R.id.content);this.panel=panel;previousFocus=activity.getCurrentFocus();
        overlay=new FrameLayout(activity);overlay.setBackgroundColor(0x33000000);
        overlay.setClickable(true);overlay.setOnClickListener(v->dismiss.run());
        overlay.setTranslationZ(WorkbenchUi.dp(activity,100));
        overlay.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)fit.run();});
        overlay.setOnApplyWindowInsetsListener((v,insets)->{overlay.post(fit);return insets;});
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(showing&&(r-l!=or-ol||b-t!=ob-ot))overlay.post(fit);});
    }
    void show(){
        if(showing)return;showing=true;
        if(panel.getParent()!=null)((ViewGroup)panel.getParent()).removeView(panel);
        for(int i=0;i<host.getChildCount();i++){
            View child=host.getChildAt(i);hidden.add(child);accessibility.add(child.getImportantForAccessibility());
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        panel.setClickable(true);panel.setFocusableInTouchMode(true);
        panel.setElevation(WorkbenchUi.dp(panel.getContext(),24));
        overlay.addView(panel,new FrameLayout.LayoutParams(1,1));
        overlay.setAlpha(0);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));panel.requestFocus();
        overlay.animate().alpha(1).setDuration(140).start();
    }
    boolean isShowing(){return showing;}
    Rect available(Rect screen,Insets safe,int keyboard){
        int[] origin=new int[2];host.getLocationOnScreen(origin);
        int w=host.getWidth()>0?host.getWidth():screen.width(),h=host.getHeight()>0?host.getHeight():screen.height();
        return new Rect(Math.max(0,screen.left+safe.left-origin[0]),Math.max(0,screen.top+safe.top-origin[1]),
                Math.min(w,screen.right-safe.right-origin[0]),Math.min(h,screen.bottom-Math.max(safe.bottom,keyboard)-origin[1]));
    }
    void place(int width,int height,Rect safe,View anchor){
        int gap=WorkbenchUi.dp(panel.getContext(),16);int x=safe.left+(safe.width()-width)/2,y=safe.top+(safe.height()-height)/2;
        if(anchor!=null&&anchor.isAttachedToWindow()){
            int[] a=new int[2],o=new int[2];anchor.getLocationOnScreen(a);host.getLocationOnScreen(o);
            x=Math.max(safe.left+gap,Math.min(a[0]-o[0],safe.right-width-gap));
            y=Math.max(safe.top+gap,Math.min(a[1]+anchor.getHeight()-o[1]+gap/2,safe.bottom-height-gap));
        }
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)panel.getLayoutParams();
        if(lp.width!=width||lp.height!=ViewGroup.LayoutParams.WRAP_CONTENT||lp.leftMargin!=x||lp.topMargin!=y){
            lp.width=width;lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;lp.leftMargin=x;lp.topMargin=y;panel.setLayoutParams(lp);
        }
    }
    void dismiss(){
        if(!showing)return;showing=false;overlay.animate().cancel();host.removeView(overlay);overlay.removeView(panel);
        for(int i=0;i<hidden.size();i++)hidden.get(i).setImportantForAccessibility(accessibility.get(i));
        hidden.clear();accessibility.clear();
        if(previousFocus!=null&&previousFocus.isAttachedToWindow())previousFocus.requestFocus();
    }
}
