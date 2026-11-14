package cn.paperfly.workbench;
import android.app.*;
import cn.paperfly.workbench.model.WorkspaceOptions;
import java.util.*;
import java.util.function.*;

/** Edits an immutable preference snapshot; choosing a pin never launches that application. */
final class DockPreferencesPanel {
    private final Activity activity;private final Supplier<WorkspaceOptions> options;private final Consumer<WorkspaceOptions> update;private final Consumer<Dialog> show;private final Function<String,String> label;
    DockPreferencesPanel(Activity activity,Supplier<WorkspaceOptions> options,Consumer<WorkspaceOptions> update,Consumer<Dialog> show,Function<String,String> label){this.activity=activity;this.options=options;this.update=update;this.show=show;this.label=label;}
    void display(){WorkspaceOptions current=options.get();WorkbenchDialog dialog=new WorkbenchDialog(activity,"Dock 设置","长按图标可拖入分组，停留后松手可打开菜单",420);
        dialog.row(WorkbenchIcon.Kind.APPS,current.dockVisible?"隐藏 Dock":"显示 Dock",null,false,false,()->{update.accept(options.get().visible(!current.dockVisible));display();});
        dialog.row(WorkbenchIcon.Kind.APPEARANCE,current.dockAutoHide?"关闭自动隐藏":"开启自动隐藏","触摸底部把手可再次显示",false,false,()->{update.accept(options.get().autoHide(!current.dockAutoHide));display();});
        dialog.divider();for(int i=0;i<current.pinnedApps.size();i++){int index=i;String component=current.pinnedApps.get(i);dialog.row(WorkbenchIcon.Kind.APPS,label.apply(component),"固定应用 "+(i+1),true,false,()->edit(index,component));}
        if(current.pinnedApps.size()<24)dialog.row(WorkbenchIcon.Kind.PLUS,"添加固定应用",null,true,false,this::pick);
        dialog.action("完成",false,dialog::dismiss);show.accept(dialog);
    }
    private void edit(int index,String component){WorkbenchDialog dialog=new WorkbenchDialog(activity,label.apply(component),"固定应用",320);
        if(index>0)dialog.row(WorkbenchIcon.Kind.BACK,"向前移",null,false,false,()->{update.accept(options.get().movePin(index,-1));display();});
        if(index+1<options.get().pinnedApps.size())dialog.row(WorkbenchIcon.Kind.BACK,"向后移",null,false,false,()->{update.accept(options.get().movePin(index,1));display();});
        dialog.row(WorkbenchIcon.Kind.CLOSE,"取消固定",null,false,false,()->{update.accept(options.get().pin(component,false));display();});show.accept(dialog);
    }
    private void pick(){AppPanel panel=new AppPanel(activity,new AppPanel.Host(){
        public List<AppPanel.Item> selected(){List<AppPanel.Item> items=new ArrayList<>();List<String> pins=options.get().pinnedApps;for(int i=0;i<pins.size();i++)items.add(new AppPanel.Item(i,pins.get(i),label.apply(pins.get(i)),false,true,false,i,pins.size()));return items;}
        public void add(String component){try{update.accept(options.get().pin(component,true));}catch(IllegalArgumentException error){android.widget.Toast.makeText(activity,"最多固定 24 个应用",android.widget.Toast.LENGTH_SHORT).show();}}
        public void remove(int id){List<String> pins=options.get().pinnedApps;if(id>=0&&id<pins.size())update.accept(options.get().pin(pins.get(id),false));}
        public void move(int id,int delta){update.accept(options.get().movePin(id,delta));}public void select(int id){}
    }).forPins();show.accept(panel);}
}
