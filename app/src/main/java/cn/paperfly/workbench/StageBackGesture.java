package cn.paperfly.workbench;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Coordinates system back and a narrow right-edge fallback before scaled app/rail input. */
final class StageBackGesture {
    private final Activity activity;
    private final FrameLayout scene;
    private final Runnable back,cancelTouches;
    private final BooleanSupplier blocked;
    private final Consumer<String> report;
    private final EdgeBackGesture gesture;
    private final Indicator indicator;
    private android.window.OnBackInvokedCallback callback;
    private boolean consuming;
    private long fallbackCommittedAt;

    StageBackGesture(Activity activity,FrameLayout scene,Runnable back,Runnable cancelTouches,
                     BooleanSupplier blocked,Consumer<String> report) {
        this.activity=activity;this.scene=scene;this.back=back;this.cancelTouches=cancelTouches;
        this.blocked=blocked;this.report=report;
        float density=activity.getResources().getDisplayMetrics().density;
        gesture=new EdgeBackGesture(ViewConfiguration.get(activity).getScaledTouchSlop(),24*density,56*density);
        indicator=new Indicator(activity,density);indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        indicator.setVisibility(View.GONE);indicator.setTranslationZ(100*density);
        scene.addView(indicator,new FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=34)callback=new android.window.OnBackAnimationCallback(){
            @Override public void onBackStarted(android.window.BackEvent event){
                fallbackCommittedAt=0;cancel();cancelTouches.run();report.accept("SYSTEM_START_"+event.getSwipeEdge());
            }
            @Override public void onBackCancelled(){cancel();report.accept("SYSTEM_CANCEL");}
            @Override public void onBackInvoked(){systemBack();}
        };
        else if(Build.VERSION.SDK_INT>=33)callback=this::systemBack;
        if(Build.VERSION.SDK_INT>=33)activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
    }
    boolean dispatch(MotionEvent event,Predicate<MotionEvent> downstream) {
        int action=event.getActionMasked();
        View decor=activity.getWindow().getDecorView();int[] windowOrigin=new int[2];decor.getLocationOnScreen(windowOrigin);
        float x=event.getRawX()-windowOrigin[0],y=event.getRawY()-windowOrigin[1];
        if(blocked.getAsBoolean()){cancel();return downstream.test(event);}
        if(action==MotionEvent.ACTION_DOWN){
            consuming=false;fallbackCommittedAt=0;
            gesture.begin(x,y,decor.getWidth(),decor.getHeight(),40*indicator.density);
        } else if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){
            boolean owned=consuming;cancel();
            if(owned){consuming=action!=MotionEvent.ACTION_CANCEL;return true;}
            return downstream.test(event);
        } else if(action==MotionEvent.ACTION_MOVE&&gesture.tracking()){
            gesture.move(x,y);
            if(gesture.claimed()){
                if(!consuming){
                    consuming=true;
                    cancelChild(event,downstream);
                }
                int[] origin=new int[2];scene.getLocationOnScreen(origin);
                indicator.show(event.getRawY()-origin[1],gesture.progress());
            }
        } else if(action==MotionEvent.ACTION_UP){
            boolean owned=consuming,commit=gesture.finish(x,y);
            consuming=false;indicator.setVisibility(View.GONE);
            if(commit){
                if(!owned)cancelChild(event,downstream);
                fallbackCommittedAt=SystemClock.uptimeMillis();report.accept("RIGHT_EDGE");back.run();
            }
            if(owned||commit)return true;
        }
        if(consuming)return true;
        // Empty scene margins still need the remainder of an edge touch stream.
        return downstream.test(event)||gesture.tracking();
    }
    private void cancelChild(MotionEvent event,Predicate<MotionEvent> downstream){
        MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
        downstream.test(cancel);cancel.recycle();cancelTouches.run();
    }
    void systemBack(){
        cancel();cancelTouches.run();
        // Some legacy implementations deliver both the released stream and a back key.
        if(fallbackCommittedAt!=0&&SystemClock.uptimeMillis()-fallbackCommittedAt<250)return;
        report.accept("SYSTEM_INVOKE");back.run();
    }
    void watchDialog(android.app.Dialog dialog){
        cancel();cancelTouches.run();
        // Same-window menus use the Activity's existing dispatch and system-back callback.
        if(dialog instanceof WorkbenchDialog&&((WorkbenchDialog)dialog).inHost())return;
        android.view.Window window=dialog.getWindow();
        if(!(window.getCallback() instanceof GestureWindowCallback))window.setCallback(new GestureWindowCallback(window.getCallback(),this));
        if(Build.VERSION.SDK_INT>=34)dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,new android.window.OnBackAnimationCallback(){
                    @Override public void onBackStarted(android.window.BackEvent event){cancel();cancelTouches.run();}
                    @Override public void onBackInvoked(){report.accept("DIALOG_BACK");dialog.onBackPressed();}
                });
        else if(Build.VERSION.SDK_INT>=33)dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{report.accept("DIALOG_BACK");dialog.onBackPressed();});
    }
    void cancel(){gesture.cancel();consuming=false;indicator.setVisibility(View.GONE);}
    void close(){cancel();if(Build.VERSION.SDK_INT>=33&&callback!=null)
        activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);}

    private static final class Indicator extends View {
        final float density;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float y,progress;
        Indicator(Activity activity,float density){super(activity);this.density=density;}
        void show(float y,float progress){this.y=y;this.progress=progress;setVisibility(VISIBLE);invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float radius=19*density,x=getWidth()-(12+progress*26)*density;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(225,20,29,43));canvas.drawCircle(x,y,radius,paint);
            paint.setColor(progress>=1?AppPanel.ACCENT:Color.WHITE);paint.setStrokeWidth(2.5f*density);
            paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(x+3*density,y-6*density,x-3*density,y,paint);
            canvas.drawLine(x-3*density,y,x+3*density,y+6*density,paint);
        }
    }
}
