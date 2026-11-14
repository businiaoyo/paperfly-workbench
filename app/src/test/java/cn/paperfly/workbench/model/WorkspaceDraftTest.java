package cn.paperfly.workbench.model;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class WorkspaceDraftTest {
    private static WorkspaceCard card(String name) { return WorkspaceCard.single("card-"+name, new AppTarget(name+"/.Main")); }
    private static WorkspaceDefinition definition(WorkspaceCard... cards) {
        return new WorkspaceDefinition("current", "写作", Arrays.asList(cards), WorkspaceStyle.DEFAULT);
    }

    @Test public void swappingKeepsCardIdentityAndStyleAndDoesNotDropUnavailableApps() {
        WorkspaceCard a=card("a"),b=card("b"),missing=card("missing");
        WorkspaceDraft draft=new WorkspaceDraft(definition(a,b,missing));
        draft.orderVisible(Arrays.asList("b/.Main","a/.Main"));
        assertEquals(Arrays.asList(b,a,missing),draft.snapshot().cards);
        assertEquals("写作",draft.snapshot().name);assertSame(WorkspaceStyle.DEFAULT,draft.snapshot().style);
        WorkspaceDefinition stable=draft.snapshot();draft.orderVisible(Arrays.asList("b/.Main","a/.Main"));
        assertSame(stable,draft.snapshot());
    }

    @Test public void addDeduplicatesPackageAndOnlyExplicitRemoveDeletesIt() {
        WorkspaceCard a=card("a");WorkspaceDraft draft=new WorkspaceDraft(definition(a));
        draft.add("a/.Main");draft.add("b/.Main");assertEquals(2,draft.snapshot().cards.size());
        draft.orderVisible(Collections.emptyList());assertEquals(2,draft.snapshot().cards.size());
        draft.remove("b/.Main");assertEquals(Collections.singletonList(a),draft.snapshot().cards);
    }

    @Test public void readdingAnUnavailableAppWithNewLauncherKeepsItsCardIdentity() {
        WorkspaceCard a=card("a");WorkspaceDraft draft=new WorkspaceDraft(definition(a,card("b")));
        draft.add("a/.NewLauncher");
        assertEquals(2,draft.snapshot().cards.size());assertEquals(a.id,draft.snapshot().cards.get(0).id);
        assertEquals("a/a.NewLauncher",draft.snapshot().cards.get(0).focusedWindowId);
        draft.orderVisible(Arrays.asList("a/.NewLauncher","b/.Main"));
        assertEquals(a.id,draft.snapshot().cards.get(0).id);
    }

    @Test public void snapshotsCannotBeMutatedByFollowingEditsOrCallers() {
        WorkspaceDraft draft=new WorkspaceDraft(definition(card("a")));WorkspaceDefinition before=draft.snapshot();
        draft.add("b/.Main");assertEquals(1,before.cards.size());
        assertThrows(UnsupportedOperationException.class,()->before.cards.clear());
        ArrayList<AppTarget> apps=new ArrayList<>(Collections.singletonList(new AppTarget("c/.Main")));
        WorkspaceCard card=new WorkspaceCard("id",apps,apps.get(0).component);apps.clear();assertEquals(1,card.apps.size());
        assertThrows(UnsupportedOperationException.class,()->card.apps.clear());
    }

    @Test public void groupSurvivesDraftLoadingAndProvidesAllLaunchTargets() {
        AppTarget a=new AppTarget("a/.Main"),b=new AppTarget("b/.Main");
        WorkspaceCard group=new WorkspaceCard("group",Arrays.asList(a,b),b.component);
        assertEquals(b.component,group.focusedWindowId);
        WorkspaceDraft draft=new WorkspaceDraft(definition(group));
        assertSame(group,draft.snapshot().cards.get(0));assertArrayEquals(new String[]{a.component,b.component},draft.components());
    }

    @Test public void invalidOwnershipAndFocusedAppAreRejected() {
        assertThrows(IllegalArgumentException.class,()->definition(card("a"),WorkspaceCard.single("other",new AppTarget("a/.Other"))));
        assertThrows(IllegalArgumentException.class,()->new WorkspaceCard("id",Collections.singletonList(new AppTarget("a/.Main")),"b/b.Main"));
        assertThrows(IllegalArgumentException.class,()->new WorkspaceCard("id",Collections.emptyList(),"a/a.Main"));
    }

    @Test public void invalidComponentsAndLayoutValuesAreRejected() {
        for(String value:Arrays.asList("a","/Main","a/","a/b/c","a/.M ain"))
            assertThrows(IllegalArgumentException.class,()->new AppTarget(value));
        assertEquals(new AppTarget("a/a.Main"),new AppTarget("a/.Main"));
        assertThrows(IllegalArgumentException.class,()->new WorkspaceStyle(0,0,0,0,190,.19f));
        assertThrows(IllegalArgumentException.class,()->new WorkspaceStyle(0,0,0,112,190,Float.NaN));
    }
}
