package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public class MultitouchGestureTest {
    private int gesture(float x,float y,float r){MultitouchGesture g=new MultitouchGesture(64);g.begin(400,400,200);return g.finish(x,y,r);}
    @Test public void directionRequiresDistanceAndDominance(){assertEquals(MultitouchGesture.NEXT,gesture(300,405,200));assertEquals(MultitouchGesture.PREVIOUS,gesture(500,405,200));assertEquals(MultitouchGesture.NONE,gesture(400,300,200));assertEquals(MultitouchGesture.NONE,gesture(420,400,200));assertEquals(MultitouchGesture.NONE,gesture(300,300,200));assertEquals(MultitouchGesture.NONE,gesture(400,500,200));}
    @Test public void pinchIsDistinctFromSwipe(){assertEquals(MultitouchGesture.HOME,gesture(400,400,90));assertEquals(MultitouchGesture.NONE,gesture(400,400,150));}
    @Test public void cancelAndFinishCannotRepeat(){MultitouchGesture g=new MultitouchGesture(64);g.begin(100,100,200);g.cancel();assertEquals(0,g.finish(0,100,200));g.begin(100,100,200);assertEquals(1,g.finish(0,100,200));assertEquals(0,g.finish(0,100,200));}
}
