package cn.paperfly.workbench;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
/** User-added system shortcut. It only opens the app's normal validated entry. */
public final class WorkbenchTileService extends TileService {
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setLabel("开放工作台");tile.setState(Tile.STATE_INACTIVE);tile.updateTile();}}
    // The Intent overload is required only on Android 11–13; Android 14+ always uses PendingIntent.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Override public void onClick(){super.onClick();Intent intent=new Intent(this,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));else startActivityAndCollapse(intent);
    }
}
