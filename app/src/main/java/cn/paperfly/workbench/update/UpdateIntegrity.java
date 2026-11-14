package cn.paperfly.workbench.update;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Streaming verification also used again before handing an APK to the system installer. */
public final class UpdateIntegrity {
    private UpdateIntegrity(){}
    public static void copy(InputStream input,OutputStream output,long expected,String sha256,BooleanSupplier cancelled,LongConsumer progress)throws Exception{
        if(expected<=0||expected>UpdateRelease.MAX_APK_BYTES)throw new IOException("更新包大小无效");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long total=0,last=0;
        progress.accept(0);
        while(true){
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("更新操作已取消");
            int count=input.read(buffer);if(count<0)break;
            total+=count;if(total>expected)throw new IOException("更新包大小超出预期");
            digest.update(buffer,0,count);output.write(buffer,0,count);
            if(total-last>=262144){last=total;progress.accept(total);}
        }
        if(total!=expected)throw new IOException("更新包下载不完整，请重试");
        if(!hex(digest.digest()).equalsIgnoreCase(sha256))throw new IOException("更新包校验失败，请重新下载");
        progress.accept(total);
    }
    public static String hex(byte[] bytes){StringBuilder result=new StringBuilder(bytes.length*2);for(byte b:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}
    public static boolean compatibleSigners(boolean installedMultiple,Set<String> installed,boolean candidateMultiple,Set<String> candidate,Set<String> history){
        if(installed.isEmpty()||candidate.isEmpty())return false;
        if(installedMultiple||candidateMultiple)return installedMultiple==candidateMultiple&&installed.equals(candidate);
        return installed.size()==1&&candidate.size()==1&&history.containsAll(installed);
    }
}
