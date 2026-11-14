package cn.paperfly.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaskTransactionBridgeTest {
    @Test public void allowsOnlyAnIsolatedStandardTask() {
        assertTrue(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, 1, new int[]{42}));
    }
    @Test public void rejectsTaskIdReuseOrChanges() {
        assertFalse(TaskTransactionBridge.isIsolatedTarget(43, 42, 0, 0, 0, 1, 1, new int[]{43}));
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, 1, new int[]{43}));
    }
    @Test public void rejectsSharedRootsAndUnknownChildren() {
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, 1, new int[]{42, 43}));
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, 1, new int[]{}));
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, 1, null));
    }
    @Test public void rejectsOtherUsersAndDisplays() {
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 1, 0, 0, 1, 1, new int[]{42}));
        assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 10, 0, 1, 1, new int[]{42}));
    }
    @Test public void rejectsPipSplitAndAlreadyFloatingTasks() {
        for (int mode : new int[]{0, 2, 5, 6, 102})
            assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, 1, mode, new int[]{42}));
    }
    @Test public void rejectsHomeAndNonstandardTasks() {
        for (int type : new int[]{0, 2, 3, 4, 5})
            assertFalse(TaskTransactionBridge.isIsolatedTarget(42, 42, 0, 0, 0, type, 1, new int[]{42}));
    }
}
