package cn.paperfly.workbench;

import android.view.View;

/** View transforms only; does not explicitly allocate bitmaps, layers, Surfaces or displays. */
final class StageCardPose {
    private final StageLayout.CardPose pose=new StageLayout.CardPose();
    /** Displayed size is independent of the app buffer while a pane closes or resizes. */
    void applyWindow(View view,float x,float y,float width,float height,int canvasWidth,int canvasHeight,float yaw,float horizon){
        pose.set(x,y,width/canvasWidth,height/canvasHeight,canvasWidth,yaw,horizon);
        apply(view);
    }
    /** Live split preview changes both edges immediately without replacing the live texture. */
    void applyBounds(View view,StageLayout.Bounds bounds,int width,int height){
        view.setPivotX(0);view.setPivotY(0);view.setRotationY(0);
        view.setTranslationX(bounds.left);view.setTranslationY(bounds.top);
        view.setScaleX(bounds.width()/width);view.setScaleY(bounds.height()/height);
    }
    void apply(View view,float x,float y,float scale,int canvasWidth,float yaw,float horizon){
        pose.set(x,y,scale,canvasWidth,yaw,horizon);
        apply(view);
    }
    private void apply(View view){
        view.setPivotX(pose.pivotX);view.setPivotY(pose.pivotY);
        // View normalises by densityDpi; Android's 3D camera uses 72 coordinate units per inch.
        view.setCameraDistance(pose.cameraDistance*view.getResources().getDisplayMetrics().densityDpi/72f);
        view.setTranslationX(pose.translationX);view.setTranslationY(pose.translationY);
        view.setScaleX(pose.scale);view.setScaleY(pose.scaleY);view.setRotationY(pose.yaw);
    }
}
