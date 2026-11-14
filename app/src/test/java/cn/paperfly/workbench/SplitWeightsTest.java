package cn.paperfly.workbench;

import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class SplitWeightsTest {
    private final List<AppTarget> apps=Arrays.asList(new AppTarget("a/.Main"),new AppTarget("b/.Main"),new AppTarget("missing/.Main"),new AppTarget("f082f015-b5dc-4ade-8df6-6f63439a334b","a/.Main"));
    private WorkspaceCard card(){return new WorkspaceCard("group",apps,apps.get(1).id).withSplit(Arrays.asList(.3f,.2f,.1f,.4f),0);}
    private WorkspaceDefinition definition(){return new WorkspaceDefinition("current","Test",Collections.singletonList(card()),WorkspaceStyle.DEFAULT);}
    private static final class Disk implements WorkspaceStorage {String value;boolean fail;int writes;public String read(){return value;}public String legacyComponents(){return "[]";}public void write(String value)throws IOException{if(fail)throw new IOException("full");this.value=value;writes++;}}

    @Test public void everyDividerChangesOnlyItsTwoNeighborsAndKeepsTotalArea(){
        StageLayout.Bounds rect=new StageLayout.Bounds(100,200,2500,1800);List<Float> weights=card().weights;
        for(boolean horizontal:new boolean[]{true,false})for(int index=0;index<3;index++)for(float shift:new float[]{-5000,-80,80,5000}){
            float point=PaneLayout.divider(rect,weights,index,horizontal,8);
            List<Float> moved=PaneLayout.moveDivider(rect,weights,index,point+shift,horizontal,8,120);
            float total=0;for(int i=0;i<moved.size();i++){total+=moved.get(i);assertTrue(moved.get(i)>0);if(i!=index&&i!=index+1)assertEquals(weights.get(i),moved.get(i));}
            assertEquals(1,total,.000001f);assertEquals(weights.get(index)+weights.get(index+1),moved.get(index)+moved.get(index+1),.000001f);
            for(int i=0;i<3;i++)if(i!=index)assertEquals(PaneLayout.divider(rect,weights,i,horizontal,8),PaneLayout.divider(rect,moved,i,horizontal,8),.001f);
            StageLayout.Bounds last=PaneLayout.member(rect,3,moved,horizontal?1:2,8);assertEquals(rect.right,last.right,.001f);assertEquals(rect.bottom,last.bottom,.001f);
        }
    }
    @Test public void dividerDoesNotJumpOnInitialGrab(){StageLayout.Bounds rect=new StageLayout.Bounds(20,40,1020,1440);for(int i=0;i<3;i++){
        List<Float> moved=PaneLayout.moveDivider(rect,card().weights,i,PaneLayout.divider(rect,card().weights,i,true,4),true,4,100);
        for(int j=0;j<4;j++)assertEquals(card().weights.get(j),moved.get(j),.000001f);}}
    @Test public void reorderingFocusAndFreeformKeepWeightsAttachedToStableWindows(){WorkspaceCard original=card();List<AppTarget> reversed=new ArrayList<>(apps);Collections.reverse(reversed);
        WorkspaceCard moved=original.withApps(reversed,apps.get(3).id).withFocus(apps.get(0).id).withFreeform(true).withPlacement(apps.get(0).id,WindowPlacement.initial(0,4));
        assertEquals(original.paneWeights,moved.paneWeights);assertEquals(original.id,moved.id);assertEquals(.4f,moved.weights.get(0),.000001f);}
    @Test public void joiningGroupsPreservesRelativeSharesInBothGroups(){WorkspaceCard first=new WorkspaceCard("g1",apps.subList(0,2),apps.get(0).id,.7f,0),second=new WorkspaceCard("g2",apps.subList(2,4),apps.get(2).id,.3f,0);
        WorkspaceDraft draft=new WorkspaceDraft(new WorkspaceDefinition("current","",Arrays.asList(first,second),WorkspaceStyle.DEFAULT));CardSession session=new CardSession();session.bind(draft);for(int i=0;i<4;i++)session.add(i+1,apps.get(i).id);session.mergeIntoPrimary(3);
        WorkspaceCard merged=session.card(1);assertEquals(Arrays.asList(.35f,.15f,.15f,.35f),merged.weights);assertEquals("g1",merged.id);
        session.detach(4);WorkspaceCard remaining=session.card(1);assertEquals(.35/.15,remaining.weights.get(0)/remaining.weights.get(1),.00001);assertEquals(1f,session.card(4).weights.get(0),0);}
    @Test public void appAddedAfterCustomRatiosGetsShareWithoutResettingExistingPairs(){WorkspaceCard three=card().withApps(apps.subList(0,3),apps.get(0).id);float ratio=three.weights.get(1)/three.weights.get(2);
        WorkspaceCard four=three.withApps(apps,apps.get(0).id);assertEquals(.25f,four.weights.get(3),.000001f);assertEquals(ratio,four.weights.get(1)/four.weights.get(2),.000001f);}
    @Test public void allLegacySchemasMigrateWithoutLosingStableIdentityFocusOrSavedRecipes()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();List<AppTarget> legacyApps=apps.subList(0,3);WorkspaceCard legacy=new WorkspaceCard("kept",legacyApps,apps.get(1).id,.6f,2);
        WorkspaceDefinition original=new WorkspaceDefinition("current","old",Collections.singletonList(legacy),WorkspaceStyle.DEFAULT);
        for(int version=1;version<=7;version++){
            JSONObject root=LegacyWorkspaceFixture.downgrade(new JSONObject(codec.encode(new WorkspaceLibrary(original,Collections.singletonList(original.named("saved","Saved"))))),version);
            Disk disk=new Disk();disk.value=root.toString();WorkspaceLibrary migrated=new WorkspaceRepository(disk).load();
            assertEquals(8,new JSONObject(disk.value).getInt("schemaVersion"));assertEquals("kept",migrated.current.cards.get(0).id);assertEquals(apps.get(1).id,migrated.current.cards.get(0).focusedWindowId);
            assertEquals(version==1?.5f:.6f,migrated.current.cards.get(0).weights.get(0),.00001f);assertEquals(3,migrated.current.cards.get(0).apps.size());assertEquals("saved",migrated.saved.get(0).id);
            assertEquals(migrated.current.cards.get(0).paneWeights,new WorkspaceRepository(disk).load().current.cards.get(0).paneWeights);
        }
    }
    @Test public void migrationFailureAndCustomRatioWriteFailureBothKeepSourceAndRetry()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();Disk disk=new Disk();disk.value=LegacyWorkspaceFixture.downgrade(new JSONObject(codec.encode(new WorkspaceLibrary(definition(),Collections.emptyList()))),7).toString();String old=disk.value;
        WorkspaceRepository repo=new WorkspaceRepository(disk);disk.fail=true;assertThrows(IOException.class,repo::load);assertEquals(old,disk.value);assertEquals(0,disk.writes);
        disk.fail=false;WorkspaceDefinition loaded=repo.load().current;String migrated=disk.value;WorkspaceDefinition changed=loaded.withCards(Collections.singletonList(loaded.cards.get(0).withSplit(Arrays.asList(.25f,.2f,.15f,.4f),1)));
        disk.fail=true;assertThrows(IOException.class,()->repo.saveCurrent(changed));assertEquals(migrated,disk.value);assertSame(loaded,repo.load().current);
        disk.fail=false;repo.saveCurrent(changed);assertEquals(changed.cards.get(0).paneWeights,new WorkspaceRepository(disk).load().current.cards.get(0).paneWeights);
    }
    @Test public void malformedWeightsAndFutureSchemaNeverOverwriteGoodSource()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();String valid=codec.encode(new WorkspaceLibrary(definition(),Collections.emptyList()));
        for(int kind=0;kind<7;kind++){JSONObject json=new JSONObject(valid);JSONObject weights=json.getJSONObject("current").getJSONArray("cards").getJSONObject(0).getJSONObject("paneWeights");
            if(kind==0)weights.remove(apps.get(0).id);if(kind==1)weights.put("unknown",.1);if(kind==2)weights.put(apps.get(0).id,0);if(kind==3)weights.put(apps.get(0).id,-.1);if(kind==4)weights.put(apps.get(0).id,.5);if(kind==5)weights.put(apps.get(0).id,"NaN");if(kind==6)json.put("schemaVersion",99);
            Disk disk=new Disk();disk.value=json.toString();String original=disk.value;assertThrows(IOException.class,()->new WorkspaceRepository(disk).load());assertEquals(original,disk.value);assertEquals(0,disk.writes);
        }
    }
    @Test public void customWeightsSurviveRestartAndDoNotMutateExplicitlySavedCombination()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();Disk disk=new Disk();disk.value=codec.encode(new WorkspaceLibrary(definition(),Collections.emptyList()));WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition saved=repo.saveAs(repo.load().current,"Saved");
        WorkspaceCard changed=card().withSplit(Arrays.asList(.3f,.15f,.15f,.4f),2);repo.saveCurrent(definition().withCards(Collections.singletonList(changed)));WorkspaceLibrary reopened=new WorkspaceRepository(disk).load();
        assertEquals(changed.paneWeights,reopened.current.cards.get(0).paneWeights);assertEquals(card().paneWeights,reopened.saved.get(0).cards.get(0).paneWeights);assertEquals(saved.id,reopened.saved.get(0).id);
    }
}
