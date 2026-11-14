package cn.paperfly.workbench;

import android.content.ComponentName;
import android.content.Context;
import cn.paperfly.workbench.model.*;
import java.util.*;

final class WorkspaceLabels {
    static List<String> apps(Context context,WorkspaceDefinition definition){
        List<String> result=new ArrayList<>();for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps){
            try{result.add(context.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(app.component),0).loadLabel(context.getPackageManager()).toString());}
            catch(Exception missing){result.add(app.packageName+"（暂不可用）");}
        }return result;
    }
    static String summary(Context context,WorkspaceDefinition definition){return android.text.TextUtils.join(" · ",apps(context,definition));}
}
