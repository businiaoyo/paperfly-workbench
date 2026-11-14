package cn.paperfly.workbench;

import cn.paperfly.workbench.model.WorkspaceStyle;
import org.junit.Test;
import static org.junit.Assert.*;

public class StagePerspectiveTest {
    // Independent camera projection of the public View properties produced by the geometry policy.
    private float[] point(StageLayout.CardPose p, float x, float y) {
        double a=Math.toRadians(p.yaw),dx=(x-p.pivotX)*p.scale,dy=(y-p.pivotY)*p.scale;
        double depth=1+dx*Math.sin(a)/p.cameraDistance;
        return new float[]{(float)(p.pivotX+p.translationX+dx*Math.cos(a)/depth),
                (float)(p.pivotY+p.translationY+dy/depth)};
    }

    @Test public void reproducesThreeMeasuredIqooQuadsWithoutRollingVerticalEdges() {
        // Native dumpsys, 2026-11-11: TL, TR, BR, BL in a 2304 x 1440 scene.
        float[][] reference={{145,89,465,138,465,363,145,335},
                {145,373,465,399,465,624,145,619},
                {145,657,465,659,465,884,145,903}};
        float scale=246f/1440;
        for(float[] expected:reference) {
            StageLayout.CardPose p=new StageLayout.CardPose();
            p.set(305-2304*scale/2,expected[1],scale,2304,StageLayout.SIDE_YAW,678);
            float[][] actual={point(p,0,0),point(p,2304,0),point(p,2304,1440),point(p,0,1440)};
            for(int i=0;i<4;i++)for(int axis=0;axis<2;axis++)assertEquals(expected[2*i+axis],actual[i][axis],2f);
            assertEquals(actual[0][0],actual[3][0],.001f);
            assertEquals(actual[1][0],actual[2][0],.001f);
        }
    }

    @Test public void mainWindowHasExactUnprojectedBoundsAtEitherScale() {
        for(float scale:new float[]{.24f,1.28f}) {
            StageLayout.CardPose p=new StageLayout.CardPose();p.set(556,22,scale,1920,0,948);
            assertArrayEquals(new float[]{556,22},point(p,0,0),.001f);
            assertArrayEquals(new float[]{556+1920*scale,22+1342*scale},point(p,1920,1342),.001f);
        }
    }

    @Test public void rightRailIsExactMirrorThroughoutTransition() {
        for(float yaw:new float[]{0,.01f,7,14,28}) {
            StageLayout.CardPose left=new StageLayout.CardPose(),right=new StageLayout.CardPose();
            left.set(33,351,.255f,1920,yaw,948);
            right.set(3040-33-1920*.255f,351,.255f,1920,-yaw,948);
            for(float x:new float[]{0,960,1920})for(float y:new float[]{0,671,1342}) {
                float[] a=point(left,x,y),b=point(right,1920-x,y);
                assertEquals(3040-a[0],b[0],.002f);assertEquals(a[1],b[1],.002f);
            }
        }
    }

    @Test public void addAndAppShareProjectionRegardlessOfLogicalCanvasSize() {
        StageLayout.CardPose app=new StageLayout.CardPose(),add=new StageLayout.CardPose();
        app.set(33,1176,.25f,1920,28,948);add.set(33,1176,1,480,28,948);
        for(float x:new float[]{0,960,1920})for(float y:new float[]{0,672,1344})
            assertArrayEquals(point(app,x,y),point(add,x*.25f,y*.25f),.001f);
    }

    @Test public void visibleCardsAndScrolledAddStayInsideRailInBothModesAndOrientations() {
        for(boolean portrait:new boolean[]{false,true})for(boolean full:new boolean[]{false,true}) {
            StageLayout layout=new StageLayout(portrait?1904:3040,portrait?2900:1764,2.75f,full,5,WorkspaceStyle.DEFAULT);
            for(int slot=1;slot<=5;slot++) {
                StageLayout.Bounds b=layout.target(slot,slot==5?layout.clampOffset(Float.MAX_VALUE):0);
                StageLayout.CardPose p=new StageLayout.CardPose();p.set(b.left,b.top,b.width()/1920,1920,28,layout.perspectiveHorizon());
                for(float x:new float[]{0,1920})for(float y:new float[]{0,b.height()*1920/b.width()}) {
                    float[] corner=point(p,x,y);
                    assertTrue(corner[0]>=b.left-.01f&&corner[0]<=b.right+.01f);
                    assertTrue(corner[1]>=layout.railTop()-.01f&&corner[1]<=layout.railBottom()+.01f);
                }
            }
        }
    }
}
