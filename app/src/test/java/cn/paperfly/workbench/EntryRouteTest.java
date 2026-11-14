package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public class EntryRouteTest {
    @Test public void launcherWaitsForActivationAndConfiguration(){
        EntryRoute r=new EntryRoute(true);
        assertFalse(r.take(true,false,true,false,false));assertFalse(r.take(true,true,false,false,false));
        assertTrue(r.take(true,true,true,false,false));assertFalse(r.take(true,true,true,false,false));
    }
    @Test public void deliberateHomeSurvivesRefreshAndLauncherArmsNextEntry(){
        EntryRoute r=new EntryRoute(true);r.launch(true);
        assertFalse(r.take(true,true,true,false,false));r.launch(false);assertTrue(r.take(true,true,true,false,false));
    }
    @Test public void backgroundAndBusyCallbacksCannotNavigate(){
        EntryRoute r=new EntryRoute(true);
        assertFalse(r.take(false,true,true,false,false));assertFalse(r.take(true,true,true,true,false));assertTrue(r.pending());
        assertTrue(r.take(true,true,true,false,false));
    }
    @Test public void shortcutTakesPrecedenceAndFailureCanStayOnHome(){
        EntryRoute r=new EntryRoute(true);assertFalse(r.take(true,true,true,false,true));
        r.stayHome();assertFalse(r.take(true,true,true,false,false));
    }
    @Test public void restoredExplicitHomeDoesNotLoopBackToStage(){
        EntryRoute before=new EntryRoute(true);before.launch(true);EntryRoute recreated=new EntryRoute(before.pending());
        assertFalse(recreated.take(true,true,true,false,false));
    }
}
