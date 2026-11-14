package cn.paperfly.workbench;

import android.view.MotionEvent;
import java.util.function.*;

/** Claims a local four/five-finger stream and cancels the original app before switching. */
final class StageTouchGestures {
    private final MultitouchGesture gesture;private final BooleanSupplier blocked;
    private final Runnable cancelTouches;private final IntConsumer command;private boolean owned,finished;
    StageTouchGestures(float density,BooleanSupplier blocked,Runnable cancelTouches,IntConsumer command){
        gesture=new MultitouchGesture(64*density);this.blocked=blocked;this.cancelTouches=cancelTouches;this.command=command;
    }
    boolean dispatch(MotionEvent event,Predicate<MotionEvent> downstream){
        int action=event.getActionMasked(),count=event.getPointerCount();
        if(action==MotionEvent.ACTION_DOWN){owned=false;finished=false;gesture.cancel();}
        if(blocked.getAsBoolean()&&!owned)return downstream.test(event);
        if(blocked.getAsBoolean()){finished=true;gesture.cancel();}
        if(!finished&&action==MotionEvent.ACTION_POINTER_DOWN&&count>=4&&count<=5){
            if(!owned){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);downstream.test(cancel);cancel.recycle();cancelTouches.run();owned=true;}
            float[] center=center(event);gesture.begin(center[0],center[1],center[2]);
        }
        if(!owned)return downstream.test(event);
        if(count>5||action==MotionEvent.ACTION_CANCEL){finished=true;gesture.cancel();}
        if(!finished&&action==MotionEvent.ACTION_POINTER_UP){float[] center=center(event);int intent=gesture.finish(center[0],center[1],center[2]);finished=true;if(intent!=MultitouchGesture.NONE)command.accept(intent);}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){owned=false;gesture.cancel();}
        return true;
    }
    private float[] center(MotionEvent event){float x=0,y=0;int n=event.getPointerCount();for(int i=0;i<n;i++){x+=event.getX(i);y+=event.getY(i);}x/=n;y/=n;float radius=0;for(int i=0;i<n;i++)radius+=Math.hypot(event.getX(i)-x,event.getY(i)-y);return new float[]{x,y,radius/n};}
}
