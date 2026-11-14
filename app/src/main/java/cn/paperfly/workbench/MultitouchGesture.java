package cn.paperfly.workbench;

/** Four/five-finger intent, independent of Android and window identities. */
final class MultitouchGesture {
    static final int NONE=0,NEXT=1,PREVIOUS=2,HOME=4;
    private final float threshold;private float x,y,radius;private boolean active;
    MultitouchGesture(float threshold){this.threshold=threshold;}
    void begin(float x,float y,float radius){this.x=x;this.y=y;this.radius=radius;active=true;}
    int finish(float x,float y,float radius){if(!active)return NONE;active=false;float dx=x-this.x,dy=y-this.y;
        if(this.radius>=threshold&&radius<this.radius*.55f)return HOME;
        if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.4f)return dx<0?NEXT:PREVIOUS;
        return NONE;
    }
    void cancel(){active=false;}
}
