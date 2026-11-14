package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EdgeBackGestureTest {
    private EdgeBackGesture gesture(){return new EdgeBackGesture(8,24,56);}
    @Test public void rightEdgeCommitsAfterRelease(){EdgeBackGesture g=gesture();assertTrue(g.begin(998,400,1000,800,40));g.move(970,402);assertTrue(g.claimed());assertTrue(g.finish(920,405));assertFalse(g.tracking());}
    @Test public void interiorSwipeStaysWithApp(){EdgeBackGesture g=gesture();assertFalse(g.begin(970,400,1000,800,40));assertFalse(g.finish(800,400));}
    @Test public void verticalScrollLocksOutBack(){EdgeBackGesture g=gesture();g.begin(999,400,1000,800,40);g.move(996,430);g.move(800,432);assertFalse(g.claimed());assertFalse(g.finish(800,432));}
    @Test public void shortAndReturnedDragsDoNotCommit(){EdgeBackGesture g=gesture();g.begin(999,400,1000,800,40);assertFalse(g.finish(980,400));g.begin(999,400,1000,800,40);g.move(900,400);assertTrue(g.claimed());assertFalse(g.finish(993,400));}
    @Test public void systemOrMultitouchCancellationCannotCommit(){EdgeBackGesture g=gesture();g.begin(999,400,1000,800,40);g.move(900,400);g.cancel();assertFalse(g.finish(800,400));}
    @Test public void diagonalAndOutwardMovesDoNotCommit(){EdgeBackGesture g=gesture();g.begin(999,400,1000,800,40);assertFalse(g.finish(930,460));g.begin(980,400,1000,800,40);g.move(995,400);assertFalse(g.finish(800,400));}
    @Test public void excludesSystemCornersAndUsesCurrentWindowSize(){EdgeBackGesture g=gesture();assertFalse(g.begin(999,20,1000,800,40));assertFalse(g.begin(999,790,1000,800,40));assertTrue(g.begin(1438,900,1440,2304,40));assertTrue(g.finish(1350,900));assertFalse(g.begin(1438,900,2304,1440,40));assertTrue(g.begin(2302,900,2304,1440,40));assertTrue(g.finish(2200,900));}
}
