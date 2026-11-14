package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public class CanvasSizeTest {
    @Test public void closingHorizontalOrVerticalMembersKeepsPixelScaleAndDensity(){
        for(boolean vertical:new boolean[]{false,true}){
            int cardW=vertical?1550:2460,cardH=vertical?2460:1550;
            CanvasSize before=CanvasSize.paneWithin(vertical?1550:814,vertical?814:1550,387,cardW,cardH);
            int originalDensity=before.density,unchangedAxis=vertical?before.width:before.height;
            for(int expanded:new int[]{1226,2460}){
                CanvasSize next=CanvasSize.paneWithin(vertical?1550:expanded,vertical?expanded:1550,387,cardW,cardH)
                        .preservingDensityOnExpansion(before.width,before.height,before.density);
                assertEquals(originalDensity,next.density);
                assertEquals(unchangedAxis,vertical?next.width:next.height);
                assertTrue(next.width<=1920&&next.height<=1920);before=next;
            }
        }
    }
    @Test public void shrinkingStillMakesRoomForControls(){
        CanvasSize full=CanvasSize.paneWithin(2460,1550,387,2460,1550);
        CanvasSize small=CanvasSize.paneWithin(500,1550,387,2460,1550)
                .preservingDensityOnExpansion(full.width,full.height,full.density);
        assertTrue(small.density<full.density);assertTrue(small.width*160f/small.density>=360);
    }
    @Test public void sharedProjectionIsEvenAndRetainsFullCardQuality(){
        CanvasSize full=CanvasSize.pane(2460,1550,387),shared=CanvasSize.paneWithin(2460,1550,387,2460,1550);
        assertEquals(full.width,shared.width);assertEquals(full.height,shared.height);assertEquals(full.density,shared.density);
        CanvasSize pane=CanvasSize.paneWithin(813,1551,387,2461,1551);
        assertEquals(0,pane.width%2);assertEquals(0,pane.height%2);
        assertThrows(IllegalArgumentException.class,()->CanvasSize.paneWithin(600,900,300,0,1200));
    }
    @Test public void removingSideCardsKeepsMemberCanvasAcrossOrientationsAndModes(){
        for(boolean full:new boolean[]{false,true})for(int[] screen:new int[][]{{2304,1440},{1440,2304}}){
            StageLayout before=new StageLayout(screen[0],screen[1],2.25f,full,5,cn.paperfly.workbench.model.WorkspaceStyle.DEFAULT);
            StageLayout.Bounds pane=before.target(0,0);
            CanvasSize original=CanvasSize.pane(Math.round(pane.width()),Math.round(pane.height()),360);
            for(int remaining=1;remaining<=4;remaining++){
                StageLayout after=new StageLayout(screen[0],screen[1],2.25f,full,remaining,cn.paperfly.workbench.model.WorkspaceStyle.DEFAULT);
                StageLayout.Bounds next=after.target(0,0);
                CanvasSize current=CanvasSize.pane(Math.round(next.width()),Math.round(next.height()),360);
                assertEquals(original.width,current.width);assertEquals(original.height,current.height);assertEquals(original.density,current.density);
            }
        }
    }
    @Test public void groupPanesKeepEnoughLogicalRoomForControls(){
        CanvasSize landscape=CanvasSize.pane(880,1104,360),portrait=CanvasSize.pane(992,886,360);
        assertTrue(landscape.height*160f/landscape.density>=640);assertTrue(portrait.height*160f/portrait.density>=640);
        assertTrue(landscape.width*160f/landscape.density>=360);assertEquals(880,landscape.width);
    }
    @Test public void fullWorkspaceFillsHeightAndKeepsDeviceDensity(){
        CanvasSize normal=CanvasSize.from(2304,1440,360),full=CanvasSize.workspace(2304,1440,360,455);
        assertEquals(normal.height,full.height);assertEquals(normal.density,full.density);
        assertEquals(1440,full.height*(1849d/full.width),2);
        CanvasSize portrait=CanvasSize.workspace(1440,2304,360,270);
        assertEquals(300,portrait.density);assertEquals(2304,portrait.height*(1170d/portrait.width),3);
    }
    @Test public void singleAppWorkspaceUsesWholeDevice(){
        CanvasSize size=CanvasSize.workspace(2304,1440,360,0);assertEquals(1920,size.width);assertEquals(1200,size.height);
        assertThrows(IllegalArgumentException.class,()->CanvasSize.workspace(1440,2304,360,1440));
    }
    @Test public void rotationSwapsCanvasAxesWithoutChangingDensity(){
        CanvasSize landscape=CanvasSize.from(2304,1440,360),portrait=CanvasSize.from(1440,2304,360);
        assertEquals(1920,landscape.width);assertEquals(1200,landscape.height);
        assertEquals(landscape.width,portrait.height);assertEquals(landscape.height,portrait.width);assertEquals(300,portrait.density);
    }
    @Test public void phonesKeepTheirResolutionAndTallOrientation(){
        CanvasSize size=CanvasSize.from(1080,2400,420);
        assertEquals(864,size.width);assertEquals(1920,size.height);assertEquals(336,size.density);
    }
    @Test public void buffersAreEvenAndBounded(){
        CanvasSize size=CanvasSize.from(1601,2561,400);
        assertEquals(0,size.width%2);assertEquals(0,size.height%2);assertTrue(size.width>=320);assertTrue(size.height<=1920);
        assertThrows(IllegalArgumentException.class,()->CanvasSize.from(0,1440,360));
    }
}
