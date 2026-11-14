package cn.paperfly.workbench;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.widget.Toast;
import cn.paperfly.workbench.model.WorkspaceDefinition;

final class WorkspaceShortcuts {
    static final String EXTRA_ID="workspace_id";
    static void pin(Activity activity,WorkspaceDefinition definition){
        ShortcutManager manager=activity.getSystemService(ShortcutManager.class);
        if(manager==null||!manager.isRequestPinShortcutSupported()){Toast.makeText(activity,"当前桌面不支持添加快捷方式",Toast.LENGTH_LONG).show();return;}
        ShortcutInfo shortcut=new ShortcutInfo.Builder(activity,"workspace-"+definition.id)
                .setShortLabel(definition.name).setIcon(Icon.createWithResource(activity,R.drawable.ic_workbench))
                .setIntent(new Intent(activity,MainActivity.class).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_ID,definition.id)).build();
        StageActivity.holdForPin();
        boolean accepted=manager.requestPinShortcut(shortcut,null);
        Toast.makeText(activity,accepted?"请在桌面提示中确认添加":"桌面未接受添加请求",Toast.LENGTH_LONG).show();
    }
    static void disable(Activity activity,String id){
        ShortcutManager manager=activity.getSystemService(ShortcutManager.class);
        if(manager!=null)manager.disableShortcuts(java.util.Collections.singletonList("workspace-"+id),"工作台组合已删除");
    }
    static void rename(Activity activity,WorkspaceDefinition definition){
        ShortcutManager manager=activity.getSystemService(ShortcutManager.class);
        if(manager!=null)manager.updateShortcuts(java.util.Collections.singletonList(new ShortcutInfo.Builder(activity,"workspace-"+definition.id).setShortLabel(definition.name).build()));
    }
}
