package cn.paperfly.workbench.activation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AdbEndpointCatalogTest {
    private final AdbEndpointCatalog catalog=new AdbEndpointCatalog();
    private final String connect=AdbEndpointCatalog.key("_adb-tls-connect._tcp.","adb-device");
    private final String pairing=AdbEndpointCatalog.key("_adb-tls-pairing._tcp.","adb-device");

    @Test public void renamedConnectionServiceSurvivesWirelessRestartAndOriginalServiceLoss(){
        long old=catalog.found(connect);catalog.update(connect,old,35863,true);
        String renamed=AdbEndpointCatalog.key("_adb-tls-connect._tcp.","adb-device (2)");
        catalog.update(renamed,catalog.found(renamed),38661,true);catalog.lost(connect);
        catalog.update(connect,old,35863,true);
        assertEquals(38661,AdbEndpointCatalog.nextPort(catalog.connections(),"ADB-DEVICE",java.util.Collections.emptySet()));
        assertEquals(0,AdbEndpointCatalog.nextPort(catalog.connections(),"adb-device",java.util.Collections.singleton(38661)));
    }
    @Test public void conflictSuffixCannotMatchAnotherDeviceOrArbitraryPrefix(){
        for(String name:new String[]{"adb-device2","adb-device-other (2)","adb-device (other)","adb-device (0)","adb-device (2) trailing","adb-device (2) (3)","other-device (2)"})
            assertFalse(name,new AdbEndpointCatalog.Endpoint(name,38661).matches("adb-device"));
        assertTrue(new AdbEndpointCatalog.Endpoint("adb-device (1)",38661).matches("adb-device"));
        assertTrue(new AdbEndpointCatalog.Endpoint("adb-device (12)",38661).matches("adb-device"));
    }

    @Test public void postPairConnectionWaitsForAuthenticatedDeviceInsteadOfFirstLocalAddress(){
        String other=AdbEndpointCatalog.key("_adb-tls-connect._tcp.","adb-HA2HEFTM-qKwfb0");
        catalog.update(other,catalog.found(other),40123,true);
        assertEquals(0,AdbEndpointCatalog.nextPort(catalog.connections(),"adb-device",java.util.Collections.emptySet()));
        catalog.update(connect,catalog.found(connect),35863,true);
        assertEquals(35863,AdbEndpointCatalog.nextPort(catalog.connections(),"ADB-DEVICE",java.util.Collections.emptySet()));
        assertEquals(0,AdbEndpointCatalog.nextPort(catalog.connections(),"adb-device",java.util.Collections.singleton(35863)));
        assertEquals(40123,AdbEndpointCatalog.nextPort(catalog.connections(),null,java.util.Collections.emptySet()));
    }
    @Test public void pairingServiceCannotSupplyConnectionAndRejectedIdentityClearsPort(){
        catalog.update(pairing,catalog.found(pairing),40123,true);
        assertEquals(0,AdbEndpointCatalog.nextPort(catalog.connections(),"adb-device",java.util.Collections.emptySet()));
        long token=catalog.found(connect);catalog.update(connect,token,40123,true);
        catalog.update(connect,token,40123,AdbEndpointIdentity.local(true,"HES-W00",37,"TB323FU","36"));
        assertEquals(0,AdbEndpointCatalog.nextPort(catalog.connections(),null,java.util.Collections.emptySet()));
    }

    @Test public void discoveryAndResolutionSuffixesIdentifyTheSameService(){
        assertEquals(connect,AdbEndpointCatalog.key("_ADB-TLS-CONNECT._TCP.local.","ADB-DEVICE"));
        assertNotEquals(connect,pairing);
    }
    @Test public void updatedPortIsPreferredAndConflictRemainsAvailableForRetry(){
        long token=catalog.found(connect);catalog.update(connect,token,34201,true);catalog.update(connect,token,33551,true);
        assertArrayEquals(new int[]{33551,34201},catalog.ports(false));
        catalog.update(connect,token,34201,true);
        assertArrayEquals(new int[]{34201,33551},catalog.ports(false));
    }
    @Test public void lostServiceWithDifferentSuffixClearsAllPorts(){
        long token=catalog.found(connect);catalog.update(connect,token,33551,true);
        catalog.lost(AdbEndpointCatalog.key("_adb-tls-connect._tcp.local.","adb-device"));
        catalog.update(connect,token,34201,true);
        assertArrayEquals(new int[0],catalog.ports(false));
    }
    @Test public void lateCallbackCannotResurrectLostAndRediscoveredService(){
        long old=catalog.found(connect);catalog.lost(connect);long current=catalog.found(connect);
        catalog.update(connect,current,33551,true);catalog.update(connect,old,34201,true);
        assertArrayEquals(new int[]{33551},catalog.ports(false));
    }
    @Test public void stopAndRestartRejectPreviousCallbacks(){
        long old=catalog.found(connect);catalog.clear();catalog.found(connect);
        catalog.update(connect,old,34201,true);assertArrayEquals(new int[0],catalog.ports(false));
    }
    @Test public void remoteAddressUpdateRemovesPreviouslyLocalCandidates(){
        long token=catalog.found(connect);catalog.update(connect,token,33551,true);
        catalog.update(connect,token,38931,false);assertArrayEquals(new int[0],catalog.ports(false));
    }
    @Test public void pairingOnlyUsesLatestPortAndIsSeparateFromConnection(){
        long token=catalog.found(pairing);catalog.update(pairing,token,35191,true);catalog.update(pairing,token,37651,true);
        assertArrayEquals(new int[]{37651},catalog.ports(true));assertArrayEquals(new int[0],catalog.ports(false));
    }
    @Test public void candidatesAreBoundedDeduplicatedAndValidated(){
        long token=catalog.found(connect);
        for(int port:new int[]{30001,30002,30003,30004,30004})catalog.update(connect,token,port,true);
        assertArrayEquals(new int[]{30004,30003,30002},catalog.ports(false));
        catalog.update(connect,token,65536,true);assertArrayEquals(new int[0],catalog.ports(false));
        catalog.update(connect,token,0,true);assertArrayEquals(new int[0],catalog.ports(false));
    }
}
