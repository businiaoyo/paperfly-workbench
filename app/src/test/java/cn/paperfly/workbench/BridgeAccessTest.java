package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgeAccessTest {
    @Test public void acceptsMatchingShell() { assertTrue(BridgeAccess.accepts(2000, 9, 9)); }
    @Test public void rejectsOrdinaryAppsIncludingOwner() {
        for (int uid : new int[]{1000, 10001, 10252, 99001, -1})
            assertFalse(BridgeAccess.accepts(uid, 9, 9));
    }
    @Test public void rejectsStaleOrMissingVersion() {
        assertFalse(BridgeAccess.accepts(2000, 8, 9));
        assertFalse(BridgeAccess.accepts(2000, -1, 9));
        assertFalse(BridgeAccess.accepts(2000, 0, 0));
    }
}
