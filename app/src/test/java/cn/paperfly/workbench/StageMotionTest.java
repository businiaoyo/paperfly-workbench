package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public final class StageMotionTest {
    @Test public void retargetPreservesPositionAndVelocity(){
        StageMotion m=new StageMotion(0);m.target(1000);for(int i=0;i<7;i++)m.step(1.0/60);
        double position=m.value,velocity=m.velocity;m.target(0);assertEquals(position,m.value,0);assertEquals(velocity,m.velocity,0);
        for(int i=0;i<90;i++)m.step(1.0/60);assertEquals(0,m.value,0);assertEquals(0,m.velocity,0);
    }
    @Test public void anUnchangedSlotDoesNotMove(){StageMotion m=new StageMotion(145);m.target(145);assertFalse(m.step(.016));assertEquals(145,m.value,0);}
    @Test public void settledSwapHasContinuousIntermediateFrames(){
        StageMotion m=new StageMotion(400);m.target(1800);double old=m.value;int frames=0;
        while(m.step(1.0/120)&&frames++<150){assertTrue(m.value>=old);assertTrue(m.value<=1800);old=m.value;}
        assertTrue(frames>20);assertTrue(frames<100);assertEquals(1800,m.value,0);
    }
}
