package cn.paperfly.workbench;

import cn.paperfly.workbench.model.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class StagePresentationTest {
    @Test public void appExitRestoresEitherWorkspaceModeEvenAfterRepeatedTransitions() {
        for(boolean full:new boolean[]{false,true}) {
            StagePresentation p=new StagePresentation();p.setWorkspaceFullscreen(full);
            for(int repeat=0;repeat<10;repeat++) {
                assertTrue(p.enterApp(7));assertTrue(p.immersive());
                assertTrue(p.showsWindow(7));assertFalse(p.showsWindow(8));
                assertEquals(full,p.workspaceFullscreen());
                assertTrue(p.leaveApp());assertEquals(full,p.immersive());
                assertTrue(p.showsWindow(7));assertTrue(p.showsWindow(8));
            }
        }
    }

    @Test public void invalidOrDelayedTargetCannotStealFullscreenAndRemovalUnpinsIt() {
        StagePresentation p=new StagePresentation();
        assertFalse(p.enterApp(-1));assertFalse(p.immersive());
        assertTrue(p.enterApp(0));assertFalse(p.enterApp(3));
        assertFalse(p.windowRemoved(3));assertTrue(p.isFullscreenApp(0));
        assertTrue(p.windowRemoved(0));assertFalse(p.appFullscreen());assertTrue(p.showsWindow(3));
        assertFalse(p.leaveApp());
    }

    @Test public void focusedSplitMemberExpandsWithoutChangingCardOwnershipOrderOrSavedSnapshot() {
        AppTarget a=new AppTarget("a/.Main"),b=new AppTarget("b/.Main");
        WorkspaceCard split=new WorkspaceCard("split",Arrays.asList(a,b),b.component,.65f,2);
        WorkspaceDefinition saved=new WorkspaceDefinition("saved","Video",Arrays.asList(split,WorkspaceCard.single("side",new AppTarget("c/.Main"))),WorkspaceStyle.DEFAULT);
        WorkspaceDraft draft=new WorkspaceDraft(saved);CardSession session=new CardSession();session.bind(draft);
        session.add(10,"a/.Main");session.add(11,"b/.Main");session.add(12,"c/.Main");
        WorkspaceDefinition before=draft.snapshot();StagePresentation p=new StagePresentation();
        assertTrue(p.enterApp(session.primary()));assertTrue(p.showsWindow(11));assertFalse(p.showsWindow(10));assertFalse(p.showsWindow(12));
        assertSame(before,draft.snapshot());assertSame(split,session.card(10));assertSame(split,session.card(11));
        p.leaveApp();assertSame(before,draft.snapshot());assertSame(split,saved.cards.get(0));
        assertEquals(11,session.primary());assertEquals(0,session.slotOf(10));assertEquals(1,session.slotOf(12));
        // Ordinary card switching still works after returning, including both split members.
        session.select(12);assertEquals(1,session.slotOf(10));assertEquals(1,session.slotOf(11));
        assertEquals("split",saved.cards.get(0).id);assertEquals(b.component,saved.cards.get(0).focusedWindowId);
    }

    @Test public void freshSessionDoesNotRestoreTransientFullscreen() {
        StagePresentation old=new StagePresentation();old.setWorkspaceFullscreen(true);old.enterApp(2);
        StagePresentation fresh=new StagePresentation();assertFalse(fresh.immersive());assertTrue(fresh.showsWindow(9));
    }
}
