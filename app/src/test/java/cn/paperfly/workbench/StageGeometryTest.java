package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public class StageGeometryTest {
    @Test public void mainWindowLeavesRailAndSystemBarsClear() {
        int[] bounds = StageGeometry.primary(2304, 1440, 120, 30, 390, 1f);
        assertTrue(bounds[0] > 390);
        assertTrue(bounds[1] > 120);
        assertTrue(bounds[2] < 2304);
        assertTrue(bounds[3] < 1410);
    }
    @Test public void scaledSurfaceEndsAtTheSamePhysicalPosition() {
        int[] unscaled = StageGeometry.primary(2304, 1440, 120, 30, 390, 1f);
        int[] scaled = StageGeometry.primary(2304, 1440, 120, 30, 390, .76f);
        assertEquals(unscaled[0], scaled[0]);
        assertEquals(unscaled[1], scaled[1]);
        assertEquals(unscaled[2], scaled[0] + (scaled[2] - scaled[0]) * .76, 1);
        assertEquals(unscaled[3], scaled[1] + (scaled[3] - scaled[1]) * .76, 1);
    }
    @Test public void portraitKeepsAnInteractivePrimaryRegion() {
        int[] rect = StageGeometry.primary(1080, 2400, 80, 100, 205, 1f);
        assertTrue(rect[2] - rect[0] > 700);
        assertTrue(rect[3] - rect[1] > 2000);
    }
    @Test public void rejectsImpossibleOrNonfiniteGeometry() {
        assertThrows(IllegalArgumentException.class, () -> StageGeometry.primary(320, 600, 0, 0, 280, 1));
        assertThrows(IllegalArgumentException.class, () -> StageGeometry.primary(1080, 2400, 0, 0, 200, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> StageGeometry.primary(1080, 2400, 0, 0, 200, .1f));
    }
    @Test public void allowsSwitchingBetweenFullAndFreeformOnly() {
        assertTrue(StageGeometry.isolated(7, 7, 0, 0, 0, 1, 1, new int[]{7}));
        assertTrue(StageGeometry.isolated(7, 7, 0, 0, 0, 1, 5, new int[]{7}));
        for (int mode : new int[]{0, 2, 6, 102})
            assertFalse(StageGeometry.isolated(7, 7, 0, 0, 0, 1, mode, new int[]{7}));
    }
    @Test public void refusesOtherUsersDisplaysHomeAndSharedTasks() {
        assertFalse(StageGeometry.isolated(7, 7, 0, 10, 0, 1, 5, new int[]{7}));
        assertFalse(StageGeometry.isolated(7, 7, 1, 0, 0, 1, 5, new int[]{7}));
        assertFalse(StageGeometry.isolated(7, 7, 0, 0, 0, 2, 1, new int[]{7}));
        assertFalse(StageGeometry.isolated(7, 7, 0, 0, 0, 1, 5, new int[]{7, 9}));
        assertFalse(StageGeometry.isolated(7, 7, 0, 0, 0, 1, 5, null));
    }
    @Test public void refusesStaleIds() {
        assertFalse(StageGeometry.isolated(8, 7, 0, 0, 0, 1, 5, new int[]{8}));
        assertFalse(StageGeometry.isolated(7, 7, 0, 0, 0, 1, 5, new int[]{8}));
    }
}
