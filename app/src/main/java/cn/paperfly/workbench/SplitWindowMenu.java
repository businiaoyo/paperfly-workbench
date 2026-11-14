package cn.paperfly.workbench;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;

/** On-demand actions for the two windows adjacent to a divider; no persistent canvas controls. */
final class SplitWindowMenu {
    static final class Item {
        final String name,position;
        final Drawable icon;
        final Runnable close;
        Item(String name,String position,Drawable icon,Runnable close){this.name=name;this.position=position;this.icon=icon;this.close=close;}
    }

    static WorkbenchDialog create(Activity activity,View anchor,List<Item> items,Runnable layout){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"分屏操作","拖动分隔条可调整比例",320).anchoredTo(anchor);
        Context c=dialog.getContext();
        for(Item item:items){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c,8),dp(c,8),dp(c,4),dp(c,8));
            ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if(item.icon==null)icon.setImageResource(android.R.drawable.sym_def_app_icon);
            else{Drawable.ConstantState state=item.icon.getConstantState();icon.setImageDrawable(state==null?item.icon:state.newDrawable(c.getResources()).mutate());}
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon,new LinearLayout.LayoutParams(dp(c,32),dp(c,32)));
            LinearLayout words=WorkbenchUi.column(c);
            TextView name=WorkbenchUi.text(c,item.name,15,WorkbenchUi.INK);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);
            TextView position=WorkbenchUi.text(c,item.position,12,WorkbenchUi.MUTED);position.setPadding(0,dp(c,5),0,0);words.addView(position);
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(c,12);wp.rightMargin=dp(c,8);row.addView(words,wp);
            Button close=WorkbenchUi.button(c,"关闭",false);close.setTextColor(WorkbenchUi.DANGER);
            close.setContentDescription("关闭 "+item.name+" 窗口");
            close.setOnClickListener(v->dialog.runAfterDismiss(item.close));
            row.addView(close,new LinearLayout.LayoutParams(dp(c,64),dp(c,48)));dialog.body.addView(row);
        }
        dialog.divider();dialog.row(WorkbenchIcon.Kind.SPLIT,"调整分屏布局",null,true,false,layout);
        return dialog;
    }
    private static int dp(Context c,int value){return WorkbenchUi.dp(c,value);}
}
