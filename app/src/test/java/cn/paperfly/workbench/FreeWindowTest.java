package cn.paperfly.workbench;

import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.WorkspaceCodec;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class FreeWindowTest {
    private WorkspaceDefinition original(){return new WorkspaceDefinition("current","Original",Arrays.asList(
        new WorkspaceCard("group",Arrays.asList(new AppTarget("a/.Main"),new AppTarget("b/.Main")),"a/a.Main").withFreeform(true).withPlacement("b/b.Main",new WindowPlacement(.3f,.1f,.6f,.8f)),
        WorkspaceCard.single("side",new AppTarget("c/.Main"))),WorkspaceStyle.DEFAULT);}
    @Test public void focusAndMemberMovePreserveSavedLayoutAndStableCardIdentity(){
        WorkspaceDefinition saved=original();WorkspaceDraft d=new WorkspaceDraft(saved);CardSession s=new CardSession();s.bind(d);s.add(1,"a/.Main");s.add(2,"b/.Main");s.add(3,"c/.Main");
        s.select(2);assertEquals(.3f,s.card(2).placements.get("b/b.Main").x,0);assertTrue(s.card(2).freeform);
        s.moveMember(2,3);assertEquals("group",s.card(1).id);assertEquals("side",s.card(2).id);assertEquals(2,s.card(3).apps.size());
        assertEquals(2,saved.cards.get(0).apps.size());assertEquals("a/a.Main",saved.cards.get(0).focusedWindowId);
        assertTrue(s.detach(2));assertEquals(1,s.card(2).apps.size());assertNotEquals("side",s.card(2).id);
    }
    @Test public void placementClampsToViewportAndRejectsCorruption(){
        WindowPlacement p=WindowPlacement.bounded(2,-1,2,.1f);assertEquals(0,p.x,0);assertEquals(0,p.y,0);assertEquals(1,p.width,0);assertEquals(.15f,p.height,0);
        assertThrows(IllegalArgumentException.class,()->new WindowPlacement(Float.NaN,0,.5f,.5f));
        assertThrows(IllegalArgumentException.class,()->new WindowPlacement(.8f,0,.5f,.5f));
    }
    @Test public void migrationPreservesOldGroupsAndRoundTripRetainsNewLayouts()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();WorkspaceDefinition definition=original();
        String encoded=codec.encode(new WorkspaceLibrary(definition,Collections.singletonList(new WorkspaceDefinition("saved","Saved",definition.cards,definition.style))));
        WorkspaceLibrary reopened=codec.decode(encoded);assertEquals(.3f,reopened.current.cards.get(0).placements.get("b/b.Main").x,0);
        assertTrue(reopened.saved.get(0).cards.get(0).freeform);
        JSONObject legacy=LegacyWorkspaceFixture.downgrade(new JSONObject(encoded),4);
        for(String root:new String[]{"current"}){JSONObject card=legacy.getJSONObject(root).getJSONArray("cards").getJSONObject(0);card.remove("freeform");card.remove("placements");}
        WorkspaceLibrary migrated=codec.decode(legacy.toString());assertFalse(migrated.current.cards.get(0).freeform);assertEquals("group",migrated.current.cards.get(0).id);assertEquals(2,migrated.current.cards.get(0).apps.size());
        JSONObject broken=new JSONObject(encoded);broken.getJSONObject("current").getJSONArray("cards").getJSONObject(0).getJSONObject("placements").getJSONObject("b/b.Main").put("width",-1);
        assertThrows(IOException.class,()->codec.decode(broken.toString()));
    }
    @Test public void minimizedWindowRestoresWithoutMutatingConfiguration(){StagePresentation p=new StagePresentation();p.minimize(7);assertFalse(p.showsWindow(7));assertTrue(p.showsWindow(8));p.restore(7);assertTrue(p.showsWindow(7));p.minimize(7);p.enterApp(7);assertTrue(p.showsWindow(7));p.leaveApp();assertTrue(p.showsWindow(8));}
    @Test public void freeBoundsStayWithinCardInBothOrientations(){for(int[] size:new int[][]{{2560,1600},{1600,2560}}){StageLayout l=new StageLayout(size[0],size[1],2,false,3,WorkspaceStyle.DEFAULT);StageLayout.Bounds card=l.target(0,0),b=l.freeMember(card,new WindowPlacement(.2f,.1f,.6f,.8f));assertTrue(b.left>=card.left);assertTrue(b.right<=card.right+.1);assertTrue(b.top>card.top);assertTrue(b.bottom<=card.bottom+.1);}}
}
