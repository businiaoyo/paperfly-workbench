package cn.paperfly.workbench;

import android.app.Activity;
import android.content.Context;
import android.widget.*;
import cn.paperfly.workbench.model.WorkspaceCard;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/** Group editing presentation; CardSession remains the only source of live group ownership. */
final class SplitCardPanel {
    static final class Item {final int id;final String name;Item(int id,String name){this.id=id;this.name=name;}}
    static WorkbenchDialog create(Activity activity,WorkspaceCard card,String name,List<Item> choices,Runnable split,IntConsumer merge,BiConsumer<List<Float>,Integer> apply){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"分屏卡片",name+" · "+card.apps.size()+" 个应用",480);Context c=dialog.getContext();LinearLayout box=dialog.body;
        WorkbenchUi.section(box,"排列方式");WorkbenchUi.Choices axis=new WorkbenchUi.Choices(c,new String[]{"自动排列","左右排列","上下排列"},card.axis);box.addView(axis);
        java.util.ArrayList<Float> weights=new java.util.ArrayList<>(card.weights);
        WorkbenchUi.space(box,20);
        TextView proportions=WorkbenchUi.text(c,"",13,WorkbenchUi.ACCENT);box.addView(proportions);
        Runnable update=()->{java.util.ArrayList<String> values=new java.util.ArrayList<>();for(float weight:weights)values.add(Math.round(weight*100)+"%");proportions.setText(android.text.TextUtils.join(" · ",values));};update.run();
        box.addView(WorkbenchUi.text(c,"拖动分隔条，实时调整相邻窗口；轻点分隔条，可单独关闭其中一个应用",12,WorkbenchUi.MUTED));
        Button equal=WorkbenchUi.button(c,"均分所有应用",false);equal.setOnClickListener(v->{for(int i=0;i<weights.size();i++)weights.set(i,1f/weights.size());update.run();});box.addView(equal);
        LinearLayout detach=WorkbenchUi.row(c,WorkbenchIcon.Kind.SPLIT,"拆分为独立应用",card.apps.size()>1?"保留所有应用，分别放回侧栏":"当前卡片只有一个应用",false,false);
        detach.setEnabled(card.apps.size()>1);detach.setAlpha(card.apps.size()>1?1:.45f);detach.setOnClickListener(v->dialog.runAfterDismiss(split));box.addView(detach);
        WorkbenchUi.section(box,"加入当前分屏卡片");
        if(choices.isEmpty())box.addView(WorkbenchUi.text(c,"先在工作台加入其他应用",13,WorkbenchUi.MUTED));
        for(Item item:choices)dialog.row(WorkbenchIcon.Kind.PLUS,item.name,"合并到当前卡片",false,false,()->merge.accept(item.id));
        dialog.action("完成",false,dialog::dismiss);dialog.action("应用布局",true,()->dialog.runAfterDismiss(()->apply.accept(weights,axis.selected())));return dialog;
    }
}
