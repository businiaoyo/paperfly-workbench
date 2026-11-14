package cn.paperfly.workbench;

/** Scroll math independent of Views; the rail never shrinks all cards to fit a growing list. */
final class RailLayout {
    static float maxOffset(int count, float itemHeight, float viewportHeight) {
        return Math.max(0, count * itemHeight - viewportHeight);
    }
    static float clamp(float offset, int count, float itemHeight, float viewportHeight) {
        return Math.max(0, Math.min(offset, maxOffset(count,itemHeight,viewportHeight)));
    }
    static float itemTop(int index, int count, float itemHeight, float viewportHeight, float offset) {
        return Math.max(0,(viewportHeight-count*itemHeight)/2) + index*itemHeight
                - clamp(offset,count,itemHeight,viewportHeight);
    }
}
