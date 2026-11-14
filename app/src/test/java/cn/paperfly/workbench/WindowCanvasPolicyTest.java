package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowCanvasPolicyTest {
    private final CanvasSize pane=CanvasSize.pane(1766,1104,360);
    @Test public void honorWechatUsesNativeBoundsAndDensity(){CanvasSize s=WindowCanvasPolicy.resolve("HONOR","com.tencent.mm/.ui.LauncherUI",pane,2304,1440,360);assertEquals(2304,s.width);assertEquals(1440,s.height);assertEquals(360,s.density);}
    @Test public void portraitFollowsDeviceRatherThanCard(){CanvasSize s=WindowCanvasPolicy.resolve("honor","com.tencent.mm/.ui.LauncherUI",pane,1440,2304,360);assertEquals(1440,s.width);assertEquals(2304,s.height);}
    @Test public void unrelatedAppsAndManufacturersKeepPaneSize(){assertSame(pane,WindowCanvasPolicy.resolve("HONOR","com.tencent.mm.fake/.Main",pane,2304,1440,360));assertSame(pane,WindowCanvasPolicy.resolve("vivo","com.tencent.mm/.Main",pane,2304,1440,360));}
    @Test public void unsupportedNativeDimensionsKeepValidPane(){assertSame(pane,WindowCanvasPolicy.resolve("HONOR","com.tencent.mm/.Main",pane,4096,2560,360));}
}
