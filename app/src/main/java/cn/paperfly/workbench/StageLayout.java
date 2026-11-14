package cn.paperfly.workbench;

import cn.paperfly.workbench.model.WorkspaceStyle;

/** Pure placement policy shared by previews, gestures and fullscreen canvas sizing. All outputs are physical pixels. */
final class StageLayout {
    static final float SIDE_YAW = 28f;
    static final float DOCK_INSET_DP = 62;
    // iQOO reference: far/near vertical edges ≈ 0.917, with a shared vertical vanishing point.
    static final float SIDE_EDGE_RATIO = 11f / 12f;

    /** Reused per View. All camera geometry is in scene pixels, independent of Android density. */
    static final class CardPose {
        float pivotX, pivotY, translationX, translationY, scale, scaleY, yaw, cameraDistance;
        void set(float x, float y, float scale, int canvasWidth, float yaw, float horizon) {
            set(x,y,scale,scale,canvasWidth,yaw,horizon);
        }
        void set(float x,float y,float scale,float scaleY,int canvasWidth,float yaw,float horizon){
            this.scale = scale;
            this.scaleY = scaleY;
            this.yaw = Math.max(-SIDE_YAW, Math.min(SIDE_YAW, yaw));
            float progress = Math.abs(this.yaw) / SIDE_YAW;
            float ratio = 1 - (1 - SIDE_EDGE_RATIO) * progress;
            double angle = Math.toRadians(Math.abs(this.yaw));
            float width = canvasWidth * scale;
            float projectedWidth = width * (float)Math.cos(angle) * ratio;
            float inset = (width - projectedWidth) / 2;
            pivotX = this.yaw < 0 ? canvasWidth : 0;
            // Every card rotates around the same scene horizon, not its own centre.
            pivotY = (horizon - y) / scaleY;
            translationX = x - pivotX * (1 - scale) + (this.yaw < 0 ? -inset : inset);
            translationY = y - pivotY * (1 - scaleY);
            cameraDistance = progress > .0001f
                    ? width * (float)Math.sin(angle) * ratio / (1 - ratio)
                    : width * 6;
        }
    }
    static final class Bounds {
        final float left, top, right, bottom;
        Bounds(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        float width() { return right - left; }
        float height() { return bottom - top; }
    }

    private final int width, height, windowCount;
    private final float density;
    private final boolean fullscreen;
    private final WorkspaceStyle style;
    boolean railHidden;
    float dockInset;

    StageLayout(int width, int height, float density, boolean fullscreen, int windowCount, WorkspaceStyle style) {
        if (width < 0 || height < 0 || density <= 0 || !Float.isFinite(density)
                || windowCount < 0)
            throw new IllegalArgumentException("Invalid scene dimensions");
        this.width = width; this.height = height; this.density = density; this.fullscreen = fullscreen;
        this.windowCount = windowCount; this.style = style;
    }

    boolean matches(int width, int height, float density, boolean fullscreen, int windowCount, WorkspaceStyle style) {
        return this.width == width && this.height == height && this.density == density && this.fullscreen == fullscreen
                && this.windowCount == windowCount && this.style == style;
    }

    static float railWidthFor(int width, int height, float density, WorkspaceStyle style) {
        return Math.min(Math.round((height > width ? style.portraitRailDp : style.landscapeRailDp) * density),
                width * style.maxRailFraction);
    }

    static int fullPrimaryLeft(int width, int height, float density, WorkspaceStyle style) {
        return Math.round(railWidthFor(width, height, density, style)) + Math.round((height > width ? 8 : 12) * density);
    }

    private int dp(float value) { return Math.round(value * density); }
    private boolean portrait() { return height > width; }
    int gap() { return dp(portrait() ? 8 : 12); }
    float railTop() { return fullscreen ? dp(8) : dp(56); }
    float railBottom() { return height - dp(fullscreen ? 56 : 8); }
    float perspectiveHorizon() { return (railTop() + railBottom()) / 2; }
    float railHeight() { return Math.max(dp(100), railBottom() - railTop()); }
    float railWidth() { return railWidthFor(width, height, density, style); }
    int railEntries() { return Math.max(1, windowCount); } // N - 1 side cards and one Add card.
    float railItem() { return (style.compactCards?.88f:1f)*Math.min(dp(portrait()?220:150),railHeight()/Math.min(4,railEntries())); }
    float clampOffset(float offset) { return RailLayout.clamp(offset, railEntries(), railItem(), railHeight()); }

