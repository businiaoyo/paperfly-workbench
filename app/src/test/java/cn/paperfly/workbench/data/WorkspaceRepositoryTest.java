package cn.paperfly.workbench.data;

import cn.paperfly.workbench.model.*;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class WorkspaceRepositoryTest {
    private static final class Disk implements WorkspaceStorage {
        String document,legacy="[\"a/.Main\",\"b/.Main\"]";int writes;boolean fail;
        public String read(){return document;}
        public String legacyComponents(){return legacy;}
        public void write(String value)throws IOException{if(fail)throw new IOException("Disk full");document=value;writes++;}
    }
    private final WorkspaceCodec codec=new WorkspaceCodec();

    @Test public void migrationIsOneTimeAndKeepsOrderAndStableIdsAcrossProcessRestart() throws Exception {
        Disk disk=new Disk();WorkspaceLibrary initial=new WorkspaceRepository(disk).load();
        String firstId=initial.current.cards.get(0).id;
        assertEquals("a/a.Main",initial.current.cards.get(0).apps.get(0).component);
        assertEquals("b/b.Main",initial.current.cards.get(1).apps.get(0).component);
        disk.legacy="[]";WorkspaceLibrary reopened=new WorkspaceRepository(disk).load();
        assertEquals(firstId,reopened.current.cards.get(0).id);assertEquals(2,reopened.current.cards.size());assertEquals(1,disk.writes);
    }

    @Test public void legacyMigrationKeepsUnavailableNamesAndSkipsInvalidAndDuplicateEntries() throws Exception {
        WorkspaceLibrary result=codec.migrateLegacy("[\"gone/.Main\",\"a/.Main\",\"a/.Other\",null,42,\"broken\"]");
        assertEquals(2,result.current.cards.size());assertEquals("gone/gone.Main",result.current.cards.get(0).focusedWindowId);
        assertTrue(codec.migrateLegacy("[]").current.cards.isEmpty());
        assertThrows(IOException.class,()->codec.migrateLegacy("bad json"));
    }

    @Test public void schemaRoundTripPreservesGroupsFocusSavedIdsAndStyle() throws Exception {
        WorkspaceDefinition current=codec.migrateLegacy("[]").current;
        AppTarget a=new AppTarget("a/.Main"),b=new AppTarget("b/.Main");
        WorkspaceCard group=new WorkspaceCard("group",Arrays.asList(a,b),b.component);
        WorkspaceStyle style=new WorkspaceStyle(0xff123456,0xffabcdef,0xff778899,90,170,.23f);
        WorkspaceDefinition preset=new WorkspaceDefinition("preset-writing","阅读与笔记",Collections.singletonList(group),style);
        WorkspaceLibrary reopened=codec.decode(codec.encode(new WorkspaceLibrary(current,Collections.singletonList(preset))));
        WorkspaceDefinition saved=reopened.saved.get(0);
        assertEquals("preset-writing",saved.id);assertEquals("阅读与笔记",saved.name);
        assertEquals("group",saved.cards.get(0).id);assertEquals(2,saved.cards.get(0).apps.size());assertEquals(b.component,saved.cards.get(0).focusedWindowId);
        assertArrayEquals(style.backgroundColors(),saved.style.backgroundColors());assertEquals(170,saved.style.landscapeRailDp);
        assertEquals(.23f,saved.style.maxRailFraction,0);
    }

    @Test public void updatingCurrentDraftDoesNotOverwriteSavedRecipe() throws Exception {
        Disk disk=new Disk();WorkspaceLibrary initial=codec.migrateLegacy(disk.legacy);
        WorkspaceDefinition saved=new WorkspaceDefinition("preset","学习",initial.current.cards,initial.current.style);
        disk.document=codec.encode(new WorkspaceLibrary(initial.current,Collections.singletonList(saved)));
        WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDraft draft=new WorkspaceDraft(repo.load().current);
        draft.remove("a/.Main");repo.saveCurrent(draft.snapshot());
        WorkspaceLibrary reopened=new WorkspaceRepository(disk).load();
        assertEquals(1,reopened.current.cards.size());assertEquals(2,reopened.saved.get(0).cards.size());
        assertEquals("preset",reopened.saved.get(0).id);
    }

    @Test public void failedWriteKeepsCommittedDataAndCanBeRetried() throws Exception {
        Disk disk=new Disk();WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition before=repo.load().current;
        String good=disk.document;WorkspaceDraft draft=new WorkspaceDraft(before);draft.add("c/.Main");disk.fail=true;
        assertThrows(IOException.class,()->repo.saveCurrent(draft.snapshot()));assertEquals(good,disk.document);assertSame(before,repo.load().current);
        disk.fail=false;repo.saveCurrent(draft.snapshot());assertEquals(3,new WorkspaceRepository(disk).load().current.cards.size());
    }

    @Test public void failedMigrationDoesNotPublishAnUnsavedCache() throws Exception {
        Disk disk=new Disk();disk.fail=true;WorkspaceRepository repo=new WorkspaceRepository(disk);
        assertThrows(IOException.class,repo::load);assertNull(disk.document);
        disk.fail=false;assertEquals(2,repo.load().current.cards.size());assertEquals(1,disk.writes);
    }

    @Test public void corruptOrFutureFilesAreNeverReplacedWithLegacyOrEmptyData() throws Exception {
        String valid=codec.encode(codec.migrateLegacy("[]"));
        JSONObject missingCurrent=new JSONObject(valid);missingCurrent.remove("current");
        for(String invalid:Arrays.asList("broken",new JSONObject(valid).put("schemaVersion",99).toString(),
                missingCurrent.toString())) {
            Disk disk=new Disk();disk.document=invalid;WorkspaceRepository repo=new WorkspaceRepository(disk);
            assertThrows(IOException.class,repo::load);
            assertThrows(IOException.class,()->repo.saveCurrent(codec.migrateLegacy("[]").current));
            assertEquals(invalid,disk.document);assertEquals(0,disk.writes);
        }
    }

    @Test public void unchangedSnapshotsDoNotCauseDiskWrites() throws Exception {
        Disk disk=new Disk();WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition current=repo.load().current;
        repo.saveCurrent(current);repo.saveCurrent(current.withCards(current.cards));assertEquals(1,disk.writes);
    }
    @Test public void savedCommandsKeepIdsAndDraftIsIndependent()throws Exception{
        Disk disk=new Disk();WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition initial=repo.load().current;
        WorkspaceDefinition saved=repo.saveAs(initial,"写作");WorkspaceDraft d=new WorkspaceDraft(initial);d.remove("a/.Main");repo.saveCurrent(d.snapshot());
        repo.rename(saved.id,"阅读");assertEquals(saved.id,repo.load().saved.get(0).id);
        WorkspaceDefinition loaded=repo.activate(saved.id);assertEquals(2,loaded.cards.size());assertEquals("current",loaded.id);
        repo.overwrite(saved.id,d.snapshot());assertEquals(1,repo.load().saved.get(0).cards.size());assertEquals(2,repo.load().current.cards.size());
        repo.delete(saved.id);assertTrue(repo.load().saved.isEmpty());assertEquals(2,repo.load().current.cards.size());assertThrows(IOException.class,()->repo.activate(saved.id));
    }
    @Test public void schemaOneMigratesToStyleAndSplitDefaultsWithoutLosingIds()throws Exception{
        JSONObject root=new JSONObject(codec.encode(codec.migrateLegacy("[\"a/.Main\"]")));cn.paperfly.workbench.LegacyWorkspaceFixture.downgrade(root,1);
        JSONObject current=root.getJSONObject("current"),style=current.getJSONObject("style"),card=current.getJSONArray("cards").getJSONObject(0);
        for(String key:new String[]{"railRight","compactCards","primaryScale","backgroundUri"})style.remove(key);
        card.remove("axis");card.remove("firstWeight");String id=card.getString("id");
        WorkspaceLibrary library=codec.decode(root.toString());assertEquals(id,library.current.cards.get(0).id);assertEquals(.5f,library.current.cards.get(0).firstWeight,0);assertFalse(library.current.style.railRight);assertEquals(1f,library.current.style.primaryScale,0);
        assertEquals(WorkspaceCodec.SCHEMA_VERSION,new JSONObject(codec.encode(library)).getInt("schemaVersion"));
    }
    @Test public void schemaTwoPreservesImageRailAndScale()throws Exception{
        WorkspaceDefinition current=codec.migrateLegacy("[]").current;
        WorkspaceStyle style=new WorkspaceStyle(1,2,3,100,170,.25f,true,true,.8f,"content://images/1");
        WorkspaceLibrary decoded=codec.decode(codec.encode(new WorkspaceLibrary(new WorkspaceDefinition("current","custom",current.cards,style),Collections.emptyList())));
        assertEquals("content://images/1",decoded.current.style.backgroundUri);assertTrue(decoded.current.style.railRight);assertTrue(decoded.current.style.compactCards);assertEquals(.8f,decoded.current.style.primaryScale,0);
    }
    @Test public void existingSchemaMigrationCommitsBeforeCacheAndRetriesOnWriteFailure()throws Exception{
        Disk disk=new Disk();JSONObject old=new JSONObject(codec.encode(codec.migrateLegacy(disk.legacy)));cn.paperfly.workbench.LegacyWorkspaceFixture.downgrade(old,2);
        JSONObject style=old.getJSONObject("current").getJSONObject("style");style.remove("aspectRatio");disk.document=old.toString();String untouched=disk.document;
        WorkspaceRepository repo=new WorkspaceRepository(disk);disk.fail=true;assertThrows(IOException.class,repo::load);assertEquals(untouched,disk.document);
        disk.fail=false;String id=old.getJSONObject("current").getJSONArray("cards").getJSONObject(0).getString("id");assertEquals(id,repo.load().current.cards.get(0).id);
        assertEquals(WorkspaceCodec.SCHEMA_VERSION,new JSONObject(disk.document).getInt("schemaVersion"));
    }
    @Test public void savedThreeAppSetOpensAllThreeWithoutChangingTheSavedList()throws Exception{
        Disk disk=new Disk();WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition before=repo.load().current;
        WorkspaceDraft selection=new WorkspaceDraft(new WorkspaceDefinition("current","选择应用",Collections.emptyList(),before.style));
        for(String app:Arrays.asList("browser/.Main","notes/.Main","chat/.Main"))selection.add(app);
        WorkspaceDefinition saved=repo.saveAs(selection.snapshot(),"办公");
        assertSame(before,repo.load().current);assertEquals(3,saved.cards.size());assertTrue(saved.cards.stream().allMatch(card->card.apps.size()==1));
        WorkspaceDefinition first=repo.activate(saved.id);assertArrayEquals(new String[]{"browser/browser.Main","notes/notes.Main","chat/chat.Main"},new WorkspaceDraft(first).components());
        WorkspaceDraft changed=new WorkspaceDraft(first);changed.add("extra/.Main");repo.saveCurrent(changed.snapshot());
        WorkspaceDefinition reopened=new WorkspaceRepository(disk).activate(saved.id);
        assertEquals(3,reopened.cards.size());assertArrayEquals(new WorkspaceDraft(first).components(),new WorkspaceDraft(reopened).components());
        assertEquals(saved.id,new WorkspaceRepository(disk).load().saved.get(0).id);
    }
    @Test public void schemaThreeAddsExplicitWallpaperFlagWithoutChangingExistingStyles()throws Exception{
        Disk disk=new Disk();JSONObject old=new JSONObject(codec.encode(codec.migrateLegacy(disk.legacy)));
        cn.paperfly.workbench.LegacyWorkspaceFixture.downgrade(old,3);JSONObject style=old.getJSONObject("current").getJSONObject("style");style.remove("wallpaperBackground");
        style.put("backgroundUri","content://images/kept");disk.document=old.toString();String original=disk.document;
        WorkspaceRepository repo=new WorkspaceRepository(disk);disk.fail=true;assertThrows(IOException.class,repo::load);assertEquals(original,disk.document);
        disk.fail=false;WorkspaceLibrary migrated=repo.load();assertFalse(migrated.current.style.wallpaperBackground);
        assertEquals("content://images/kept",migrated.current.style.backgroundUri);
        assertEquals(old.getJSONObject("current").getJSONArray("cards").getJSONObject(0).getString("id"),migrated.current.cards.get(0).id);
        assertEquals(WorkspaceCodec.SCHEMA_VERSION,new JSONObject(disk.document).getInt("schemaVersion"));
    }
    @Test public void wallpaperRoundTripsAndRecentEntryCannotChangeSavedRecipe()throws Exception{
        Disk disk=new Disk();WorkspaceRepository repo=new WorkspaceRepository(disk);WorkspaceDefinition initial=repo.load().current;
        assertTrue(initial.style.wallpaperBackground);WorkspaceDefinition saved=repo.saveAs(initial,"原来的组合");
        String before=codec.encode(new WorkspaceLibrary(initial,Collections.singletonList(saved)));
        repo.enterRecent(Arrays.asList("c/.Main","b/.Main"));
        WorkspaceLibrary reopened=new WorkspaceRepository(disk).load();assertEquals(3,reopened.current.cards.size());
        assertEquals("c/c.Main",reopened.current.cards.get(0).focusedWindowId);assertTrue(reopened.current.style.wallpaperBackground);
        assertEquals(before,codec.encode(new WorkspaceLibrary(initial,reopened.saved)));
        String committed=disk.document;disk.fail=true;assertThrows(IOException.class,()->repo.enterRecent(Collections.singletonList("d/.Main")));
        assertEquals(committed,disk.document);assertEquals(3,repo.load().current.cards.size());
        disk.fail=false;repo.enterRecent(Collections.singletonList("d/.Main"));assertEquals(4,new WorkspaceRepository(disk).load().current.cards.size());
    }
    @Test public void invalidWallpaperFieldDoesNotOverwriteTheFile()throws Exception{
        Disk disk=new Disk();JSONObject invalid=new JSONObject(codec.encode(codec.migrateLegacy(disk.legacy)));
        invalid.getJSONObject("current").getJSONObject("style").remove("wallpaperBackground");disk.document=invalid.toString();
        assertThrows(IOException.class,()->new WorkspaceRepository(disk).load());assertEquals(0,disk.writes);
        assertThrows(IllegalArgumentException.class,()->new WorkspaceStyle(1,2,3,100,190,.2f,false,false,1f,"content://photo",0,true));
    }
}
