package cn.paperfly.workbench;

/** Distinguishes a card tap, vertical browsing and deliberate horizontal dismissal. */
final class RailGesture {
    static final int NONE = 0, HORIZONTAL = 1, VERTICAL = 2;
    private final float slop;
    private float startX, startY;
    float dx, dy;
    int axis;
    RailGesture(float slop) { this.slop = slop; }
    void begin(float x, float y) { startX=x; startY=y; dx=dy=0; axis=NONE; }
    void move(float x, float y) {
        dx=x-startX; dy=y-startY;
        if (axis != NONE) return;
        if (Math.max(Math.abs(dx), Math.abs(dy)) <= slop) return;
        if (Math.abs(dx) > Math.abs(dy) * 1.3f) axis=HORIZONTAL;
        else if (Math.abs(dy) > Math.abs(dx) * 1.1f) axis=VERTICAL;
    }
    boolean dismiss(float cardWidth, float minimumDistance) {
        return axis == HORIZONTAL && Math.abs(dx) >= Math.max(minimumDistance, cardWidth * .32f);
    }
    boolean tap() { return axis == NONE && Math.max(Math.abs(dx),Math.abs(dy)) <= slop; }
}
