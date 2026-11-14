package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import cn.paperfly.workbench.model.WorkspaceCard;
import java.util.*;

/** One independently accessible handle per adjacent pair; preview is never persisted here. */
@android.annotation.SuppressLint("ViewConstructor")
final class SplitDivider extends FrameLayout {
    interface Actions {
        void begin(WorkspaceCard card);
        void preview(WorkspaceCard card,List<Float> weights);
        void end(WorkspaceCard card,List<Float> weights,boolean commit);
        void edit(WorkspaceCard card,int boundary,boolean horizontal,View anchor);
    }
    private final Actions actions;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Handle> handles=new ArrayList<>();
    private StageLayout.Bounds bounds;
    private WorkspaceCard card;
    private List<Float> preview;
    private boolean horizontal,moved;
    private float gap,downX,downY,grabOffset;
    private int active=-1;

    SplitDivider(Context context,Actions actions){
        super(context);this.actions=actions;setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void configure(WorkspaceCard value,StageLayout.Bounds area,boolean across,float spacing){
        if(dragging()&&(!value.id.equals(card.id)||!value.apps.equals(card.apps)||!value.weights.equals(card.weights)
                ||across!=horizontal||bounds.left!=area.left||bounds.top!=area.top||bounds.right!=area.right||bounds.bottom!=area.bottom))cancel();
        card=value;bounds=area;horizontal=across;gap=spacing;if(!dragging())preview=value.weights;
        while(handles.size()>value.apps.size()-1)removeView(handles.remove(handles.size()-1));
        while(handles.size()<value.apps.size()-1){Handle handle=new Handle(getContext(),handles.size());handles.add(handle);addView(handle,new LayoutParams(1,1));}
        position();
    }
    boolean dragging(){return active>=0;}
    void cancel(){if(!dragging())return;active=-1;preview=card.weights;position();actions.end(card,preview,false);}
    private int dp(float value){return WorkbenchUi.dp(getContext(),value);}
    private float coordinate(int index){return PaneLayout.divider(bounds,preview,index,horizontal,gap);}
    private void position(){
        if(bounds==null)return;float middle=horizontal?(bounds.top+bounds.bottom)/2:(bounds.left+bounds.right)/2;
        for(int i=0;i<handles.size();i++){
            Handle handle=handles.get(i);LayoutParams lp=(LayoutParams)handle.getLayoutParams();int w=dp(horizontal?32:64),h=dp(horizontal?64:32);
            if(lp.width!=w||lp.height!=h){lp.width=w;lp.height=h;handle.setLayoutParams(lp);}
            handle.setTranslationX((horizontal?coordinate(i):middle)-w/2f);handle.setTranslationY((horizontal?middle:coordinate(i))-h/2f);
            String description="第 "+(i+1)+" 和第 "+(i+2)+" 个应用的分隔条：拖动调比例，轻点管理窗口";
            handle.setContentDescription(description);handle.setTooltipText("拖动调比例 · 轻点管理窗口");handle.invalidate();
        }invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        if(!dragging()||!moved)return;
        float middle=horizontal?(bounds.top+bounds.bottom)/2:(bounds.left+bounds.right)/2;
        float x=horizontal?coordinate(active):middle,y=horizontal?middle-dp(38):coordinate(active)-dp(16);
        paint.setColor(WorkbenchUi.SURFACE);canvas.drawRoundRect(x-dp(64),y-dp(20),x+dp(64),y+dp(8),dp(8),dp(8),paint);
        paint.setColor(WorkbenchUi.ACCENT);paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(Math.round(preview.get(active)*100)+"% / "+Math.round(preview.get(active+1)*100)+"%",x,y,paint);
    }
    private void finish(boolean commit){
        WorkspaceCard original=card;List<Float> selected=preview;active=-1;
        actions.end(original,selected,commit);position();
    }
    private final class Handle extends View {
        final int index;
        Handle(Context context,int index){super(context);this.index=index;setFocusable(true);setOnClickListener(v->{if(card!=null&&index+1<card.apps.size())actions.edit(card,index,horizontal,this);});}
        @Override protected void onDraw(Canvas canvas){
            paint.setColor(index==active?WorkbenchUi.ACCENT:0xddffffff);float x=getWidth()/2f,y=getHeight()/2f;
            canvas.drawRoundRect(x-dp(horizontal?2:22),y-dp(horizontal?22:2),x+dp(horizontal?2:22),y+dp(horizontal?22:2),dp(2),dp(2),paint);
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();int[] origin=new int[2];SplitDivider.this.getLocationOnScreen(origin);
            float x=event.getRawX()-origin[0],y=event.getRawY()-origin[1];
            if(action==MotionEvent.ACTION_DOWN){
                if(card==null)return false;active=index;preview=card.weights;downX=x;downY=y;grabOffset=(horizontal?x:y)-coordinate(index);moved=false;
                getParent().requestDisallowInterceptTouchEvent(true);actions.begin(card);position();return true;
            }
            if(active!=index)return false;
            if(action==MotionEvent.ACTION_CANCEL||event.getPointerCount()>1){cancel();return true;}
            if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
                moved|=Math.hypot(x-downX,y-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if(moved){List<Float> next=PaneLayout.moveDivider(bounds,card.weights,index,(horizontal?x:y)-grabOffset,horizontal,gap,dp(100));
                    if(!next.equals(preview)){preview=next;actions.preview(card,preview);position();}}
                if(action==MotionEvent.ACTION_UP){boolean click=!moved;finish(moved);if(click)performClick();}
            }return true;
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");
            float pair=preview.get(index)+preview.get(index+1);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0,100,preview.get(index)*100/pair));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        @Override public boolean performAccessibilityAction(int action,Bundle args){
            if(card!=null&&isShown()&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){
                float length=(horizontal?bounds.width():bounds.height())-gap*(preview.size()-1);
                List<Float> next=PaneLayout.moveDivider(bounds,card.weights,index,coordinate(index)+length*.05f*(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1),horizontal,gap,dp(100));
                actions.begin(card);actions.preview(card,next);actions.end(card,next,true);return true;
            }return super.performAccessibilityAction(action,args);
        }
    }
}
