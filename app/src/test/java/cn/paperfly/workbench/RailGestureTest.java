package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RailGestureTest {
    @Test public void smallMovementRemainsTap(){RailGesture g=new RailGesture(8);g.begin(100,100);g.move(105,104);assertTrue(g.tap());assertFalse(g.dismiss(200,54));}
    @Test public void eitherDirectionCanDismiss(){for(int direction:new int[]{-1,1}){RailGesture g=new RailGesture(8);g.begin(100,100);g.move(100+direction*90,103);assertTrue(g.dismiss(200,54));assertFalse(g.tap());}}
    @Test public void verticalScrollCannotBecomeDismissalWhenFingerDrifts(){RailGesture g=new RailGesture(8);g.begin(100,100);g.move(104,125);g.move(250,300);assertEquals(RailGesture.VERTICAL,g.axis);assertFalse(g.dismiss(200,54));}
    @Test public void shortHorizontalDragIsNotTapOrDismissal(){RailGesture g=new RailGesture(8);g.begin(100,100);g.move(125,101);assertEquals(RailGesture.HORIZONTAL,g.axis);assertFalse(g.tap());assertFalse(g.dismiss(200,54));}
    @Test public void ambiguousDiagonalDoesNotTriggerTap(){RailGesture g=new RailGesture(8);g.begin(100,100);g.move(130,130);assertFalse(g.tap());assertFalse(g.dismiss(200,54));}
}
