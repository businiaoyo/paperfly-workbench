package cn.paperfly.workbench;

import cn.paperfly.workbench.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RecentWorkspaceTest {
    private WorkspaceDefinition previous(){
        return new WorkspaceDefinition("current","工作台",Arrays.asList(
            new WorkspaceCard("split",Arrays.asList(new AppTarget("notes/.Main"),new AppTarget("browser/.Main")),"notes/notes.Main",.7f,1),
            WorkspaceCard.single("missing",new AppTarget("missing/.Main")),
            WorkspaceCard.single("chat",new AppTarget("chat/.Main"))),WorkspaceStyle.DEFAULT);
    }
    @Test public void recentAppInsideGroupMovesWholeGroupAndFocusesOnlyTheDraft(){
        WorkspaceDefinition before=previous();
        WorkspaceDefinition after=RecentWorkspace.merge(before,Arrays.asList("chat/.Main","browser/.Alias","notes/.Main","newapp/.Main"));
        assertEquals("chat",after.cards.get(0).id);assertEquals("split",after.cards.get(1).id);
        assertEquals(2,after.cards.get(1).apps.size());assertEquals(.7f,after.cards.get(1).firstWeight,0);assertEquals(1,after.cards.get(1).axis);
        assertEquals("browser/browser.Main",after.cards.get(1).apps.get(1).component);
        assertEquals("missing",after.cards.get(3).id);assertEquals("split",before.cards.get(0).id);assertEquals(3,before.cards.size());
        WorkspaceDefinition grouped=RecentWorkspace.merge(before,Collections.singletonList("browser/.Alias"));
        assertEquals("browser/browser.Main",grouped.cards.get(0).focusedWindowId);
        assertEquals("notes/notes.Main",before.cards.get(0).focusedWindowId);
    }
    @Test public void entryCapsDistinctAppsAndSkipsMalformedOrDuplicateValues(){
        WorkspaceDefinition empty=new WorkspaceDefinition("current","空白",Collections.emptyList(),WorkspaceStyle.DEFAULT);
        WorkspaceDefinition after=RecentWorkspace.merge(empty,Arrays.asList("bad","a/.Main","a/.Other","b/.Main","c/.Main","d/.Main","e/.Main","f/.Main"));
        assertEquals(5,after.cards.size());assertEquals("e/e.Main",after.cards.get(4).focusedWindowId);
        assertTrue(after.cards.stream().allMatch(c->c.apps.size()==1));
    }
    @Test public void repeatedEntryKeepsIdentityAndEmptyDiscoveryDoesNotClearAnything(){
        WorkspaceDefinition once=RecentWorkspace.merge(previous(),Arrays.asList("newapp/.Main","chat/.Main"));
        WorkspaceDefinition twice=RecentWorkspace.merge(once,Arrays.asList("newapp/.Main","chat/.Main"));
        for(int i=0;i<once.cards.size();i++)assertEquals(once.cards.get(i).id,twice.cards.get(i).id);
        assertSame(once,RecentWorkspace.merge(once,Collections.emptyList()));
    }
}
