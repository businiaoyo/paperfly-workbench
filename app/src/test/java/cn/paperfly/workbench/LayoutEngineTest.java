package cn.paperfly.workbench;

import org.junit.Test;

import static org.junit.Assert.*;

/** Checks layout properties instead of duplicating the placement algorithm. */
public class LayoutEngineTest {
    @Test
    public void allLayoutsFitPhoneAndTabletInBothOrientations() {
        int[][] screens = {{1080, 2400}, {2400, 1080}, {2136, 3200}, {3200, 2136}};
        for (int[] screen : screens) {
            for (int count = 1; count <= 3; count++) {
                for (int mode = 0; mode <= 2; mode++) {
                    int[][] bounds = LayoutEngine.calculate(screen[0], screen[1],
                            24, 96, 30, 72, count, mode);
                    assertValidLayout(bounds, count, 24, 96, screen[0] - 30, screen[1] - 72);
                }
            }
        }
    }

    @Test
    public void oneAppUsesSameInsetAreaRegardlessOfLayout() {
        int[] baseline = LayoutEngine.calculate(1080, 2400, 0, 100, 0, 80, 1, 0)[0];
        for (int mode = 1; mode <= 2; mode++) {
            assertArrayEquals(baseline,
                    LayoutEngine.calculate(1080, 2400, 0, 100, 0, 80, 1, mode)[0]);
        }
        assertTrue(baseline[0] > 0);
        assertTrue(baseline[1] > 100);
        assertTrue(baseline[2] < 1080);
        assertTrue(baseline[3] < 2320);
    }

    @Test
    public void parallelLayoutHasEqualHeightAndAlmostEqualWidths() {
        int[][] bounds = LayoutEngine.calculate(1081, 2400, 7, 90, 13, 45, 3, 0);
        for (int i = 1; i < bounds.length; i++) {
            assertEquals(bounds[0][1], bounds[i][1]);
            assertEquals(bounds[0][3], bounds[i][3]);
            assertTrue(Math.abs(width(bounds[0]) - width(bounds[i])) <= 1);
            assertTrue(bounds[i - 1][2] < bounds[i][0]);
        }
    }

    @Test
    public void rowLayoutHasEqualWidthAndAlmostEqualHeights() {
        int[][] bounds = LayoutEngine.calculate(2400, 1081, 7, 41, 13, 19, 3, 2);
        for (int i = 1; i < bounds.length; i++) {
            assertEquals(bounds[0][0], bounds[i][0]);
            assertEquals(bounds[0][2], bounds[i][2]);
            assertTrue(Math.abs(height(bounds[0]) - height(bounds[i])) <= 1);
            assertTrue(bounds[i - 1][3] < bounds[i][1]);
        }
    }

    @Test
    public void focusLayoutKeepsPrimaryLargestAndAdaptsToOrientation() {
        int[][] portrait = LayoutEngine.calculate(1080, 2400, 0, 90, 0, 60, 3, 1);
        int[][] landscape = LayoutEngine.calculate(2400, 1080, 0, 60, 0, 45, 3, 1);
        for (int i = 1; i <= 2; i++) {
            assertTrue(area(portrait[0]) > area(portrait[i]));
            assertTrue(area(landscape[0]) > area(landscape[i]));
            assertTrue("Portrait auxiliaries should sit below primary", portrait[0][3] < portrait[i][1]);
            assertTrue("Landscape auxiliaries should sit beside primary", landscape[0][2] < landscape[i][0]);
        }
    }

    @Test
    public void minimumUsableAreaStillProducesNonemptyNonoverlappingWindows() {
        for (int count = 1; count <= 3; count++) {
            for (int mode = 0; mode <= 2; mode++) {
                int[][] bounds = LayoutEngine.calculate(220, 280, 50, 80, 50, 80, count, mode);
                assertValidLayout(bounds, count, 50, 80, 170, 200);
            }
        }
    }

    @Test
    public void extremeLegalDimensionsDoNotOverflowCoordinates() {
        // Keeps intermediate arithmetic safe even if display metadata is unexpectedly large.
        for (int count = 1; count <= 3; count++) {
            for (int mode = 0; mode <= 2; mode++) {
                int[][] bounds = LayoutEngine.calculate(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        12, 48, 24, 96, count, mode);
                assertValidLayout(bounds, count, 12, 48, Integer.MAX_VALUE - 24, Integer.MAX_VALUE - 96);
            }
        }
    }

    @Test
    public void rejectsInvalidAppCountsAndLayoutModes() {
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 4, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LayoutEngine.calculate(1080, 2400, 0, 0, 0, 0, count, 0));
        }
        for (int mode : new int[]{Integer.MIN_VALUE, -1, 3, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LayoutEngine.calculate(1080, 2400, 0, 0, 0, 0, 1, mode));
        }
    }

    @Test
    public void rejectsInvalidScreenAndInsetsWithoutArithmeticWraparound() {
        int[][] invalid = {
                {0, 2400, 0, 0, 0, 0},
                {1080, -1, 0, 0, 0, 0},
                {1080, 2400, -1, 0, 0, 0},
                {1080, 2400, 0, -1, 0, 0},
                {1080, 2400, 0, 0, -1, 0},
                {1080, 2400, 0, 0, 0, -1},
                {119, 2400, 0, 0, 0, 0},
                {1080, 119, 0, 0, 0, 0},
                {1080, 2400, 500, 0, 500, 0},
                {1080, 2400, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0},
                {1080, 2400, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 1, 0}
        };
        for (int[] v : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> LayoutEngine.calculate(v[0], v[1], v[2], v[3], v[4], v[5], 2, 0));
        }
    }

    private static void assertValidLayout(int[][] bounds, int count,
                                          int left, int top, int right, int bottom) {
        assertEquals(count, bounds.length);
        for (int[] window : bounds) {
            assertEquals(4, window.length);
            assertTrue("Window starts inside usable display", window[0] >= left && window[1] >= top);
            assertTrue("Window ends inside usable display", window[2] <= right && window[3] <= bottom);
            assertTrue("Window has positive width", window[2] > window[0]);
            assertTrue("Window has positive height", window[3] > window[1]);
        }
        for (int i = 0; i < bounds.length; i++) {
            for (int j = i + 1; j < bounds.length; j++) {
                int[] a = bounds[i], b = bounds[j];
                assertTrue("Windows must not overlap",
                        a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]);
            }
        }
    }

    private static long width(int[] bounds) { return (long) bounds[2] - bounds[0]; }
    private static long height(int[] bounds) { return (long) bounds[3] - bounds[1]; }
    private static long area(int[] bounds) { return width(bounds) * height(bounds); }
}
