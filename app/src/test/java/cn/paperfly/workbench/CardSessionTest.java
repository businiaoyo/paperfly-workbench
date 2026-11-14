package cn.paperfly.workbench;
import cn.paperfly.workbench.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CardSessionTest {
    @Test public void closeMiddleInstancePreservesFocusWeightsSavedGroupAndRemainingIdentities(){
        AppTarget a=new AppTarget("a/.Main"),b=new AppTarget("00000000-0000-0000-0000-000000000001","a/.Main"),c=new AppTarget("c/.Main"),side=new AppTarget("side/.Main");
        WorkspaceCard group=new WorkspaceCard("split",Arrays.asList(a,b,c),c.id).withSplit(Arrays.asList(.4f,.1f,.5f),1);
        WorkspaceDefinition saved=new WorkspaceDefinition("current","test",Arrays.asList(group,WorkspaceCard.single("side",side)),WorkspaceStyle.DEFAULT);
        WorkspaceDraft d=new WorkspaceDraft(saved);CardSession s=new CardSession();s.bind(d);s.add(1,a.id);s.add(2,b.id);s.add(3,c.id);s.add(4,side.id);
        s.remove(2);
        WorkspaceCard remaining=d.snapshot().cards.get(0);
        assertEquals(Arrays.asList(a,c),remaining.apps);assertEquals("split",remaining.id);assertEquals(3,s.primary());
        assertEquals(.4f/.9f,remaining.weights.get(0),.00001);assertEquals(.5f/.9f,remaining.weights.get(1),.00001);
        assertSame(group,saved.cards.get(0));assertEquals(3,saved.cards.get(0).apps.size());assertEquals(1,s.slotOf(4));
        s.remove(2);assertSame(remaining,d.snapshot().cards.get(0));
        s.remove(3);assertEquals(1,s.primary());assertEquals(Collections.singletonList(a),d.snapshot().cards.get(0).apps);assertEquals(1, d.snapshot().cards.get(0).weights.get(0),0);
        s.remove(1);assertEquals(4,s.primary());assertEquals("side",d.snapshot().cards.get(0).id);assertEquals(1,s.size());
        s.remove(4);assertEquals(-1,s.primary());assertEquals(0,s.size());assertTrue(d.snapshot().cards.isEmpty());
    }
    private WorkspaceDraft draft(){List<WorkspaceCard> cards=new ArrayList<>();for(String p:new String[]{"a","b","c","missing"})cards.add(WorkspaceCard.single(p,new AppTarget(p+"/.Main")));
        return new WorkspaceDraft(new WorkspaceDefinition("current","test",cards,WorkspaceStyle.DEFAULT));}
    @Test public void mergeSwitchFocusAndSplitPreserveAppsAndIndependentOtherCards(){
        WorkspaceDraft draft=draft();CardSession s=new CardSession();s.bind(draft);s.add(1,"a/.Main");s.add(2,"b/.Main");s.add(3,"c/.Main");
        s.mergeIntoPrimary(2);assertEquals(2,s.size());assertEquals(0,s.slotOf(2));assertEquals(1,s.representative(2));
        assertEquals(2,draft.snapshot().cards.get(0).apps.size());assertEquals("missing",draft.snapshot().cards.get(2).id);
        s.select(2);assertEquals(2,s.primary());assertEquals(0,s.slotOf(1));
        s.select(3);assertEquals(3,s.primary());assertEquals(1,s.slotOf(1));assertEquals(1,s.slotOf(2));
        s.select(1);s.splitPrimary();assertEquals(3,s.size());assertEquals(4,draft.snapshot().cards.size());assertEquals("a",draft.snapshot().cards.get(0).id);
    }
    @Test public void removingFocusedMemberKeepsRestOfGroupAndFallsBackFocus(){
        WorkspaceDraft draft=draft();CardSession s=new CardSession();s.bind(draft);s.add(1,"a/.Main");s.add(2,"b/.Main");s.mergeIntoPrimary(2);s.select(2);s.remove(2);
        assertEquals(1,s.primary());assertEquals(1,s.size());assertEquals(3,draft.snapshot().cards.size());assertEquals(1,draft.snapshot().cards.get(0).apps.size());
    }
    @Test public void groupingDoesNotChangePreviouslySavedSnapshot(){WorkspaceDraft d=draft();WorkspaceDefinition before=d.snapshot();CardSession s=new CardSession();s.bind(d);s.add(1,"a/.Main");s.add(2,"b/.Main");s.mergeIntoPrimary(2);s.splitOptions(.7f,2);
        assertEquals(4,before.cards.size());assertEquals(1,before.cards.get(0).apps.size());assertEquals(.7f,d.snapshot().cards.get(0).firstWeight,0);assertEquals(2,d.snapshot().cards.get(0).axis);}
    @Test public void paneWeightsTileParentWithoutOverlapInBothDirections(){
        StageLayout.Bounds parent=new StageLayout.Bounds(10,20,1010,820);
        for(int axis:new int[]{1,2}){
            StageLayout.Bounds a=PaneLayout.member(parent,0,3,.6f,axis,4),b=PaneLayout.member(parent,1,3,.6f,axis,4),c=PaneLayout.member(parent,2,3,.6f,axis,4);
            if(axis==1){assertEquals(parent.left,a.left,0);assertEquals(4,b.left-a.right,.001);assertEquals(4,c.left-b.right,.001);assertEquals(parent.right,c.right,.001);assertEquals(3,a.width()/b.width(),.001);}
            else{assertEquals(parent.top,a.top,0);assertEquals(4,b.top-a.bottom,.001);assertEquals(4,c.top-b.bottom,.001);assertEquals(parent.bottom,c.bottom,.001);}
        }
    }
}
