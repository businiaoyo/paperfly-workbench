package cn.paperfly.workbench;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** A shared 24-unit grid: alignment never depends on a font's glyph baseline. */
final class WorkbenchIcon extends Drawable {
    enum Kind { BACK, MORE, CLOSE, FULLSCREEN, APPS, COLLECTION, APPEARANCE, SPLIT, MEMORY,
        REMOVE, EXIT, PLUS, CHECK, NEXT, SAVE, RENAME, SHORTCUT, TRASH, IMAGE }
    private final Kind kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    WorkbenchIcon(Kind kind,int color){this.kind=kind;paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){
        int save=canvas.save();float size=Math.min(getBounds().width(),getBounds().height());
        canvas.translate(getBounds().exactCenterX()-size/2,getBounds().exactCenterY()-size/2);canvas.scale(size/24,size/24);paint.setStyle(Paint.Style.STROKE);
        switch(kind){
            case BACK: line(canvas,14.5f,6.5f,9,12,14.5f,17.5f);break;
            case NEXT: line(canvas,9.5f,6.5f,15,12,9.5f,17.5f);break;
            case MORE: paint.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)canvas.drawCircle(x,12,1.5f,paint);break;
            case CLOSE: canvas.drawLine(6,6,18,18,paint);canvas.drawLine(18,6,6,18,paint);break;
            case FULLSCREEN: line(canvas,9,4,4,4,4,9);line(canvas,15,4,20,4,20,9);line(canvas,4,15,4,20,9,20);line(canvas,15,20,20,20,20,15);break;
            case APPS: for(int x=4;x<=14;x+=10)for(int y=4;y<=14;y+=10)canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,paint);break;
            case COLLECTION: canvas.drawRoundRect(4,7,17,20,2,2,paint);line(canvas,8,4,20,4,20,16);break;
            case APPEARANCE: canvas.drawCircle(12,12,8,paint);paint.setStyle(Paint.Style.FILL);canvas.drawArc(4,4,20,20,-90,180,true,paint);break;
            case SPLIT: canvas.drawRoundRect(3.5f,5,20.5f,19,2,2,paint);canvas.drawLine(11,5,11,19,paint);break;
            case MEMORY: canvas.drawRoundRect(6,6,18,18,2,2,paint);canvas.drawRoundRect(9,9,15,15,1,1,paint);for(int x=8;x<=16;x+=4){canvas.drawLine(x,3,x,6,paint);canvas.drawLine(x,18,x,21,paint);canvas.drawLine(3,x,6,x,paint);canvas.drawLine(18,x,21,x,paint);}break;
            case REMOVE: canvas.drawRoundRect(4,5,20,19,2,2,paint);canvas.drawLine(8,12,16,12,paint);break;
            case EXIT: line(canvas,10,4,4,4,4,20,10,20);canvas.drawLine(10,12,21,12,paint);line(canvas,17,8,21,12,17,16);break;
            case PLUS: canvas.drawLine(5,12,19,12,paint);canvas.drawLine(12,5,12,19,paint);break;
            case CHECK: line(canvas,5,12,10,17,19,7);break;
            case SAVE: canvas.drawRoundRect(4,4,20,20,2,2,paint);canvas.drawRect(8,4,16,9,paint);canvas.drawRect(8,14,16,20,paint);break;
            case RENAME: line(canvas,5,15,16,4,20,8,9,19,4,20,5,15);canvas.drawLine(13,7,17,11,paint);break;
            case SHORTCUT: line(canvas,13,4,20,4,20,11);canvas.drawLine(20,4,10,14,paint);line(canvas,8,5,4,5,4,20,19,20,19,16);break;
            case TRASH: line(canvas,5,7,19,7);line(canvas,7,7,8,20,16,20,17,7);line(canvas,9,7,9,4,15,4,15,7);canvas.drawLine(11,10,11,16,paint);canvas.drawLine(14,10,14,16,paint);break;
            case IMAGE: canvas.drawRoundRect(3,4,21,20,2,2,paint);canvas.drawCircle(8,9,1.5f,paint);line(canvas,4,18,10,12,14,16,17,13,20,16);break;
        }
        canvas.restoreToCount(save);
    }
    private void line(Canvas canvas,float... points){path.rewind();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);canvas.drawPath(path,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
