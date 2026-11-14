package cn.paperfly.workbench.model;

/** Normalized position within a card. No display identity or device pixels are persisted. */
public final class WindowPlacement {
    public final float x,y,width,height;
    public final int order;
    public WindowPlacement(float x,float y,float width,float height) {
        this(x,y,width,height,0);
    }
    public WindowPlacement(float x,float y,float width,float height,int order){
        if(order<0||order>10000)throw new IllegalArgumentException("Invalid stacking order");this.order=order;
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)
                ||x<0||y<0||width<.15f||height<.15f||width>1||height>1||x+width>1.0001f||y+height>1.0001f)
            throw new IllegalArgumentException("Invalid window placement");
        this.x=x;this.y=y;this.width=width;this.height=height;
    }
    public WindowPlacement withOrder(int order){return new WindowPlacement(x,y,width,height,order);}
    public static WindowPlacement bounded(float x,float y,float width,float height) {
        float w=Math.max(.15f,Math.min(1,width)),h=Math.max(.15f,Math.min(1,height));
        return new WindowPlacement(Math.max(0,Math.min(1-w,x)),Math.max(0,Math.min(1-h,y)),w,h);
    }
    public static WindowPlacement initial(int index,int count) {
        if(count==1)return new WindowPlacement(.08f,.06f,.84f,.88f);
        float offset=(index%5)*.055f;
        return new WindowPlacement(.025f+offset,.025f+offset,.70f,.70f,index);
    }
}
