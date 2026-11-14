package cn.paperfly.workbench.update;
import org.junit.Test;
import org.json.JSONObject;
import java.net.URI;
import static org.junit.Assert.*;
public class UpdateReleaseTest {
    private static final URI ORIGIN=URI.create("http://192.168.5.53:8787");
    private JSONObject valid()throws Exception{return new JSONObject().put("schemaVersion",1).put("appId","open-workbench").put("channel","stable").put("versionCode",35).put("version","0.15.0").put("notes","更新说明").put("artifact",new JSONObject().put("platform","android").put("arch","universal").put("kind","apk").put("contentType","application/vnd.android.package-archive").put("size",20).put("sha256","a".repeat(64)).put("url",ORIGIN+"/api/v1/artifacts/id/app.apk"));}
    private UpdateRelease parse(JSONObject value)throws Exception{return UpdateRelease.parse(value.toString(),ORIGIN,34);}
    @Test public void acceptsExactContractAndPreservesNotes()throws Exception{UpdateRelease r=parse(valid());assertEquals(35,r.versionCode);assertEquals("更新说明",r.notes);assertEquals(20,r.size);}
    @Test public void rejectsDifferentAppChannelAndSchema()throws Exception{
        for(String[] item:new String[][]{{"appId","mobile-nas"},{"channel","beta"},{"schemaVersion","2"}}){JSONObject v=valid().put(item[0],item[1]);assertThrows(Exception.class,()->parse(v));}
        JSONObject fractional=valid().put("schemaVersion",1.5);assertThrows(Exception.class,()->parse(fractional));
    }
    @Test public void refusesDowngradeSameVersionAndFractionalCodes()throws Exception{
        for(Object code:new Object[]{34,33,-1,35.5,2147483648L}){JSONObject v=valid().put("versionCode",code);assertThrows(Exception.class,()->parse(v));}
    }
    @Test public void rejectsOffOriginCredentialsRedirectPathsAndWrongScheme()throws Exception{
        for(String url:new String[]{"http://other.local:8787/api/v1/artifacts/id/app.apk","http://192.168.5.53/api/v1/artifacts/id/app.apk","https://192.168.5.53:8787/api/v1/artifacts/id/app.apk","http://user@192.168.5.53:8787/api/v1/artifacts/id/app.apk","http://192.168.5.53:8787/api/v1/artifacts/../admin","file:///tmp/app.apk","/api/v1/artifacts/id/app.apk"}){
            JSONObject v=valid();v.getJSONObject("artifact").put("url",url);assertThrows(Exception.class,()->parse(v));
        }
    }
    @Test public void rejectsBadSizeHashAndTarget()throws Exception{
        for(Object size:new Object[]{0,-1,UpdateRelease.MAX_APK_BYTES+1,1.5}){JSONObject v=valid();v.getJSONObject("artifact").put("size",size);assertThrows(Exception.class,()->parse(v));}
        for(String[] pair:new String[][]{{"sha256","abc"},{"platform","darwin"},{"arch","x64"},{"kind","zip"},{"contentType","text/html"}}){JSONObject v=valid();v.getJSONObject("artifact").put(pair[0],pair[1]);assertThrows(Exception.class,()->parse(v));}
    }
    @Test public void rejectsMissingTruncatedAndOversizedMetadata()throws Exception{
        assertThrows(Exception.class,()->UpdateRelease.parse("{",ORIGIN,34));JSONObject v=valid();v.remove("artifact");assertThrows(Exception.class,()->parse(v));
        JSONObject notes=valid().put("notes","n".repeat(16001));assertThrows(Exception.class,()->parse(notes));
    }
}
