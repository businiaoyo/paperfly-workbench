package cn.paperfly.workbench.activation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AdbEndpointIdentityTest {
    @Test public void honorCurrentAnnouncementIsAccepted(){
        assertTrue(AdbEndpointIdentity.local(true,"HES-W00",37,"HES-W00","37.0"));
    }
    @Test public void lenovoAnnouncementWithIncorrectHonorAddressIsRejected(){
        assertFalse(AdbEndpointIdentity.local(true,"HES-W00",37,"TB323FU","36"));
        assertFalse(AdbEndpointIdentity.local(true,"HES-W00",37,"TB323FU",null));
    }
    @Test public void sameModelWithRemoteAddressOrDifferentApiIsRejected(){
        assertFalse(AdbEndpointIdentity.local(false,"HES-W00",37,"HES-W00","37"));
        assertFalse(AdbEndpointIdentity.local(true,"HES-W00",37,"HES-W00","36"));
    }
    @Test public void absentLegacyHintsStillRequireLocalAddress(){
        assertTrue(AdbEndpointIdentity.local(true,"HES-W00",37,null,null));
        assertFalse(AdbEndpointIdentity.local(false,"HES-W00",37,null,null));
    }
    @Test public void malformedApiCannotTurnIntoMatchingVersion(){
        for(String api:new String[]{"37.1","invalid","999999999999999999999999","-37"})
            assertFalse(api,AdbEndpointIdentity.local(true,"HES-W00",37,"HES-W00",api));
    }
}
