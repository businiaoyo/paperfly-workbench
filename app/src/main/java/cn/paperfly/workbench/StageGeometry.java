package cn.paperfly.workbench;

/** Physical placement of the rail and the primary task, with an optional OEM scale correction. */
final class StageGeometry {
    private StageGeometry() {}

    static int[] primary(int width, int height, int top, int bottom, int rail, float scale) {
        if (width < 240 || height < 240 || top < 0 || bottom < 0 || rail < 80
                || scale < 0.5f || scale > 1f || !Float.isFinite(scale))
            throw new IllegalArgumentException("屏幕布局不可用");
        int gap = Math.max(12, Math.min(width, height) / 60);
        int left = rail + gap, y = top + gap;
        int visibleWidth = width - left - gap;
        int visibleHeight = height - y - bottom - gap;
        if (visibleWidth < 120 || visibleHeight < 120)
            throw new IllegalArgumentException("主窗口空间不足");
        return new int[]{left, y, left + Math.round(visibleWidth / scale),
                y + Math.round(visibleHeight / scale)};
    }

    static boolean isolated(int taskId, int expected, int display, int user, int expectedUser,
                            int type, int mode, int[] children) {
        return taskId >= 0 && taskId == expected && display == 0 && user == expectedUser
                && user >= 0 && type == 1 && (mode == 1 || mode == 5)
                && children != null && children.length == 1 && children[0] == taskId;
    }
}
