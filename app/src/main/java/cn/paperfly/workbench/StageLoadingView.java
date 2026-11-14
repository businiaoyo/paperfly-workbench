package cn.paperfly.workbench;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Static app identity while waiting; a single fade reveals the real Surface's first frame. */
final class StageLoadingView extends View {
    private static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private static final Typeface REGULAR=Typeface.create("sans-serif",Typeface.NORMAL);
    private final android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String name;
    private String status;
    private String title="";
    private Drawable icon;
    private final android.graphics.Rect iconBounds=new android.graphics.Rect();
    private Shader background;
    private boolean revealing;
    private int generation;

    StageLoadingView(Context context,String name){
        super(context);this.name=name;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        showStatus("准备接续");
    }
    void icon(Drawable drawable){
        Drawable.ConstantState state=drawable.getConstantState();
        icon=state==null?drawable:state.newDrawable(getResources()).mutate();invalidate();
    }
    void name(String value){name=value;onSizeChanged(getWidth(),getHeight(),getWidth(),getHeight());setContentDescription(name+"，"+status);invalidate();}
    void showStatus(String value){
        generation++;animate().cancel();revealing=false;status=value;
        setContentDescription(name+"，"+value);
        setAlpha(1f);setVisibility(VISIBLE);invalidate();
    }
    void reveal(){
        if(getVisibility()!=VISIBLE||revealing)return;
        revealing=true;int expected=++generation;
        animate().alpha(0f).setDuration(200).setInterpolator(new DecelerateInterpolator())
                .withEndAction(()->{if(generation==expected){setVisibility(GONE);revealing=false;}}).start();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        background=new LinearGradient(0,0,w,h,0xff334449,0xff242d36,Shader.TileMode.CLAMP);
        paint.setTypeface(MEDIUM);paint.setTextSize(24*Math.min(w,h)/640f);
        title=android.text.TextUtils.ellipsize(name,paint,w*.8f,android.text.TextUtils.TruncateAt.END).toString();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);paint.setShader(background);canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
        float unit=Math.min(getWidth(),getHeight())/640f,cx=getWidth()/2f,cy=getHeight()/2f;
        float size=88*unit,top=cy-96*unit;
        if(icon!=null){
            // Drawables without ConstantState may also be used by the rail badge.
            icon.copyBounds(iconBounds);
            icon.setBounds(Math.round(cx-size/2),Math.round(top),Math.round(cx+size/2),Math.round(top+size));
            icon.draw(canvas);icon.setBounds(iconBounds);
        }
        else{paint.setColor(0xff52656b);canvas.drawRoundRect(cx-size/2,top,cx+size/2,top+size,22*unit,22*unit,paint);}
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(MEDIUM);
        paint.setTextSize(24*unit);paint.setColor(0xfff0f4f4);
        canvas.drawText(title,cx,cy+37*unit,paint);
        paint.setTypeface(REGULAR);paint.setTextSize(16*unit);paint.setColor(0xffbbcbd0);
        canvas.drawText(status,cx,cy+70*unit,paint);
    }
    @Override protected void onDetachedFromWindow(){generation++;animate().cancel();revealing=false;super.onDetachedFromWindow();}
}
