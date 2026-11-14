package cn.paperfly.workbench;

import android.app.Activity;

/** Layout controls independent of session ownership and live displays. */
final class WindowArrangementPanel {
    interface Actions {void split();void freeform();void half(boolean trailing,boolean horizontal);void tile();void cascade();}
    static WorkbenchDialog create(Activity activity,boolean freeform,Actions actions){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"窗口布局","调整应用画布大小；全屏请使用窗口全屏按钮",360);
        dialog.row(WorkbenchIcon.Kind.SPLIT,"分屏比例与方向","调整占比、左右或上下排列",true,false,actions::split);
        dialog.row(WorkbenchIcon.Kind.SPLIT,freeform?"切换到分屏布局":"切换到自由窗口",null,false,false,actions::freeform);
        dialog.row(WorkbenchIcon.Kind.SPLIT,"左半屏",null,false,false,()->actions.half(false,true));
        dialog.row(WorkbenchIcon.Kind.SPLIT,"右半屏",null,false,false,()->actions.half(true,true));
        dialog.row(WorkbenchIcon.Kind.SPLIT,"上半屏",null,false,false,()->actions.half(false,false));
        dialog.row(WorkbenchIcon.Kind.SPLIT,"下半屏",null,false,false,()->actions.half(true,false));
        dialog.row(WorkbenchIcon.Kind.APPS,"平铺当前组窗口",null,false,false,actions::tile);
        dialog.row(WorkbenchIcon.Kind.APPS,"层叠当前组窗口",null,false,false,actions::cascade);
        return dialog;
    }
}
