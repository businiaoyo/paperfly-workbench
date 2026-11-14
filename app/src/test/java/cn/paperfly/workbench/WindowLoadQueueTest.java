package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public class WindowLoadQueueTest {
    @Test public void reusedTaskWaitsForFrameThenUsesShortSettle(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("warm");q.add("cold");q.add("next");
        q.next(0);q.opened("warm",20,true);assertNull(q.next(1000));
        q.frame("warm",1200);assertNull(q.next(1379));assertEquals("cold",q.next(1380));
        q.opened("cold",1400,false);q.frame("cold",1500);
        assertNull(q.next(2399));assertEquals("next",q.next(2400));
    }
    @Test public void reusedEarlyFrameStillWaitsForRpcAndUserInteraction(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.next(0);q.frame("a",10);
        assertNull(q.next(2000));q.opened("a",2100,true);q.interaction(2200);
        assertNull(q.next(2699));assertEquals("b",q.next(2700));
    }
    @Test public void cancelledReusedTaskCannotOverlapOrReleaseAnotherRequest(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.add("c");q.next(0);q.cancel("a",50);
        assertNull(q.next(10000));q.opened("a",10000,true);
        assertNull(q.next(10179));assertEquals("b",q.next(10180));
        q.frame("a",10190);q.opened("a",10200,true);assertNull(q.next(20000));
        q.opened("b",20100,true);assertEquals("b",q.expire(28100));assertEquals("c",q.next(28280));
    }
    @Test public void waitsForBothRpcAndFrameThenSettles(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("main");q.add("side");
        assertEquals("main",q.next(0));q.opened("main",100);assertNull(q.next(2000));
        q.frame("main",2100);assertNull(q.next(2999));assertEquals("side",q.next(3000));
    }
    @Test public void firstFrameBeforeRpcDoesNotOverlap(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.next(0);q.frame("a",10);
        assertNull(q.next(5000));q.opened("a",5000);assertEquals("b",q.next(5900));
    }
    @Test public void missingFrameTimesOutWithoutBlockingOtherApps(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.next(0);q.opened("a",100);
        assertNull(q.expire(8099));assertEquals("a",q.expire(8100));assertEquals("b",q.next(9000));
    }
    @Test public void failureAdvancesAndLateFramesCannotCompleteNextLaunch(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.add("c");q.next(0);q.failed("a",100);
        assertEquals("b",q.next(1000));q.frame("a",1100);q.opened("b",1200);assertNull(q.next(5000));
    }
    @Test public void selectedWaitingAppHasPriority(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("main");q.add("b");q.add("c");q.next(0);q.prioritize("c");
        q.failed("main",100);assertEquals("c",q.next(1000));q.failed("c",1100);assertEquals("b",q.next(2000));
    }
    @Test public void cancelInFlightStillWaitsForRpc(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("b");q.next(0);q.cancel("a",100);
        assertNull(q.next(20000));q.opened("a",20000);assertEquals("b",q.next(20900));
    }
    @Test public void clearDoesNotLaunchOldPendingAndNewSessionWaitsForOldRpc(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("a");q.add("old");q.next(0);q.clear(100);q.add("new");
        assertNull(q.next(500));q.failed("a",600);assertEquals("new",q.next(1500));q.failed("new",1600);assertFalse(q.busy());
    }
    @Test public void touchDefersPendingLaunchAndRemovedAppNeverStarts(){
        WindowLoadQueue<String> q=new WindowLoadQueue<>();q.add("removed");q.add("b");q.cancel("removed",0);q.interaction(1000);
        assertNull(q.next(1499));assertEquals("b",q.next(1500));
    }
}
