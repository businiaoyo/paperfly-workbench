package cn.paperfly.workbench.activation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AdbPrerequisitesTest {
    @Test public void honorDisabledAdbNeedsChargingSettingGuidance(){
        assertTrue(AdbPrerequisites.connectionHint("HONOR",0,1,-1).contains("仅充电"));
        assertTrue(AdbPrerequisites.connectionHint("HONOR",1,1,0).contains("仅充电"));
    }
    @Test public void missingVendorKeyDoesNotClaimItIsDisabled(){
        assertFalse(AdbPrerequisites.connectionHint("HONOR",1,1,-1).contains("仅充电"));
    }
    @Test public void otherDevicesDoNotRequireUsbDebuggingForWirelessConnection(){
        assertFalse(AdbPrerequisites.connectionHint("Google",0,1,0).contains("仅充电"));
        assertFalse(AdbPrerequisites.needsChargingGuide("vivo"));
    }
    @Test public void wirelessOffIsReportedBeforeDiscoveryFailure(){
        assertTrue(AdbPrerequisites.connectionHint("HONOR",1,0,1).contains("无线调试已关闭"));
    }
}