    /** A single app ignores the rail, card scale/aspect preferences and split-pane weights. */
    Bounds fullscreenApp() { return new Bounds(0, 0, width, height); }

    Bounds member(Bounds card, int index, int count, float firstWeight, int axis) {
        return PaneLayout.member(card, index, count, firstWeight, axis == 0 ? (portrait() ? 2 : 1) : axis, dp(4));
    }
    Bounds member(Bounds card,int index,java.util.List<Float> weights,int axis) {
        return PaneLayout.member(card,index,weights,axis==0?(portrait()?2:1):axis,dp(4));
    }

    /** Map a whole split card together, so fullscreen/rail previews cannot add gaps per member. */
    static Bounds mapMember(Bounds target, Bounds reference, Bounds member) {
        float scale = Math.min(target.width() / reference.width(), target.height() / reference.height());
        float left = target.left + (target.width() - reference.width() * scale) / 2;
        float top = target.top + (target.height() - reference.height() * scale) / 2;
        return new Bounds(left + (member.left - reference.left) * scale,
                top + (member.top - reference.top) * scale,
                left + (member.right - reference.left) * scale,
                top + (member.bottom - reference.top) * scale);
    }

    Bounds freeMember(Bounds card,cn.paperfly.workbench.model.WindowPlacement p){
        float left=card.left+p.x*card.width(),top=card.top+p.y*card.height();
        float caption=Math.min(dp(28),card.height()*.07f);
        return new Bounds(left,top+caption,left+card.width()*p.width,Math.max(top+caption+1,top+card.height()*p.height));
    }

    /** Fit the existing canvas without stretching, cropping or changing application configuration. */
    static Bounds fitCanvas(Bounds pane, int canvasWidth, int canvasHeight) {
        float scale = Math.min(pane.width() / canvasWidth, pane.height() / canvasHeight);
        float width = canvasWidth * scale, height = canvasHeight * scale;
        float x = pane.left + (pane.width() - width) / 2, y = pane.top + (pane.height() - height) / 2;
        return new Bounds(x, y, x + width, y + height);
    }

    Bounds target(int slot,float railOffset){
        Bounds result=slot==0&&railHidden?new Bounds(dp(8),dp(fullscreen?0:8),width-dp(8),height-dp(8)):leftTarget(slot,railOffset);
        if(slot==0&&dockInset>0)result=new Bounds(result.left,result.top,result.right,Math.max(result.top+1,result.bottom-dockInset));
        if(slot==0&&style.aspectRatio>0){float w=Math.min(result.width(),result.height()*style.aspectRatio),h=w/style.aspectRatio;
            result=new Bounds(result.left+(result.width()-w)/2,result.top+(result.height()-h)/2,result.left+(result.width()+w)/2,result.top+(result.height()+h)/2);}
        if(slot==0&&style.primaryScale<1f){float w=result.width()*style.primaryScale,h=result.height()*style.primaryScale;
            result=new Bounds(result.left+(result.width()-w)/2,result.top+(result.height()-h)/2,result.left+(result.width()+w)/2,result.top+(result.height()+h)/2);}
        return style.railRight?new Bounds(width-result.right,result.top,width-result.left,result.bottom):result;
    }
    private Bounds leftTarget(int slot, float railOffset) {
        if (slot < 0) throw new IllegalArgumentException("Unknown slot");
        if (fullscreen && slot == 0)
            return new Bounds(fullPrimaryLeft(width, height, density, style), 0, width, height);
        float top = railTop(), gap = gap(), rail = railWidth(), ah = railHeight();
        if (slot == 0) {
            return new Bounds(rail+gap,dp(8),Math.max(rail+gap+1,width-dp(8)),Math.max(dp(8)+1,height-dp(8)));
        }
        Bounds main=target(0,0);float ratio=main.width()/main.height();
        float item=railItem(),h=Math.max(1,Math.min((rail-gap)/ratio,item-dp(16))),w=h*ratio,x=(rail+gap-w)/2;
        float y = top + RailLayout.itemTop(slot - 1, railEntries(), item, ah, railOffset)+dp(8);
        return new Bounds(x, y, x + w, y + h);
    }
}
