package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public class CanvasCommitQueueTest {
    @Test public void oldFramesKeepTheirGeometryUntilResizeHasDrawn(){
        CanvasCommitQueue queue=new CanvasCommitQueue();
        CanvasSize wide=CanvasSize.nativeDisplay(1600,1200,300);
        queue.add(wide,200);
        assertNull(queue.ready(150));assertNull(queue.ready(199));
        assertSame(wide,queue.ready(200));assertNull(queue.ready(201));
    }
    @Test public void consecutiveClosesCommitEachMatchingFrame(){
        CanvasCommitQueue queue=new CanvasCommitQueue();
        CanvasSize two=CanvasSize.nativeDisplay(1200,1500,300),one=CanvasSize.nativeDisplay(1920,1200,240);
        queue.add(two,200);queue.add(one,300);
        assertSame(two,queue.ready(250));assertNull(queue.ready(299));assertSame(one,queue.ready(310));
    }
    @Test public void consumerMaySkipAnIntermediateFrame(){
        CanvasCommitQueue queue=new CanvasCommitQueue();
        CanvasSize latest=CanvasSize.nativeDisplay(1920,1200,240);
        queue.add(CanvasSize.nativeDisplay(1200,1500,300),200);queue.add(latest,300);
        assertSame(latest,queue.ready(350));assertNull(queue.ready(400));
    }
    @Test public void callbackCanArriveAfterItsFrame(){
        CanvasCommitQueue queue=new CanvasCommitQueue();CanvasSize size=CanvasSize.nativeDisplay(600,1200,200);
        assertNull(queue.ready(500));queue.add(size,400);assertSame(size,queue.ready(500));
    }
}
