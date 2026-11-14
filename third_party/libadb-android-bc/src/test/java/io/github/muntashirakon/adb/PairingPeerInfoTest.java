package io.github.muntashirakon.adb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PairingPeerInfoTest {
    private byte[] payload(String guid){byte[] bytes=new byte[8192];bytes[0]=1;
        byte[] name=guid.getBytes(StandardCharsets.US_ASCII);System.arraycopy(name,0,bytes,1,name.length);return bytes;}
    private void rejected(byte[] bytes){try{PairingPeerInfo.deviceGuid(bytes);fail("Malformed peer identity accepted");}catch(IOException expected){}}
    @Test public void parsesAuthenticatedServerGuid(){
        try{assertEquals("adb-AKDPBB6A08000069-0XR5OU",PairingPeerInfo.deviceGuid(payload("adb-AKDPBB6A08000069-0XR5OU")));}
        catch(IOException e){throw new AssertionError(e);}
    }
    @Test public void rejectsKeyTypeAndInvalidPayloadSize(){
        byte[] bytes=payload("adb-device");bytes[0]=0;rejected(bytes);
        rejected(null);rejected(new byte[8191]);rejected(new byte[8193]);
    }
    @Test public void rejectsEmptyUnterminatedOrOversizedGuid(){
        rejected(payload(""));rejected(payload("a".repeat(256)));
        byte[] bytes=new byte[8192];Arrays.fill(bytes,(byte)'a');bytes[0]=1;rejected(bytes);
    }
    @Test public void rejectsNonAsciiAndControlCharacters(){
        rejected(payload("adb-device\n"));rejected(payload("adb/device"));
        byte[] bytes=payload("adb-device");bytes[2]=(byte)0xff;rejected(bytes);
    }
}
