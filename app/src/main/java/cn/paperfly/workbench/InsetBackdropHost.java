package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** One edge-to-edge background; only the live scene is inset around visible system controls. */
final class InsetBackdropHost extends FrameLayout {
    final FrameLayout scene;
    private boolean fullscreen,avoidIme;

    InsetBackdropHost(Context context){
        super(context);scene=new FrameLayout(context);
        addView(scene,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        setOnApplyWindowInsetsListener((view,insets)->{positionScene(insets);return insets;});
    }

    void avoidIme(){avoidIme=true;requestApplyInsets();}

    void fullscreen(boolean value){
        fullscreen=value;
        WindowInsets insets=getRootWindowInsets();if(insets!=null)positionScene(insets);
        requestApplyInsets();
    }

    private void positionScene(WindowInsets insets){
        Insets safe=fullscreen?Insets.NONE:insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        if(avoidIme)safe=Insets.max(safe,insets.getInsets(WindowInsets.Type.ime()));
        LayoutParams layout=(LayoutParams)scene.getLayoutParams();
        if(layout.leftMargin==safe.left&&layout.topMargin==safe.top&&layout.rightMargin==safe.right&&layout.bottomMargin==safe.bottom)return;
        layout.setMargins(safe.left,safe.top,safe.right,safe.bottom);scene.setLayoutParams(layout);
    }
}
