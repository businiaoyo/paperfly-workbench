package io.github.muntashirakon.adb;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public final class PairingAuthCtxTest {
    private byte[] password(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void pairedPeersAuthenticateBidirectionalMessages(){PairingAuthCtx a=PairingAuthCtx.createAlice(password("123456-exporter")),b=PairingAuthCtx.createBob(password("123456-exporter"));assertNotNull(a);assertNotNull(b);assertTrue(a.initCipher(b.getMsg()));assertTrue(b.initCipher(a.getMsg()));assertArrayEquals(password("peer info"),b.decrypt(a.encrypt(password("peer info"))));assertArrayEquals(password("response"),a.decrypt(b.encrypt(password("response"))));a.destroy();b.destroy();}
    @Test public void wrongCodeDoesNotAuthenticate(){PairingAuthCtx a=PairingAuthCtx.createAlice(password("123456-exporter")),b=PairingAuthCtx.createBob(password("654321-exporter"));a.initCipher(b.getMsg());b.initCipher(a.getMsg());assertNull(b.decrypt(a.encrypt(password("peer info"))));a.destroy();b.destroy();}
    @Test public void changedCiphertextIsRejected(){PairingAuthCtx a=PairingAuthCtx.createAlice(password("123456-exporter")),b=PairingAuthCtx.createBob(password("123456-exporter"));a.initCipher(b.getMsg());b.initCipher(a.getMsg());byte[] encrypted=a.encrypt(password("peer info"));encrypted[0]^=1;assertNull(b.decrypt(encrypted));a.destroy();b.destroy();}
    @Test public void destroyedContextCannotEncrypt(){PairingAuthCtx a=PairingAuthCtx.createAlice(password("123456-exporter"));a.destroy();assertTrue(a.isDestroyed());assertNull(a.encrypt(password("peer info")));}
}
