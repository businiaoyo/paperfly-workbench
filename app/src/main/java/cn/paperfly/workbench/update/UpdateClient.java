package cn.paperfly.workbench.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import cn.paperfly.workbench.BuildConfig;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;

/** App-UID network/download adapter. It has no access to the privileged workspace service. */
public final class UpdateClient implements AutoCloseable {
    public static final String BASE="http://192.168.5.53:8787";
    private final Context context;
    private volatile boolean closed;
    private volatile HttpURLConnection active;
    public UpdateClient(Context context){this.context=context.getApplicationContext();}
    private HttpURLConnection open(URI url,String accept)throws Exception{
        if(closed)throw new IOException("更新操作已取消");
        HttpURLConnection connection=(HttpURLConnection)url.toURL().openConnection();active=connection;
        connection.setConnectTimeout(8000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        connection.setRequestProperty("Accept",accept);connection.setRequestProperty("User-Agent","OpenWorkbench/"+BuildConfig.VERSION_NAME);
        if(closed){connection.disconnect();throw new IOException("更新操作已取消");}return connection;
    }
    public UpdateRelease check()throws Exception{
        URI url=URI.create(BASE+"/api/v1/apps/"+UpdateRelease.APP_ID+"/"+UpdateRelease.CHANNEL+"/latest?platform=android&arch=universal&currentVersionCode="+BuildConfig.VERSION_CODE);
        HttpURLConnection connection=open(url,"application/json");
        try{
            int status=connection.getResponseCode();if(status==204)return null;
            if(status!=200)throw new IOException("更新中心暂不可用（"+status+"）");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;long deadline=System.nanoTime()+20_000_000_000L;
                while((count=in.read(buffer))!=-1){if(closed||System.nanoTime()>deadline)throw new IOException("更新检查超时或已取消");if(bytes.size()+count>128*1024)throw new IOException("更新清单过大");bytes.write(buffer,0,count);}
                return UpdateRelease.parse(bytes.toString("UTF-8"),URI.create(BASE),BuildConfig.VERSION_CODE);
            }
        }finally{connection.disconnect();if(active==connection)active=null;}
    }
    public File download(UpdateRelease release,LongConsumer progress)throws Exception{
        File directory=new File(context.getCacheDir(),"updates");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新缓存");
        File partial=new File(directory,"download.part");HttpURLConnection connection=open(release.url,"application/vnd.android.package-archive");
        try{
            if(connection.getResponseCode()!=200)throw new IOException("下载失败，请重新检查更新");
            long length=connection.getContentLengthLong();if(length>=0&&length!=release.size)throw new IOException("更新包大小与清单不一致");
            long deadline=System.nanoTime()+120_000_000_000L;
            try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                UpdateIntegrity.copy(in,out,release.size,release.sha256,()->closed||System.nanoTime()>deadline,progress);out.getFD().sync();
            }
            verifyArchive(partial,release);
            File output=verifiedFile(context);if(!partial.renameTo(output))throw new IOException("无法保存更新包");return output;
        }finally{connection.disconnect();if(active==connection)active=null;partial.delete();}
    }
    public void verify(File apk,UpdateRelease release)throws Exception{
        try(InputStream in=new FileInputStream(apk)){
            UpdateIntegrity.copy(in,new OutputStream(){@Override public void write(int b){}@Override public void write(byte[] b,int off,int len){}},release.size,release.sha256,()->closed,n->{});
        }
        verifyArchive(apk,release);
    }
    private void verifyArchive(File apk,UpdateRelease release)throws Exception{
        PackageManager pm=context.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!context.getPackageName().equals(archive.packageName)||archive.getLongVersionCode()!=release.versionCode
                ||!release.version.equals(archive.versionName)||archive.getLongVersionCode()<=BuildConfig.VERSION_CODE)throw new IOException("更新包的应用或版本不匹配");
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        SigningInfo old=installed.signingInfo,next=archive.signingInfo;
        if(old==null||next==null)throw new IOException("无法核对应用签名");
        Set<String> current=signers(next.getApkContentsSigners()),history=signers(next.getSigningCertificateHistory());
        if(history.isEmpty())history=current;
        if(!UpdateIntegrity.compatibleSigners(old.hasMultipleSigners(),signers(old.getApkContentsSigners()),next.hasMultipleSigners(),current,history))throw new IOException("更新包签名与当前应用不一致");
        if(archive.applicationInfo!=null&&archive.applicationInfo.minSdkVersion>android.os.Build.VERSION.SDK_INT)throw new IOException("此更新需要更高的 Android 版本");
    }
    private static Set<String> signers(Signature[] signatures)throws Exception{
        Set<String> result=new HashSet<>();if(signatures!=null)for(Signature s:signatures)result.add(UpdateIntegrity.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;
    }
    public static File verifiedFile(Context context){return new File(context.getCacheDir(),"updates/verified.apk");}
    @Override public void close(){closed=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
}
