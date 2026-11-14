package cn.paperfly.workbench;

import cn.paperfly.workbench.model.WorkspaceStyle;
import org.junit.Test;
import static org.junit.Assert.*;

public class StageLayoutTest {
    @Test public void independentPaneResizeKeepsItsTopAndBottomAnchored(){
        StageLayout.CardPose pose=new StageLayout.CardPose();
        for(float sx:new float[]{.25f,.5f,.8f,1f})for(float sy:new float[]{.3f,.7f,1f}){
            pose.set(400,120,sx,sy,2400,0,900);
            assertEquals(400,pose.translationX+pose.pivotX*(1-pose.scale),.001f);
            assertEquals(120,pose.translationY+pose.pivotY*(1-pose.scaleY),.001f);
            assertEquals(120+1600*sy,pose.translationY+pose.pivotY*(1-pose.scaleY)+1600*pose.scaleY,.001f);
        }
    }
    @Test public void independentResizeKeepsTheSharedPerspectiveHorizon(){
        StageLayout.CardPose pose=new StageLayout.CardPose();
        for(float yaw:new float[]{-StageLayout.SIDE_YAW,0,StageLayout.SIDE_YAW}){
            pose.set(120,400,.3f,.65f,1800,yaw,950);
            assertEquals(950,pose.translationY+pose.pivotY,.001f);
            pose.set(120,400,.3f,1800,yaw,950);
            assertEquals(pose.scale,pose.scaleY,0);
        }
    }
    @Test public void ordinaryDockBaselineFillsSplitAreaAndPreservesOnlyTheDividerGap(){
        for(boolean portrait:new boolean[]{false,true})for(int count:new int[]{2,3})for(float weight:new float[]{.3f,.5f,.6f}){
            StageLayout normal=scene(portrait,false,4);normal.dockInset=Math.round(StageLayout.DOCK_INSET_DP*2.25f);
            StageLayout.Bounds card=normal.target(0,0);boolean horizontal=!portrait;
            StageLayout.Bounds previous=null;
            for(int i=0;i<count;i++){
                StageLayout.Bounds pane=normal.member(card,i,count,weight,0);
                CanvasSize size=CanvasSize.pane(Math.round(pane.width()),Math.round(pane.height()),360);
                StageLayout.Bounds shown=StageLayout.fitCanvas(StageLayout.mapMember(card,card,pane),size.width,size.height);
                assertEquals(pane.left,shown.left,2);assertEquals(pane.right,shown.right,2);
                // Even-sized canvases can leave a few pixels at the long edge of a narrow pane.
                assertEquals(pane.top,shown.top,3);assertEquals(pane.bottom,shown.bottom,3);
                if(previous!=null)assertEquals(9,horizontal?shown.left-previous.right:shown.top-previous.bottom,3);
                previous=shown;
            }
        }
    }

    @Test public void sharedCardTransformKeepsPanesAdjacentAcrossPresentationAndSidebar(){
        for(boolean portrait:new boolean[]{false,true})for(int axis:new int[]{0,1,2}){
            StageLayout reference=scene(portrait,false,4);reference.dockInset=140;
            StageLayout.Bounds original=reference.target(0,0);
            for(StageLayout.Bounds target:new StageLayout.Bounds[]{scene(portrait,true,4).target(0,0),reference.target(1,0),new StageLayout.Bounds(0,0,2400,900)}){
                StageLayout.Bounds a=StageLayout.mapMember(target,original,reference.member(original,0,3,.5f,axis));
                StageLayout.Bounds b=StageLayout.mapMember(target,original,reference.member(original,1,3,.5f,axis));
                float scale=Math.min(target.width()/original.width(),target.height()/original.height());
                boolean horizontal=axis==1||axis==0&&!portrait;
                assertEquals(9*scale,horizontal?b.left-a.right:b.top-a.bottom,.001f);
                assertTrue(a.left>=target.left-.01f&&a.top>=target.top-.01f&&b.right<=target.right+.01f&&b.bottom<=target.bottom+.01f);
            }
        }
    }

