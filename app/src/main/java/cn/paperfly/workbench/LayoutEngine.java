package cn.paperfly.workbench;

/** Pure geometry; these are requested system task bounds, not embedded app surfaces. */
public final class LayoutEngine {
    private LayoutEngine() {}

    /** mode: 0 columns, 1 a large primary window and side windows, 2 rows. */
    public static int[][] calculate(int width, int height, int insetLeft, int insetTop,
                                    int insetRight, int insetBottom, int count, int mode) {
        if (count < 1 || count > 3 || mode < 0 || mode > 2)
            throw new IllegalArgumentException("需要 1–3 个应用和有效布局");
        if (width <= 0 || height <= 0 || insetLeft < 0 || insetTop < 0
                || insetRight < 0 || insetBottom < 0)
            throw new IllegalArgumentException("屏幕尺寸无效");
        long usableWidth = (long) width - insetLeft - insetRight;
        long usableHeight = (long) height - insetTop - insetBottom;
        if (usableWidth < 120 || usableHeight < 120)
            throw new IllegalArgumentException("可用屏幕区域过小");
        int gap = Math.max(4, Math.min(24, (int) Math.min(usableWidth, usableHeight) / 60));
        int left = insetLeft + gap, top = insetTop + gap;
        int right = width - insetRight - gap, bottom = height - insetBottom - gap;
        int[][] out = new int[count][4];
        if (count == 1) {
            out[0] = new int[]{left, top, right, bottom};
        } else if (mode == 1) {
            // In portrait, stack the primary above the auxiliaries to preserve readable widths.
            if (right - left >= bottom - top) {
                int split = left + (int) ((long) (right - left - gap) * 3 / 5);
                out[0] = new int[]{left, top, split, bottom};
                divide(out, 1, count - 1, split + gap, top, right, bottom, gap, true);
            } else {
                int split = top + (int) ((long) (bottom - top - gap) * 3 / 5);
                out[0] = new int[]{left, top, right, split};
                divide(out, 1, count - 1, left, split + gap, right, bottom, gap, false);
            }
        } else {
            divide(out, 0, count, left, top, right, bottom, gap, mode == 2);
        }
        return out;
    }

    private static void divide(int[][] out, int offset, int count, int left, int top,
                               int right, int bottom, int gap, boolean rows) {
        int span = (rows ? bottom - top : right - left) - gap * (count - 1);
        for (int i = 0; i < count; i++) {
            int start = (int) ((long) span * i / count) + gap * i;
            int end = (int) ((long) span * (i + 1) / count) + gap * i;
            out[offset + i] = rows
                    ? new int[]{left, top + start, right, top + end}
                    : new int[]{left + start, top, left + end, bottom};
        }
    }
}
