package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small window controls above the live canvas; never owns or replaces its Surface. */
final class WindowChrome extends FrameLayout {
    interface Actions {void focus();void minimize();void fullscreen();void close();void drag(MotionEvent event,boolean resize);}
    private final LinearLayout caption;
    private final TextView grip;
    private final TextView title;
    private float titleDownX,titleDownY;
    @android.annotation.SuppressLint("ViewConstructor") // Created only by the scene, never inflated from XML.
    WindowChrome(Context context,String name,Actions actions){
        super(context);setClipChildren(false);setClipToPadding(false);
        caption=new LinearLayout(context);caption.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background=new GradientDrawable();background.setColor(0xee273341);background.setCornerRadius(dp(9));caption.setBackground(background);
        title=button(name,"移动窗口："+name);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(9),0,dp(5),0);
        caption.addView(title,new LinearLayout.LayoutParams(0,-1,1));title.setOnClickListener(v->actions.focus());title.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){titleDownX=e.getRawX();titleDownY=e.getRawY();}
            actions.drag(e,false);if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.hypot(e.getRawX()-titleDownX,e.getRawY()-titleDownY)<android.view.ViewConfiguration.get(context).getScaledTouchSlop())v.performClick();return true;});
        addControl("−","最小化 "+name,actions::minimize);addControl("□","全屏 "+name,actions::fullscreen);addControl("×","移出 "+name,actions::close);
        addView(caption,new FrameLayout.LayoutParams(-1,dp(28),Gravity.TOP));
        grip=button("◢","调整窗口大小："+name);grip.setBackground(WorkbenchUi.touch(context,0xbb273341,8));
        addView(grip,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.BOTTOM|Gravity.RIGHT));
        grip.setOnClickListener(v->actions.focus());grip.setOnTouchListener((v,e)->{actions.drag(e,true);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;});
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void addControl(String symbol,String description,Runnable action){TextView b=button(symbol,description);caption.addView(b,new LinearLayout.LayoutParams(dp(36),-1));b.setOnClickListener(v->action.run());}
    void name(String value){title.setText(value);title.setContentDescription("移动窗口："+value);}
    private TextView button(String label,String description){TextView b=new TextView(getContext());b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setGravity(Gravity.CENTER);b.setContentDescription(description);b.setFocusable(true);return b;}
    void position(float x,float y,float width,float height,float z){
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)getLayoutParams();int w=Math.max(1,Math.round(width)),h=Math.max(dp(60),Math.round(height)+dp(28));
        // On narrow portrait windows, keep the close control and expose the other actions in Dock.
        caption.getChildAt(1).setVisibility(w<dp(150)?View.GONE:View.VISIBLE);caption.getChildAt(2).setVisibility(w<dp(150)?View.GONE:View.VISIBLE);
        if(p.width!=w||p.height!=h){p.width=w;p.height=h;setLayoutParams(p);}setTranslationX(x);setTranslationY(y-dp(28));setTranslationZ(z);
    }
    private int dp(float value){return WorkbenchUi.dp(getContext(),value);}
}
