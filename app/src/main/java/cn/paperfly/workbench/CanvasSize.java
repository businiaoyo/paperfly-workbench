package cn.paperfly.workbench;

/** An even-sized application canvas that follows device orientation, not main/side swaps. */
final class CanvasSize {
    final int width,height,density;
    private CanvasSize(int width,int height,int density){this.width=width;this.height=height;this.density=density;}
    static CanvasSize nativeDisplay(int width,int height,int density){return new CanvasSize(width,height,density);}
    static CanvasSize from(int width,int height,int density){
        if(width<=0||height<=0||density<=0)throw new IllegalArgumentException("显示器尺寸无效");
        float scale=Math.min(1f,1920f/Math.max(width,height));
        return new CanvasSize(Math.max(320,Math.round(width*scale/2)*2),Math.max(320,Math.round(height*scale/2)*2),Math.max(100,Math.min(640,Math.round(density*scale))));
    }
    static CanvasSize pane(int width,int height,int density){
        CanvasSize size=from(width,height,density);
        int comfortable=Math.max(100,Math.min(size.density,Math.min(size.width*160/360,size.height*160/640)));
        return new CanvasSize(size.width,size.height,comfortable);
    }
    /** Members share their card's pixel scale so removing a neighbour only expands the viewport. */
    static CanvasSize paneWithin(int width,int height,int density,int cardWidth,int cardHeight){
        if(width<=0||height<=0||density<=0||cardWidth<=0||cardHeight<=0)throw new IllegalArgumentException("显示器尺寸无效");
        float scale=Math.min(1f,1920f/Math.max(Math.max(cardWidth,cardHeight),Math.max(width,height)));
        int w=Math.max(320,Math.round(width*scale/2)*2),h=Math.max(320,Math.round(height*scale/2)*2);
        int dpi=Math.max(100,Math.min(640,Math.round(density*scale)));
        return new CanvasSize(w,h,Math.max(100,Math.min(dpi,Math.min(w*160/360,h*160/640))));
    }
    /** Increasing space doesn't require a density change (which relaunches many Android apps). */
    CanvasSize preservingDensityOnExpansion(int oldWidth,int oldHeight,int oldDensity){
        return width>=oldWidth&&height>=oldHeight&&density>oldDensity&&oldDensity>=100
                ?new CanvasSize(width,height,oldDensity):this;
    }
    static CanvasSize workspace(int width,int height,int density,int primaryLeft){
        if(primaryLeft<0||primaryLeft>=width)throw new IllegalArgumentException("主窗口区域无效");
        CanvasSize device=from(width,height,density);
        int appWidth=Math.max(320,Math.round((width-primaryLeft)*(device.width/(float)width)/2)*2);
        return new CanvasSize(appWidth,device.height,device.density);
    }
}
