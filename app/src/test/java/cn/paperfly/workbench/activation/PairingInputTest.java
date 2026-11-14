package cn.paperfly.workbench.activation;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PairingInputTest {
    @Test public void leadingZerosAreKept(){PairingInput p=PairingInput.parse("001023",37491);assertEquals("001023",p.code);assertEquals(37491,p.port);}
    @Test public void replyCanSupplyPortWithoutDiscovery(){PairingInput p=PairingInput.parse("37491 001023",0);assertEquals(37491,p.port);assertEquals("001023",p.code);}
    @Test public void discoveryCanWaitForPort(){assertEquals(0,PairingInput.parse("001023",0).port);assertEquals(0,PairingInput.port(""));}
    @Test public void unicodeSeparatorsWork(){assertEquals(37491,PairingInput.parse("37491：001023",0).port);}
    @Test public void onlyBoundedNumericPortsAreAccepted(){for(String value:new String[]{"0","65536","-1","1.5","127.0.0.1:12345","22;id","$1234"}){try{PairingInput.port(value);fail(value);}catch(IllegalArgumentException expected){}}assertEquals(65535,PairingInput.port("65535"));}
    @Test public void invalidOrExtraCodeTokensAreRejected(){for(String value:new String[]{"12345","1234567","abcdef","12 123456 extra","-1 123456","12345 12 34",""}){try{PairingInput.parse(value,0);fail(value);}catch(IllegalArgumentException expected){}}}
}
