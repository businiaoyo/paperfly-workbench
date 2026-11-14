package cn.paperfly.workbench;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Desktop entry only. Explicit home and saved-combination intents still go to MainActivity. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(!StageActivity.resumeRetained(this))startActivity(new Intent(this,MainActivity.class));finish();
    }
}
