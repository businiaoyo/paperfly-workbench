package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** An upright app icon attached to a preview corner, including an indicator for split cards. */
final class RailAppBadge extends FrameLayout {
    final ImageView icon;
    private final TextView count;
    RailAppBadge(Context context){
        super(context);setClipChildren(false);
        icon=new ImageView(context);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setPadding(dp(2),dp(2),dp(2),dp(2));
        GradientDrawable background=new GradientDrawable();background.setColor(0xf5ffffff);background.setCornerRadius(dp(8));icon.setBackground(background);
        addView(icon,new LayoutParams(-1,-1));
        count=new TextView(context);count.setTextSize(9);count.setTextColor(Color.WHITE);count.setGravity(Gravity.CENTER);
        GradientDrawable circle=new GradientDrawable();circle.setColor(0xff243c45);circle.setCornerRadius(dp(8));count.setBackground(circle);
        LayoutParams lp=new LayoutParams(dp(16),dp(16),Gravity.TOP|Gravity.RIGHT);lp.topMargin=-dp(3);lp.rightMargin=-dp(3);addView(count,lp);
        setElevation(dp(3));setFocusable(true);
    }
    void describe(String name,int members){setContentDescription("切换到"+name);setTooltipText(name);count.setVisibility(members>1?VISIBLE:GONE);if(members>1)count.setText(Integer.toString(members));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
