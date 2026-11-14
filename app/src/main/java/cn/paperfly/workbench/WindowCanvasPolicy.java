package cn.paperfly.workbench;

/** Per-app display compatibility, independent of card geometry, storage and shell privileges. */
final class WindowCanvasPolicy {
    private WindowCanvasPolicy(){}
    static CanvasSize resolve(String manufacturer,String component,CanvasSize pane,int width,int height,int density){
        // Honor's WeChat activity embedding retains the physical display's split bounds on a secondary display.
        // Keep its native coordinate space; the existing view transform still fits it into any workbench card.
        boolean honorWechat="HONOR".equalsIgnoreCase(manufacturer)&&component!=null&&component.startsWith("com.tencent.mm/");
        if(honorWechat&&width>=320&&height>=320&&width<=3200&&height<=3200&&density>=100&&density<=640)
            return CanvasSize.nativeDisplay(width,height,density);
        return pane;
    }
}
