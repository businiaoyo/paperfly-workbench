package cn.paperfly.workbench.data;

import cn.paperfly.workbench.model.WorkspaceDefinition;
import cn.paperfly.workbench.model.WorkspaceLibrary;
import java.io.IOException;

/** Synchronous, serialized repository. The Android facade runs all calls on its storage executor. */
public final class WorkspaceRepository {
    private final WorkspaceStorage storage;
    private final WorkspaceCodec codec = new WorkspaceCodec();
    private WorkspaceLibrary library;
    private String committed;

    public WorkspaceRepository(WorkspaceStorage storage) { this.storage = storage; }

    public synchronized WorkspaceLibrary load() throws IOException {
        if (library != null) return library;
        String existing = storage.read();
        WorkspaceLibrary loaded = existing == null ? codec.migrateLegacy(storage.legacyComponents()) : codec.decode(existing);
        String encoded = codec.encode(loaded);
        if (existing == null || !existing.equals(encoded)) storage.write(encoded); // Commit migrations before publishing the cache.
        library = loaded;
        committed = encoded;
        return library;
    }

    public synchronized void saveCurrent(WorkspaceDefinition current) throws IOException {
        commit(load().withCurrent(current));
    }
    public synchronized WorkspaceDefinition enterRecent(java.util.List<String> components)throws IOException{
        WorkspaceDefinition current=cn.paperfly.workbench.model.RecentWorkspace.merge(load().current,components);
        saveCurrent(current);return current;
    }
    public synchronized WorkspaceDefinition saveAs(WorkspaceDefinition current,String name) throws IOException {
        if(name==null||name.trim().isEmpty())throw new IOException("请输入组合名称");
        WorkspaceDefinition saved=current.withOptions(current.options.recipe()).named(java.util.UUID.randomUUID().toString(),name.trim());
        java.util.List<WorkspaceDefinition> list=new java.util.ArrayList<>(load().saved);list.add(saved);
        commit(new WorkspaceLibrary(load().current,list));return saved;
    }
    public synchronized WorkspaceDefinition activate(String id) throws IOException {
        WorkspaceDefinition saved=find(id);
        WorkspaceDefinition current=saved.named(WorkspaceDefinition.CURRENT_ID,saved.name);
        saveCurrent(current);return current;
    }
    public synchronized void rename(String id,String name) throws IOException {
        if(name==null||name.trim().isEmpty())throw new IOException("请输入组合名称");
        WorkspaceDefinition saved=find(id);java.util.List<WorkspaceDefinition> list=new java.util.ArrayList<>(load().saved);
        list.set(list.indexOf(saved),saved.named(saved.id,name.trim()));
        commit(new WorkspaceLibrary(load().current,list));
    }
    public synchronized void overwrite(String id,WorkspaceDefinition current) throws IOException {
        WorkspaceDefinition saved=find(id);java.util.List<WorkspaceDefinition> list=new java.util.ArrayList<>(load().saved);
        list.set(list.indexOf(saved),current.withOptions(current.options.recipe()).named(saved.id,saved.name));
        commit(new WorkspaceLibrary(load().current,list));
    }
    public synchronized void delete(String id) throws IOException {
        WorkspaceDefinition saved=find(id);java.util.List<WorkspaceDefinition> list=new java.util.ArrayList<>(load().saved);list.remove(saved);
        commit(new WorkspaceLibrary(load().current,list));
    }
    private WorkspaceDefinition find(String id)throws IOException {
        for(WorkspaceDefinition saved:load().saved)if(saved.id.equals(id))return saved;
        throw new IOException("这个工作台组合已删除");
    }
    private void commit(WorkspaceLibrary updated)throws IOException {
        String encoded=codec.encode(updated);if(encoded.equals(committed))return;
        storage.write(encoded);library=updated;committed=encoded;
    }
}
