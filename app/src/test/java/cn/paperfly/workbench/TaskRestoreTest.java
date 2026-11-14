package cn.paperfly.workbench;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TaskRestoreTest {
    private static final class Backend implements TaskRestore.Backend {
        final List<String> calls=new ArrayList<>();
        final Exception rejected=new Exception("rejected");
        String fail="";int revealFailures;
        public void hide()throws Exception{calls.add("hide");if(fail.equals("hide"))throw rejected;}
        public void move()throws Exception{calls.add("move");if(fail.equals("move"))throw rejected;if(fail.equals("linkage"))throw new LinkageError();}
        public void show()throws Exception{calls.add("show");if(revealFailures-->0)throw new Exception("reveal failed");}
    }
    @Test public void successfulMigrationRevealsAfterMove()throws Exception{
        Backend b=new Backend();TaskRestore.run(b);assertEquals(Arrays.asList("hide","move","show"),b.calls);
    }
    @Test public void partiallyAppliedHideIsAlwaysReverted(){
        Backend b=new Backend();b.fail="hide";
        try{TaskRestore.run(b);fail();}catch(Exception e){assertSame(b.rejected,e);}
        assertEquals(Arrays.asList("hide","show"),b.calls);
    }
    @Test public void failedMoveRevealsAndReportsOriginalError(){
        Backend b=new Backend();b.fail="move";b.revealFailures=1;
        try{TaskRestore.run(b);fail();}catch(Exception e){assertSame(b.rejected,e);}
        assertEquals(Arrays.asList("hide","move","show","show"),b.calls);
    }
    @Test public void failedRevealRetriesWithoutRepeatingMigration()throws Exception{
        Backend b=new Backend();b.revealFailures=1;TaskRestore.run(b);
        assertEquals(Arrays.asList("hide","move","show","show"),b.calls);
    }
    @Test public void unrecoverableVisibilityFailureIsReported(){
        Backend b=new Backend();b.revealFailures=2;
        try{TaskRestore.run(b);fail();}catch(Exception e){assertEquals("reveal failed",e.getMessage());assertEquals(1,e.getSuppressed().length);}
    }
    @Test public void cleanupDoesNotMaskMoveFailure(){
        Backend b=new Backend();b.fail="move";b.revealFailures=2;
        try{TaskRestore.run(b);fail();}catch(Exception e){assertSame(b.rejected,e);assertEquals(1,e.getSuppressed().length);}
    }
    @Test public void platformLinkageFailureStillRestoresVisibility()throws Exception{
        Backend b=new Backend();b.fail="linkage";
        try{TaskRestore.run(b);fail();}catch(LinkageError expected){assertEquals(Arrays.asList("hide","move","show"),b.calls);}
    }
}
