package cn.paperfly.workbench.update;

import org.json.JSONObject;
import java.net.URI;
import java.util.Locale;

/** The public NAS contract; no credentials, Android objects or workspace configuration. */
public final class UpdateRelease {
    public static final String APP_ID="open-workbench", CHANNEL="stable";
    public static final long MAX_APK_BYTES=128L*1024*1024;
    public final long versionCode,size;
    public final String version,notes,sha256;
    public final URI url;
    private UpdateRelease(long code,String version,String notes,URI url,long size,String sha256){
        this.versionCode=code;this.version=version;this.notes=notes;this.url=url;this.size=size;this.sha256=sha256;
    }
    public static UpdateRelease parse(String body,URI origin,long current)throws Exception{
        JSONObject value=new JSONObject(body);
        require(integer(value,"schemaVersion")==1,"更新清单版本不支持");
        require(APP_ID.equals(value.getString("appId"))&&CHANNEL.equals(value.getString("channel")),"更新清单不属于开放工作台");
        long code=integer(value,"versionCode");
        require(code>current&&code<=Integer.MAX_VALUE,"更新版本必须高于当前版本");
        String version=value.getString("version");require(!version.trim().isEmpty()&&version.length()<=64,"更新版本名称无效");
        String notes=value.optString("notes","");require(notes.length()<=16000,"更新说明过长");
        JSONObject artifact=value.getJSONObject("artifact");
        require("android".equals(artifact.getString("platform"))&&"universal".equals(artifact.getString("arch"))&&"apk".equals(artifact.getString("kind")),"更新包平台不匹配");
        require("application/vnd.android.package-archive".equals(artifact.getString("contentType")),"更新文件不是 APK");
        long size=integer(artifact,"size");require(size>0&&size<=MAX_APK_BYTES,"更新包大小无效");
        String hash=artifact.getString("sha256").toLowerCase(Locale.ROOT);require(hash.matches("[0-9a-f]{64}"),"更新包校验信息无效");
        URI url=new URI(artifact.getString("url"));
        require(url.getHost()!=null&&url.getHost().equalsIgnoreCase(origin.getHost())&&port(url)==port(origin)
                &&url.getScheme().equalsIgnoreCase(origin.getScheme())&&url.getUserInfo()==null&&url.getFragment()==null&&url.getQuery()==null
                &&url.getPath().startsWith("/api/v1/artifacts/")&&url.normalize().equals(url),"更新包地址不属于当前更新中心");
        return new UpdateRelease(code,version,notes,url,size,hash);
    }
    private static int port(URI url){return url.getPort()>=0?url.getPort():"https".equalsIgnoreCase(url.getScheme())?443:80;}
    private static long integer(JSONObject value,String key)throws Exception{
        String raw=String.valueOf(value.get(key));require(raw.matches("[0-9]{1,12}"),"更新清单整数无效");return Long.parseLong(raw);
    }
    static void require(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
}
