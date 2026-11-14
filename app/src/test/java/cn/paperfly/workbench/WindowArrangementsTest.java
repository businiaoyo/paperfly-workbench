package cn.paperfly.workbench;
import cn.paperfly.workbench.model.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowArrangementsTest {
    @Test public void tilePresetsStayInBoundsWithoutOverlapInEitherOrientation(){
        for(boolean horizontal:new boolean[]{true,false})for(int count=1;count<=36;count++)for(int i=0;i<count;i++){
            WindowPlacement a=WindowArrangements.tile(i,count,horizontal);assertTrue(a.x+a.width<=1.0001f);assertTrue(a.y+a.height<=1.0001f);
            for(int j=0;j<i;j++){WindowPlacement b=WindowArrangements.tile(j,count,horizontal);assertTrue(a.x+a.width<=b.x+.0001f||b.x+b.width<=a.x+.0001f||a.y+a.height<=b.y+.0001f||b.y+b.height<=a.y+.0001f);}
        }
    }
    @Test public void halvesMeetAtMiddle(){WindowPlacement left=WindowArrangements.half(false,true),right=WindowArrangements.half(true,true);assertEquals(left.width,right.x,0);assertEquals(1,right.x+right.width,0);WindowPlacement bottom=WindowArrangements.half(true,false);assertEquals(.5f,bottom.y,0);assertEquals(1,bottom.width,0);}
}
