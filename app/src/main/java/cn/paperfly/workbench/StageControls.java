package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/** Small controls above the rail; no full-width bar or application title consumes the canvas. */
final class StageControls extends FrameLayout {
    private final LinearLayout capsule;
    private final ImageButton menu,previous;
    private final View divider;
    StageControls(Context c,Runnable back,Runnable openMenu){
        super(c);capsule=new LinearLayout(c);capsule.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable shape=WorkbenchUi.shape(c,0x66252e3a,22);shape.setStroke(dp(1),0x24ffffff);capsule.setBackground(new InsetDrawable(shape,0,dp(4),0,dp(4)));
        previous=WorkbenchUi.iconButton(c,WorkbenchIcon.Kind.BACK,"返回工作台首页");previous.setOnClickListener(v->back.run());capsule.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        divider=new View(c);divider.setBackgroundColor(0x28ffffff);capsule.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(16)));
        menu=WorkbenchUi.iconButton(c,WorkbenchIcon.Kind.MORE,"工作台菜单");menu.setOnClickListener(v->openMenu.run());capsule.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        addView(capsule,new LayoutParams(-2,dp(48),Gravity.CENTER));
    }
    View menuAnchor(){return capsule;}
    void appName(String name){menu.setContentDescription("工作台菜单，当前应用："+name);}
    void fit(int width){boolean both=width>=dp(105);previous.setVisibility(both?VISIBLE:GONE);divider.setVisibility(both?VISIBLE:GONE);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
