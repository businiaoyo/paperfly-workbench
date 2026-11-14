package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RailLayoutTest {
    @Test public void smallListCentersAndCannotScroll(){assertEquals(100,RailLayout.itemTop(0,2,150,500,100),.01);assertEquals(0,RailLayout.maxOffset(2,150,500),.01);}
    @Test public void longListRetainsReadableSizeAndCanReachLastItem(){float max=RailLayout.maxOffset(11,140,560);assertEquals(980,max,.01);assertEquals(420,RailLayout.itemTop(10,11,140,560,max),.01);}
    @Test public void removingAtEndClampsScrollInsteadOfLosingAllWindows(){assertEquals(140,RailLayout.clamp(800,5,140,560),.01);assertEquals(0,RailLayout.clamp(800,1,140,560),.01);}
    @Test public void negativeOrExcessScrollStaysWithinBounds(){assertEquals(0,RailLayout.clamp(-80,8,140,560),.01);assertEquals(560,RailLayout.clamp(900,8,140,560),.01);}
}