    @Test public void dividerPointerMatchesMemberEdgeAndClampsBothOrientations(){
        StageLayout.Bounds card=new StageLayout.Bounds(100,200,1500,2100);
        for(boolean horizontal:new boolean[]{false,true})for(int count:new int[]{2,3,5})for(float weight:new float[]{.2f,.5f,.8f}){
            StageLayout.Bounds first=PaneLayout.member(card,0,count,weight,horizontal?1:2,8);
            assertEquals(weight,PaneLayout.weightAt(card,first.right+4,first.bottom+4,count,horizontal,8),.0001f);
            assertEquals(.2f,PaneLayout.weightAt(card,-100,-100,count,horizontal,8),0);
            assertEquals(.8f,PaneLayout.weightAt(card,4000,4000,count,horizontal,8),0);
        }
    }

    private StageLayout scene(boolean portrait,boolean full,int count) {
        int w=portrait?1440:2304,h=portrait?2304:1440;
        int left=full?StageLayout.fullPrimaryLeft(w,h,2.25f,WorkspaceStyle.DEFAULT):0;
        CanvasSize canvas=CanvasSize.workspace(w,h,360,left);
        return new StageLayout(w,full?h:h-120,2.25f,full,count,WorkspaceStyle.DEFAULT);
    }

    @Test public void fullscreenReachesTopBottomRightAndPreservesRail() {
        for(boolean portrait:new boolean[]{false,true}) {
            StageLayout layout=scene(portrait,true,9);StageLayout.Bounds main=layout.target(0,0);
            assertEquals(portrait?270:455,main.left,0);assertEquals(0,main.top,0);
            assertEquals(portrait?1440:2304,main.right,0);assertEquals(portrait?2304:1440,main.bottom,0);
            assertTrue(main.left>layout.railWidth());
        }
    }

    @Test public void lastAddCardIsReachableWithZeroOneOrManyAppsInEitherMode() {
        for(boolean portrait:new boolean[]{false,true})for(boolean full:new boolean[]{false,true})for(int count:new int[]{0,1,9,24}) {
            StageLayout layout=scene(portrait,full,count);
            StageLayout.Bounds add=layout.target(Math.max(1,count),layout.clampOffset(Float.MAX_VALUE));
            assertTrue(add.top>=layout.railTop()-1);assertTrue(add.bottom<=layout.railBottom()+1);assertTrue(add.right>add.left);
            assertEquals(Math.max(1,count),layout.railEntries());
        }
    }

    @Test public void scrollChangesOnlySidePositionsNotPrimaryBounds() {
        StageLayout layout=scene(false,false,9);StageLayout.Bounds before=layout.target(0,0),after=layout.target(0,500);
        assertEquals(before.left,after.left,0);assertEquals(before.top,after.top,0);
        assertEquals(500,layout.target(1,0).top-layout.target(1,500).top,.001);
        assertEquals(0,layout.clampOffset(-20),0);
    }

    @Test public void styleChangesPlacementAndCanvasUsingTheSameRailPolicy() {
        WorkspaceStyle style=new WorkspaceStyle(0,0,0,90,150,.19f);
        int left=StageLayout.fullPrimaryLeft(2304,1440,2.25f,style);
        StageLayout layout=new StageLayout(2304,1440,2.25f,true,6,style);
        assertEquals(365,left);assertEquals(left,layout.target(0,0).left,0);
        CanvasSize canvas=CanvasSize.workspace(2304,1440,360,left);
        assertEquals(1440,canvas.height*((2304d-left)/canvas.width),3);
    }

    @Test public void normalMainUsesFullHeightWithoutReservingTheRailControlsRow(){
        for(boolean portrait:new boolean[]{false,true}){
            StageLayout layout=scene(portrait,false,5);StageLayout.Bounds main=layout.target(0,0);
            assertEquals(18,main.top,0);
            assertEquals((portrait?2304:1440)-120-18,main.bottom,0);
            assertTrue(main.left>layout.railWidth());
            assertTrue(main.top<layout.railTop());
        }
    }

    @Test public void fourPreviewsFitAndFifthAddCardRemainsReachable(){
        for(boolean portrait:new boolean[]{false,true})for(boolean full:new boolean[]{false,true}){
            StageLayout layout=scene(portrait,full,5);float bottom=layout.railTop();
            for(int slot=1;slot<=4;slot++){
                StageLayout.Bounds card=layout.target(slot,0);
                assertTrue(card.top>=bottom);assertTrue(card.bottom<=layout.railBottom());
                assertTrue(card.width()>60);assertTrue(card.height()>60);bottom=card.bottom;
            }
            StageLayout.Bounds add=layout.target(5,layout.clampOffset(Float.MAX_VALUE));
            assertTrue(add.top>=layout.railTop());assertTrue(add.bottom<=layout.railBottom());
        }
    }

