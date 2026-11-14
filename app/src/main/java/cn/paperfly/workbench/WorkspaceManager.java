package cn.paperfly.workbench;

import android.app.*;
import android.widget.*;
import cn.paperfly.workbench.data.WorkspacePersistence;
import cn.paperfly.workbench.model.*;
import java.util.*;
import java.util.function.Consumer;

/** Saved recipes have explicit save/overwrite actions; opening copies into the current draft. */
final class WorkspaceManager {
    private final Activity activity;
    private final WorkspacePersistence store;
    private final Consumer<Dialog> show;
    private final Consumer<WorkspaceDefinition> open;
    private final WorkspaceDefinition current;
    WorkspaceManager(Activity activity,WorkspaceDefinition current,Consumer<Dialog> show,Consumer<WorkspaceDefinition> open){this.activity=activity;this.current=current;this.show=show;this.open=open;store=WorkspacePersistence.get(activity);}
    void display(){store.library(library->{
        if(activity.isFinishing())return;
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"应用组合","保存常用的应用列表，下次一键打开",480);
        dialog.row(WorkbenchIcon.Kind.PLUS,"新建应用组合","选择应用并保存",true,false,()->new ApplicationSetEditor(activity,current.style,show,this::display).display());
        dialog.row(WorkbenchIcon.Kind.SAVE,"保存当前应用列表为组合",WorkspaceLabels.summary(activity,current),true,false,()->name("保存应用组合",current.name,name->store.saveAs(current,name,saved->{toast("组合已保存");display();},this::toast)));
        WorkbenchUi.section(dialog.body,"已保存 · "+library.saved.size());
        if(library.saved.isEmpty()){TextView empty=WorkbenchUi.text(dialog.getContext(),"还没有组合。先选几个常用应用，或保存当前列表。",14,WorkbenchUi.MUTED);empty.setPadding(WorkbenchUi.dp(activity,14),WorkbenchUi.dp(activity,16),WorkbenchUi.dp(activity,14),WorkbenchUi.dp(activity,16));dialog.body.addView(empty);}
        for(WorkspaceDefinition definition:library.saved)dialog.row(WorkbenchIcon.Kind.COLLECTION,definition.name+" · "+WorkspaceLabels.apps(activity,definition).size()+" 个应用",WorkspaceLabels.summary(activity,definition),true,false,()->options(definition));
        dialog.divider();dialog.row(WorkbenchIcon.Kind.APPS,"创建空白工作台",null,false,false,()->{WorkspaceDefinition empty=new WorkspaceDefinition(WorkspaceDefinition.CURRENT_ID,"新建工作台",Collections.emptyList(),current.style);store.saveCurrent(empty,this::toast);open.accept(empty);});
        dialog.action("完成",false,dialog::dismiss);show.accept(dialog);
    },this::toast);}
    private void options(WorkspaceDefinition definition){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,definition.name,WorkspaceLabels.summary(activity,definition),400);
        dialog.row(WorkbenchIcon.Kind.APPS,"打开组合",null,false,false,()->store.activate(definition.id,open,this::toast));
        dialog.row(WorkbenchIcon.Kind.RENAME,"重命名",null,true,false,()->name("重命名",definition.name,name->store.rename(definition.id,name,()->{WorkspaceShortcuts.rename(activity,new WorkspaceDefinition(definition.id,name,definition.cards,definition.style));display();},this::toast)));
        dialog.row(WorkbenchIcon.Kind.SAVE,"用当前工作台覆盖",null,true,false,()->confirm("覆盖组合","将当前应用、分屏配置及样式保存到「"+definition.name+"」。","覆盖",false,()->store.overwrite(definition.id,current,()->{toast("已覆盖保存");display();},this::toast)));
        dialog.row(WorkbenchIcon.Kind.SHORTCUT,"添加桌面快捷方式",null,false,false,()->WorkspaceShortcuts.pin(activity,definition));dialog.divider();
        dialog.row(WorkbenchIcon.Kind.TRASH,"删除组合",null,false,true,()->confirm("删除组合","删除「"+definition.name+"」？当前正在使用的工作台会保留。","删除",true,()->store.delete(definition.id,()->{WorkspaceShortcuts.disable(activity,definition.id);display();},this::toast)));
        show.accept(dialog);
    }
    private void confirm(String title,String message,String action,boolean danger,Runnable done){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,title,null,400);TextView text=WorkbenchUi.text(dialog.getContext(),message,15,WorkbenchUi.MUTED);text.setPadding(WorkbenchUi.dp(activity,8),WorkbenchUi.dp(activity,8),WorkbenchUi.dp(activity,8),WorkbenchUi.dp(activity,16));dialog.body.addView(text);
        dialog.action("取消",false,dialog::dismiss);Button positive=dialog.action(action,true,()->dialog.runAfterDismiss(done));if(danger)positive.setBackground(WorkbenchUi.touch(dialog.getContext(),WorkbenchUi.DANGER,14));show.accept(dialog);
    }
    private void name(String title,String initial,Consumer<String> done){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,title,"为这组应用起一个名字",400);EditText input=WorkbenchUi.input(dialog.getContext(),"例如：办公、阅读、学习");input.setText(initial);input.setContentDescription("应用组合名称");dialog.body.addView(input);
        dialog.action("取消",false,dialog::dismiss);dialog.action("保存",true,()->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("请输入名称");return;}dialog.runAfterDismiss(()->done.accept(name));});show.accept(dialog);
    }
    private void toast(String message){if(!activity.isFinishing())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
}
