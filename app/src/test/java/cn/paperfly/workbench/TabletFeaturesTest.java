package cn.paperfly.workbench;
import cn.paperfly.workbench.model.*;
import cn.paperfly.workbench.data.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class TabletFeaturesTest {
    private static final class Disk implements WorkspaceStorage {String value;boolean fail;public String read(){return value;}public String legacyComponents(){return "[]";}public void write(String value)throws IOException{if(fail)throw new IOException("full");this.value=value;}}
    private WorkspaceDefinition workspace(){return new WorkspaceDefinition("current","Original",Arrays.asList(WorkspaceCard.single("a-card",new AppTarget("a/.Main")),WorkspaceCard.single("b-card",new AppTarget("b/.Main"))),WorkspaceStyle.DEFAULT);}
    @Test public void migrationSixKeepsStableIdentityAndProtectsFailedCommit()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();JSONObject old=new JSONObject(codec.encode(new WorkspaceLibrary(workspace(),Collections.emptyList())));LegacyWorkspaceFixture.downgrade(old,6);
        Disk disk=new Disk();disk.value=old.toString();String original=disk.value;disk.fail=true;WorkspaceRepository repo=new WorkspaceRepository(disk);assertThrows(IOException.class,repo::load);assertEquals(original,disk.value);
        disk.fail=false;WorkspaceDefinition loaded=repo.load().current;assertEquals("a-card",loaded.cards.get(0).id);assertTrue(loaded.options.pinnedApps.isEmpty());assertTrue(loaded.options.dockVisible);assertFalse(loaded.options.dockAutoHide);
        assertEquals(WorkspaceCodec.SCHEMA_VERSION,new JSONObject(disk.value).getInt("schemaVersion"));assertEquals("b-card",new WorkspaceRepository(disk).load().current.cards.get(1).id);
    }
    @Test public void pinsVisibilityAndMinimizeSurviveRestartWithoutMutatingSavedRecipe()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();Disk disk=new Disk();disk.value=codec.encode(new WorkspaceLibrary(workspace(),Collections.emptyList()));WorkspaceRepository repo=new WorkspaceRepository(disk);
        WorkspaceOptions options=WorkspaceOptions.DEFAULT.pin("missing/.Main",true).pin("b/.Main",true).movePin(1,-1).remember("a/.Main").minimized("a/a.Main",true).autoHide(true);
        WorkspaceDefinition changed=workspace().withOptions(options);repo.saveCurrent(changed);WorkspaceDefinition saved=repo.saveAs(changed,"Saved");
        WorkspaceLibrary reopened=new WorkspaceRepository(disk).load();assertEquals(Arrays.asList("b/b.Main","missing/missing.Main"),reopened.current.options.pinnedApps);assertTrue(reopened.current.options.minimizedWindows.contains("a/a.Main"));assertTrue(reopened.current.options.dockAutoHide);
        assertTrue(reopened.saved.get(0).options.minimizedWindows.isEmpty());assertTrue(reopened.saved.get(0).options.recentApps.isEmpty());repo.rename(saved.id,"Renamed");assertEquals(options.pinnedApps,repo.load().saved.get(0).options.pinnedApps);
        repo.saveCurrent(changed.withOptions(options.pin("b/.Main",false)));assertEquals(2,repo.load().saved.get(0).options.pinnedApps.size());assertTrue(repo.activate(saved.id).options.minimizedWindows.isEmpty());
    }
    @Test public void removingWindowsPrunesOnlyTheirMinimizedIdentity(){WorkspaceDraft draft=new WorkspaceDraft(workspace().withOptions(WorkspaceOptions.DEFAULT.minimized("a/a.Main",true).pin("a/.Main",true)));draft.removeWindow("a/a.Main");assertTrue(draft.snapshot().options.minimizedWindows.isEmpty());assertEquals(Collections.singletonList("a/a.Main"),draft.snapshot().options.pinnedApps);assertEquals("b-card",draft.snapshot().cards.get(0).id);}
    @Test public void corruptPreferencesAndFutureVersionPreserveSource()throws Exception{
        WorkspaceCodec codec=new WorkspaceCodec();JSONObject valid=new JSONObject(codec.encode(new WorkspaceLibrary(workspace(),Collections.emptyList())));
        for(int type=0;type<3;type++){JSONObject broken=new JSONObject(valid.toString());if(type==0)broken.put("schemaVersion",99);if(type==1)broken.getJSONObject("current").getJSONObject("options").put("minimizedWindows",new JSONArray().put("not-a-member"));if(type==2)broken.getJSONObject("current").getJSONObject("options").put("pinnedApps",new JSONArray().put("a/.Main").put("a/.Other"));Disk disk=new Disk();disk.value=broken.toString();String original=disk.value;assertThrows(IOException.class,()->new WorkspaceRepository(disk).load());assertEquals(original,disk.value);}
    }
    @Test public void newPreferenceWriteCanRetryWithoutPublishingFailedState()throws Exception {WorkspaceCodec codec=new WorkspaceCodec();Disk disk=new Disk();disk.value=codec.encode(new WorkspaceLibrary(workspace(),Collections.emptyList()));WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition before=repo.load().current;String original=disk.value;WorkspaceDefinition changed=before.withOptions(before.options.pin("a/.Main",true));disk.fail=true;assertThrows(IOException.class,()->repo.saveCurrent(changed));assertSame(before,repo.load().current);assertEquals(original,disk.value);disk.fail=false;repo.saveCurrent(changed);assertEquals(1,new WorkspaceRepository(disk).load().current.options.pinnedApps.size());}
    @Test public void dockKeepsInstancesButDeduplicatesPinnedAndRecentPackages(){List<DockModel.Entry> entries=DockModel.entries(Arrays.asList("a/.Main","missing/.Main"),Arrays.asList(new DockModel.Window(1,"a/.Main"),new DockModel.Window(2,"a/.Main"),new DockModel.Window(3,"b/.Main")),Arrays.asList("a/.Main","b/.Main","c/.Main","c/.Other","d/.Main","e/.Main","f/.Main"));assertEquals(7,entries.size());assertEquals(1,entries.get(0).id);assertEquals(-1,entries.get(1).id);assertEquals(2,entries.get(2).id);assertEquals("c/c.Main",entries.get(4).component);assertEquals(DockModel.RECENT,entries.get(6).section);}
}