    @Test public void rightRailMirrorsGeometryWithoutCoveringMain(){
        WorkspaceStyle base=WorkspaceStyle.DEFAULT;
        WorkspaceStyle right=new WorkspaceStyle(base.backgroundStart,base.backgroundMiddle,base.backgroundEnd,112,190,.19f,true,false,1f,"",0,true);
        StageLayout left=new StageLayout(3040,1764,2.75f,false,5,base),mirrored=new StageLayout(3040,1764,2.75f,false,5,right);
        for(int slot=0;slot<=5;slot++){
            StageLayout.Bounds a=left.target(slot,0),b=mirrored.target(slot,0);
            assertEquals(3040-a.right,b.left,.001f);assertEquals(a.top,b.top,.001f);
        }
        assertTrue(mirrored.target(1,0).left>mirrored.target(0,0).right);
    }

    @Test public void singleAppUsesWholeSceneDespiteRailSideAspectScaleAndSplitPreferences(){
        WorkspaceStyle base=WorkspaceStyle.DEFAULT;
        for(boolean portrait:new boolean[]{false,true})for(boolean right:new boolean[]{false,true})for(boolean full:new boolean[]{false,true}){
            WorkspaceStyle style=new WorkspaceStyle(base.backgroundStart,base.backgroundMiddle,base.backgroundEnd,112,190,.19f,right,true,.8f,"",1,true);
            int width=portrait?1440:2304,height=portrait?2304:1440;
            StageLayout layout=new StageLayout(width,height,2.25f,full,9,style);
            StageLayout.Bounds app=layout.fullscreenApp();
            assertEquals(0,app.left,0);assertEquals(0,app.top,0);assertEquals(width,app.right,0);assertEquals(height,app.bottom,0);
        }
    }

    @Test public void existingCanvasFitsEveryPresentationAndReturnsToIdenticalBounds(){
        for(boolean portrait:new boolean[]{false,true})for(int members:new int[]{1,2,3}){
            int width=portrait?1440:2304,height=portrait?2304:1440;
            // App canvases are derived once from the normal safe area, never from the enlarged scene.
            StageLayout normal=new StageLayout(width,height-120,2.25f,false,6,WorkspaceStyle.DEFAULT);
            StageLayout immersive=new StageLayout(width,height,2.25f,true,6,WorkspaceStyle.DEFAULT);
            for(int member=0;member<members;member++){
                StageLayout.Bounds normalPane=normal.member(normal.target(0,0),member,members,.6f,0);
                CanvasSize canvas=CanvasSize.pane(Math.round(normalPane.width()),Math.round(normalPane.height()),360);
                StageLayout.Bounds before=StageLayout.fitCanvas(normalPane,canvas.width,canvas.height);
                StageLayout.Bounds workbenchPane=immersive.member(immersive.target(0,0),member,members,.6f,0);
                for(StageLayout.Bounds target:new StageLayout.Bounds[]{workbenchPane,immersive.fullscreenApp()}){
                    StageLayout.Bounds shown=StageLayout.fitCanvas(target,canvas.width,canvas.height);
                    assertEquals(canvas.width/(float)canvas.height,shown.width()/shown.height(),.001f);
                    assertTrue(shown.left>=target.left-.01f);assertTrue(shown.top>=target.top-.01f);
                    assertTrue(shown.right<=target.right+.01f);assertTrue(shown.bottom<=target.bottom+.01f);
                    assertTrue(Math.abs(shown.width()-target.width())<.01f||Math.abs(shown.height()-target.height())<.01f);
                }
                StageLayout.Bounds after=StageLayout.fitCanvas(normalPane,canvas.width,canvas.height);
                assertEquals(before.left,after.left,0);assertEquals(before.top,after.top,0);
                assertEquals(before.right,after.right,0);assertEquals(before.bottom,after.bottom,0);
            }
        }
    }
}
