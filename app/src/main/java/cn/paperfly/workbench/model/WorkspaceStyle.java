package cn.paperfly.workbench.model;

/** Device-independent presentation preferences. Pixel positions and scroll offsets are runtime state. */
public final class WorkspaceStyle {
    public static final WorkspaceStyle DEFAULT = new WorkspaceStyle(
            0xff141d2b, 0xff274049, 0xff162131, 112, 190, .19f,false,false,1f,"",0,true);
    public final int backgroundStart, backgroundMiddle, backgroundEnd;
    public final int portraitRailDp, landscapeRailDp;
    public final float maxRailFraction, primaryScale, aspectRatio;
    public final boolean railRight, compactCards;
    public final String backgroundUri;
    public final boolean wallpaperBackground;

    public WorkspaceStyle(int start, int middle, int end, int portraitDp, int landscapeDp, float maxFraction) {
        this(start,middle,end,portraitDp,landscapeDp,maxFraction,false,false,1f,"");
    }

    public WorkspaceStyle(int start,int middle,int end,int portraitDp,int landscapeDp,float maxFraction,
                          boolean right,boolean compact,float primaryScale,String backgroundUri) {
        this(start,middle,end,portraitDp,landscapeDp,maxFraction,right,compact,primaryScale,backgroundUri,0);
    }
    public WorkspaceStyle(int start,int middle,int end,int portraitDp,int landscapeDp,float maxFraction,
                          boolean right,boolean compact,float primaryScale,String backgroundUri,float aspectRatio) {
        this(start,middle,end,portraitDp,landscapeDp,maxFraction,right,compact,primaryScale,backgroundUri,aspectRatio,false);
    }
    public WorkspaceStyle(int start,int middle,int end,int portraitDp,int landscapeDp,float maxFraction,
                          boolean right,boolean compact,float primaryScale,String backgroundUri,float aspectRatio,boolean wallpaperBackground) {
        this.wallpaperBackground=wallpaperBackground;
        if(!Float.isFinite(aspectRatio)||aspectRatio<0||aspectRatio>3)throw new IllegalArgumentException("Invalid aspect ratio");
        this.aspectRatio=aspectRatio;
        if(!Float.isFinite(primaryScale)||primaryScale<.6f||primaryScale>1f)throw new IllegalArgumentException("Invalid primary scale");
        this.primaryScale=primaryScale;railRight=right;compactCards=compact;
        this.backgroundUri=java.util.Objects.requireNonNull(backgroundUri);
        if(wallpaperBackground&&!backgroundUri.isEmpty())throw new IllegalArgumentException("Wallpaper and image are exclusive");
        if (portraitDp < 40 || portraitDp > 400 || landscapeDp < 40 || landscapeDp > 400
                || !Float.isFinite(maxFraction) || maxFraction < .1f || maxFraction > .4f)
            throw new IllegalArgumentException("Invalid rail dimensions");
        backgroundStart = start; backgroundMiddle = middle; backgroundEnd = end;
        portraitRailDp = portraitDp; landscapeRailDp = landscapeDp; maxRailFraction = maxFraction;
    }

    public int[] backgroundColors() { return new int[]{backgroundStart, backgroundMiddle, backgroundEnd}; }
}
