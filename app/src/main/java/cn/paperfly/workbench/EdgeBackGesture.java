package cn.paperfly.workbench;

/** Right-edge fallback when the device does not take ownership of the back gesture. */
final class EdgeBackGesture {
    private final float slop, edgeWidth, distance;
    private float startX, startY, inward, vertical;
    private boolean tracking, claimed;

    EdgeBackGesture(float slop, float edgeWidth, float distance) {
        this.slop=slop;this.edgeWidth=edgeWidth;this.distance=distance;
    }
    boolean begin(float x,float y,float width,float height,float corner) {
        cancel();
        tracking=width>0&&x>=width-edgeWidth&&x<width&&y>=corner&&y<height-corner;
        startX=x;startY=y;return tracking;
    }
    void move(float x,float y) {
        if(!tracking)return;
        inward=startX-x;vertical=Math.abs(y-startY);
        if(claimed)return;
        if(inward < -slop || (vertical>slop && vertical>=Math.max(0,inward))){tracking=false;return;}
        if(inward>slop&&inward>vertical*1.5f)claimed=true;
    }
    boolean tracking(){return tracking;}
    boolean claimed(){return claimed;}
    float progress(){return Math.max(0,Math.min(1,inward/distance));}
    boolean finish(float x,float y) {
        move(x,y);
        boolean commit=tracking&&claimed&&inward>=distance&&inward>vertical*1.5f;
        cancel();return commit;
    }
    void cancel(){tracking=claimed=false;inward=vertical=0;}
}
