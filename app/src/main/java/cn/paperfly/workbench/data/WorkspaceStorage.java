package cn.paperfly.workbench.data;

import java.io.IOException;

/** Persistence port; write must replace the complete document atomically or retain the previous one. */
public interface WorkspaceStorage {
    /** Null only when no document exists. Corruption and I/O failures must throw. */
    String read() throws IOException;
    void write(String document) throws IOException;
    String legacyComponents();
}
