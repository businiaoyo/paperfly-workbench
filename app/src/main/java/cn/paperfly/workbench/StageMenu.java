package cn.paperfly.workbench;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Menu presentation only; the Activity remains responsible for actions and focus. */
final class StageMenu {
    interface Actions {void freeform();void minimize();void rail();void dock();void shortcuts();void back();void fullscreen();void appFullscreen();void apps();void combinations();void appearance();void split();void memory();void remove();void exit();}
    static WorkbenchDialog create(Activity activity,View anchor,String app,String memory,boolean fullscreen,Actions actions){
        boolean hasApp=app!=null;WorkbenchDialog d=new WorkbenchDialog(activity,"工作台",hasApp?app+" · 当前应用":"添加应用，开始工作",336).anchoredTo(anchor);
        Context c=d.getContext();LinearLayout first=new LinearLayout(c);
        tile(d,first,WorkbenchIcon.Kind.FULLSCREEN,c.getString(fullscreen?R.string.stage_leave_fullscreen:R.string.stage_workspace_fullscreen),
                fullscreen?c.getString(R.string.stage_leave_workspace_fullscreen):"整个工作台全屏",hasApp||fullscreen,actions::fullscreen);
        tile(d,first,WorkbenchIcon.Kind.APPS,"添加应用","添加与管理应用",true,actions::apps);
        tile(d,first,WorkbenchIcon.Kind.APPEARANCE,"外观比例","外观与比例",true,actions::appearance);
        d.body.addView(first);d.divider();
        if(hasApp)item(d,WorkbenchIcon.Kind.FULLSCREEN,c.getString(R.string.stage_app_fullscreen),c.getString(R.string.stage_app_fullscreen_detail),false,actions::appFullscreen);
        if(hasApp)item(d,WorkbenchIcon.Kind.SPLIT,"窗口布局","自由窗口、平铺与分屏",true,actions::freeform);
        if(hasApp)item(d,WorkbenchIcon.Kind.BACK,"最小化当前窗口",null,false,actions::minimize);
        item(d,WorkbenchIcon.Kind.APPEARANCE,"显示 / 隐藏侧栏",null,false,actions::rail);
        item(d,WorkbenchIcon.Kind.APPS,"Dock 设置","固定应用与自动隐藏",true,actions::dock);
        item(d,WorkbenchIcon.Kind.COLLECTION,"手势与快捷键",null,true,actions::shortcuts);
        if(hasApp)item(d,WorkbenchIcon.Kind.BACK,"返回当前应用",null,false,actions::back);
        item(d,WorkbenchIcon.Kind.COLLECTION,"应用组合",null,true,actions::combinations);
        if(hasApp)item(d,WorkbenchIcon.Kind.SPLIT,"分屏卡片",null,true,actions::split);
        item(d,WorkbenchIcon.Kind.MEMORY,"内存占用",memory,true,actions::memory);
        if(hasApp)d.action("关闭当前窗口",false,()->d.runAfterDismiss(actions::remove)).setContentDescription("关闭当前应用窗口，保留其他窗口");
        d.action("退出工作台",false,()->d.runAfterDismiss(actions::exit)).setTextColor(WorkbenchUi.DANGER);return d;
    }
    private static void item(WorkbenchDialog d,WorkbenchIcon.Kind icon,String label,String detail,boolean arrow,Runnable action){
        d.row(icon,label,detail,arrow,false,action);View row=d.body.getChildAt(d.body.getChildCount()-1);Context c=d.getContext();row.setMinimumHeight(WorkbenchUi.dp(c,48));row.setPadding(WorkbenchUi.dp(c,14),WorkbenchUi.dp(c,10),WorkbenchUi.dp(c,14),WorkbenchUi.dp(c,10));
    }
    private static void tile(WorkbenchDialog d,LinearLayout row,WorkbenchIcon.Kind icon,String label,String description,boolean enabled,Runnable action){
        Context c=d.getContext();LinearLayout tile=WorkbenchUi.column(c);tile.setGravity(Gravity.CENTER);tile.setBackground(WorkbenchUi.touch(c,WorkbenchUi.TILE,16));tile.setContentDescription(description);tile.setFocusable(true);tile.setEnabled(enabled);tile.setAlpha(enabled?1:.35f);
        tile.addView(WorkbenchUi.icon(c,icon,WorkbenchUi.ACCENT),new LinearLayout.LayoutParams(WorkbenchUi.dp(c,24),WorkbenchUi.dp(c,24)));
        TextView text=WorkbenchUi.text(c,label,13,WorkbenchUi.INK);text.setGravity(Gravity.CENTER);text.setPadding(0,WorkbenchUi.dp(c,8),0,0);tile.addView(text);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,WorkbenchUi.dp(c,72),1);if(row.getChildCount()>0)lp.leftMargin=WorkbenchUi.dp(c,8);row.addView(tile,lp);tile.setOnClickListener(v->d.runAfterDismiss(action));
    }
}
