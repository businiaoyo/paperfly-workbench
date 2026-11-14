package cn.paperfly.workbench.activation;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public final class ShellV2ResultTest {
    private byte[] packet(int id,byte[] payload)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(id);for(int i=0;i<4;i++)b.write(payload.length>>(8*i));b.write(payload);return b.toByteArray();}
    @Test public void stdoutAndStderrAreFramedUntilExit()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(packet(1,"started".getBytes("UTF-8")));b.write(packet(2,"note".getBytes("UTF-8")));b.write(packet(3,new byte[]{0}));assertEquals("startednote",ShellV2Result.read(new ByteArrayInputStream(b.toByteArray())));}
    @Test public void truncatedOutputCannotLookLikeSuccess()throws Exception{try{ShellV2Result.read(new ByteArrayInputStream(packet(1,"started".getBytes("UTF-8"))));fail();}catch(EOFException expected){}}
    @Test public void failureExitIsRejected()throws Exception{try{ShellV2Result.read(new ByteArrayInputStream(packet(3,new byte[]{1})));fail();}catch(IOException expected){}}
    @Test public void unboundedAndUnknownFramesAreRejected()throws Exception{for(byte[] data:new byte[][]{{1,-1,-1,-1,127},{9,0,0,0,0}}){try{ShellV2Result.read(new ByteArrayInputStream(data));fail();}catch(IOException expected){}}}
}
