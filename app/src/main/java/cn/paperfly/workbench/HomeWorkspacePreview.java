package cn.paperfly.workbench;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.*;
import android.view.View;
import cn.paperfly.workbench.model.*;
import java.util.*;

/** A static, symbolic preview of real saved cards; never captures or starts an application. */
@SuppressLint("ViewConstructor")
final class HomeWorkspacePreview extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RadialGradient glow=new RadialGradient(205,103,155,new int[]{0x345fc6aa,0x005fc6aa},null,Shader.TileMode.CLAMP);
    private final RectF iconBounds=new RectF();
    private final WorkspaceDefinition definition;
    private final Map<String,HomeAppAssets.Entry> assets;
    HomeWorkspacePreview(Context c,WorkspaceDefinition definition,Map<String,HomeAppAssets.Entry> assets){
        super(c);this.definition=definition;this.assets=assets;paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int save=canvas.save();float scale=Math.min(getWidth()/320f,getHeight()/210f);
        canvas.translate((getWidth()-320*scale)/2,(getHeight()-210*scale)/2);canvas.scale(scale,scale);
        paint.setShader(glow);
        canvas.drawRect(0,0,320,210,paint);paint.setShader(null);
        boolean populated=definition!=null&&!definition.cards.isEmpty();
        // Cards stay grouped: a split card gets multiple app icons inside the same window.
        for(int i=1;i<=(populated?Math.min(3,definition.cards.size()-1):2);i++){
            int dy=(i-1)*50;round(canvas,17,37+dy,79,79+dy,8,0x19334445);
            round(canvas,12,32+dy,74,74+dy,8,0xff6c8b87);
            if(populated)drawCard(canvas,definition.cards.get(i),43,53+dy,19,false);
            else round(canvas,33,44+dy,53,62+dy,4,0x66c8e9df);
        }
        round(canvas,91,26,310,188,14,0x35223232);
        round(canvas,84,18,303,180,14,0xffe7efeb);
        round(canvas,84,18,303,45,14,0xfff6f9f7);
        paint.setColor(0xffb6c9c3);for(int i=0;i<3;i++)canvas.drawCircle(99+i*9,32,2,paint);
        round(canvas,248,29,288,34,2.5f,0xffd4e2dc);
        if(populated)drawCard(canvas,definition.cards.get(0),193,100,42,true);
        else {round(canvas,174,80,212,115,8,0xff86b5a5);paint.setColor(0xfff6faf8);paint.setStrokeWidth(2);canvas.drawLine(193,89,193,106,paint);canvas.drawLine(184,98,202,98,paint);}
        round(canvas,137,143,251,148,2.5f,0xffceded6);
        round(canvas,159,154,229,158,2,0xffdae6df);
        canvas.restoreToCount(save);
    }
    private void drawCard(Canvas canvas,WorkspaceCard card,float x,float y,int size,boolean primary){
        int count=primary?Math.min(3,card.apps.size()):1;
        float gap=size+9;
        for(int i=0;i<count;i++){
            AppTarget app=card.apps.get(i);if(!primary)for(AppTarget member:card.apps)if(member.id.equals(card.focusedWindowId)){app=member;break;}
            float left=x-(count-1)*gap/2+i*gap-size/2f,top=y-size/2f;
            HomeAppAssets.Entry entry=assets.get(app.component);
            if(entry!=null&&entry.icon!=null){iconBounds.set(left,top,left+size,top+size);canvas.drawBitmap(entry.icon,null,iconBounds,paint);}
            else round(canvas,left,top,left+size,top+size,size*.23f,0xffa1c5b7);
        }
        if(card.apps.size()>count){paint.setTextSize(primary?11:9);paint.setColor(primary?0xff43675a:0xfff1faf6);canvas.drawText("+"+(card.apps.size()-count),x+size*.6f,y+size*.5f,paint);}
    }
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color){paint.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,paint);}
}
