package cn.paperfly.workbench.update;
import org.junit.Test;
import java.io.*;
import java.security.MessageDigest;
import java.util.Set;
import static org.junit.Assert.*;
public class UpdateIntegrityTest {
    private final byte[] payload=new byte[]{1,2,3,4};
    private String digest()throws Exception{return UpdateIntegrity.hex(MessageDigest.getInstance("SHA-256").digest(payload));}
    @Test public void verifiedStreamMatchesOriginal()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();UpdateIntegrity.copy(new ByteArrayInputStream(payload),out,4,digest(),()->false,n->{});assertArrayEquals(payload,out.toByteArray());}
    @Test public void truncatedOversizedAndCorruptedDownloadsFail()throws Exception{
        for(int expected:new int[]{3,5})assertThrows(Exception.class,()->UpdateIntegrity.copy(new ByteArrayInputStream(payload),new ByteArrayOutputStream(),expected,digest(),()->false,n->{}));
        assertThrows(Exception.class,()->UpdateIntegrity.copy(new ByteArrayInputStream(payload),new ByteArrayOutputStream(),4,"0".repeat(64),()->false,n->{}));
    }
    @Test public void cancellationPreventsWritingInstallableOutput()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();assertThrows(Exception.class,()->UpdateIntegrity.copy(new ByteArrayInputStream(payload),out,4,digest(),()->true,n->{}));assertEquals(0,out.size());
    }
    @Test public void sameSignerAndForwardRotationAreAccepted(){
        assertTrue(UpdateIntegrity.compatibleSigners(false,Set.of("old"),false,Set.of("old"),Set.of("old")));
        assertTrue(UpdateIntegrity.compatibleSigners(false,Set.of("old"),false,Set.of("new"),Set.of("old","new")));
        assertFalse(UpdateIntegrity.compatibleSigners(false,Set.of("new"),false,Set.of("old"),Set.of("old")));
    }
    @Test public void foreignMissingAndPartialMultipleSignersAreRejected(){
        assertFalse(UpdateIntegrity.compatibleSigners(false,Set.of("trusted"),false,Set.of("other"),Set.of("other")));
        assertFalse(UpdateIntegrity.compatibleSigners(false,Set.of(),false,Set.of("other"),Set.of("other")));
        assertFalse(UpdateIntegrity.compatibleSigners(true,Set.of("a","b"),true,Set.of("a"),Set.of("a","b")));
        assertTrue(UpdateIntegrity.compatibleSigners(true,Set.of("a","b"),true,Set.of("a","b"),Set.of()));
    }
}
