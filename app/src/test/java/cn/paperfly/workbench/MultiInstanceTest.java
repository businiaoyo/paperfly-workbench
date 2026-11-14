package cn.paperfly.workbench;
import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.WorkspaceCodec;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MultiInstanceTest {
 @Test public void explicitInstancesHaveIndependentIdentityFocusRemovalAndPersistence()throws Exception{
  AppTarget a=new AppTarget("browser/.Main"),b=new AppTarget("12345678-abcd-1234-abcd-123456789abc","browser/.Main");
  WorkspaceCard card=new WorkspaceCard("group",Arrays.asList(a,b),b.id).withFreeform(true);
  WorkspaceDefinition def=new WorkspaceDefinition("current","Two",Collections.singletonList(card),WorkspaceStyle.DEFAULT);
  WorkspaceDraft draft=new WorkspaceDraft(def);CardSession s=new CardSession();s.bind(draft);s.add(1,a.id);s.add(2,b.id);assertEquals(2,s.primary());s.select(1);assertEquals(1,s.primary());assertEquals(a.id,s.card(1).focusedWindowId);
  WorkspaceCodec codec=new WorkspaceCodec();WorkspaceLibrary read=codec.decode(codec.encode(new WorkspaceLibrary(draft.snapshot(),Collections.emptyList())));
  assertEquals(b.id,read.current.cards.get(0).apps.get(1).id);s.remove(1);assertEquals(b.id,draft.snapshot().cards.get(0).apps.get(0).id);assertEquals(2,def.cards.get(0).apps.size());
 }
 @Test public void singletonTaskModesCannotBeAdvertisedAsNewWindows(){assertFalse(InstancePolicy.supports(2));assertFalse(InstancePolicy.supports(3));assertFalse(InstancePolicy.supports(-1));assertTrue(InstancePolicy.supports(0));assertTrue(InstancePolicy.supports(1));assertTrue(InstancePolicy.supports(4));}
 @Test public void identitiesCannotAliasAcrossCards(){AppTarget a=new AppTarget("a/.Main");assertThrows(IllegalArgumentException.class,()->new WorkspaceDefinition("current","",Arrays.asList(WorkspaceCard.single("1",a),WorkspaceCard.single("2",a)),WorkspaceStyle.DEFAULT));}
 @Test public void schemaFiveMigrationRetainsGeometryAndAddsWindowIdentity()throws Exception{
  WorkspaceCard card=WorkspaceCard.single("stable",new AppTarget("a/.Main")).withFreeform(true).withPlacement("a/a.Main",new WindowPlacement(.2f,.1f,.6f,.8f));
  WorkspaceDefinition d=new WorkspaceDefinition("current","Old",Collections.singletonList(card),WorkspaceStyle.DEFAULT);WorkspaceCodec codec=new WorkspaceCodec();
  org.json.JSONObject json=LegacyWorkspaceFixture.downgrade(new org.json.JSONObject(codec.encode(new WorkspaceLibrary(d,Collections.emptyList()))),5);
  json.getJSONObject("current").getJSONArray("cards").getJSONObject(0).getJSONObject("placements").getJSONObject("a/a.Main").remove("order");
  WorkspaceCard migrated=codec.decode(json.toString()).current.cards.get(0);assertEquals("stable",migrated.id);assertEquals("a/a.Main",migrated.focusedWindowId);assertEquals(.6f,migrated.placements.get("a/a.Main").width,0);assertEquals(0,migrated.placements.get("a/a.Main").order);
 }
}
