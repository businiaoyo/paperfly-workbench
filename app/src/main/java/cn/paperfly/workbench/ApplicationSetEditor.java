package cn.paperfly.workbench;

import android.app.*;
import android.content.ComponentName;
import android.widget.*;
import cn.paperfly.workbench.data.WorkspacePersistence;
import cn.paperfly.workbench.model.*;
import java.util.*;
import java.util.function.Consumer;

/** Select an application set without starting apps or changing the current workbench. */
final class ApplicationSetEditor {
    private final Activity activity;
    private final Consumer<Dialog> show;
    private final Runnable saved;
    private final WorkspaceDraft selection;
    private final Map<String,Integer> ids=new HashMap<>();
    private int nextId;

    ApplicationSetEditor(Activity activity,WorkspaceStyle style,Consumer<Dialog> show,Runnable saved){
        this.activity=activity;this.show=show;this.saved=saved;
        selection=new WorkspaceDraft(new WorkspaceDefinition(WorkspaceDefinition.CURRENT_ID,"新应用组合",Collections.emptyList(),style));
    }
    void display(){
        AppPanel panel=new AppPanel(activity,new AppPanel.Host(){
            public List<AppPanel.Item> selected(){
                List<AppPanel.Item> result=new ArrayList<>();String[] apps=selection.components();
                for(int i=0;i<apps.length;i++){
                    String component=apps[i],label=component;try{label=activity.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(component),0).loadLabel(activity.getPackageManager()).toString();}catch(Exception ignored){}
                    Integer id=ids.get(component);if(id==null){id=nextId++;ids.put(component,id);}
                    result.add(new AppPanel.Item(id,component,label,i==0,true,false,i,apps.length));
                }return result;
            }
            public void add(String component){selection.add(component);}
            public void remove(int id){String component=component(id);if(component!=null)selection.remove(component);}
            public void select(int id){String component=component(id);List<String> apps=new ArrayList<>(Arrays.asList(selection.components()));int index=apps.indexOf(component);if(index>0){Collections.swap(apps,0,index);selection.orderVisible(apps);}}
            public void move(int id,int delta){String component=component(id);List<String> apps=new ArrayList<>(Arrays.asList(selection.components()));int from=apps.indexOf(component),to=from+delta;
                if(Math.abs(delta)==1&&from>=0&&to>=0&&to<apps.size()){Collections.swap(apps,from,to);selection.orderVisible(apps);}}
        },this::name);
        show.accept(panel);
    }
    private String component(int id){for(Map.Entry<String,Integer> entry:ids.entrySet())if(entry.getValue()==id)return entry.getKey();return null;}
    private void name(){
        if(activity.isFinishing()||activity.isDestroyed())return;
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"保存应用组合","共 "+selection.components().length+" 个应用，下次点击组合即可全部打开",400);
        EditText input=WorkbenchUi.input(dialog.getContext(),"例如：办公、阅读、学习");input.setContentDescription("应用组合名称");dialog.body.addView(input);
        dialog.action("取消",false,dialog::dismiss);Button save=dialog.action("保存",true,()->{});
        show.accept(dialog);save.setOnClickListener(v->{
            String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("请输入组合名称");return;}
            save.setEnabled(false);
            WorkspacePersistence.get(activity).saveAs(selection.snapshot(),name,definition->{
                if(activity.isFinishing()||activity.isDestroyed())return;dialog.dismiss();Toast.makeText(activity,"已保存，点击组合即可打开全部应用",Toast.LENGTH_SHORT).show();saved.run();
            },message->{if(activity.isFinishing()||activity.isDestroyed())return;save.setEnabled(true);input.setError(message);});
        });
    }
}
