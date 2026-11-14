package cn.paperfly.workbench.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Auto-saved current draft and explicitly saved recipes have separate ownership. */
public final class WorkspaceLibrary {
    public final WorkspaceDefinition current;
    public final List<WorkspaceDefinition> saved;

    public WorkspaceLibrary(WorkspaceDefinition current, List<WorkspaceDefinition> saved) {
        if (!WorkspaceDefinition.CURRENT_ID.equals(current.id)) throw new IllegalArgumentException("Invalid draft ID");
        HashSet<String> ids = new HashSet<>();
        ids.add(current.id);
        for (WorkspaceDefinition definition : saved)
            if (!ids.add(definition.id)) throw new IllegalArgumentException("Duplicate workspace ID");
        this.current = current;
        this.saved = Collections.unmodifiableList(new ArrayList<>(saved));
    }

    public WorkspaceLibrary withCurrent(WorkspaceDefinition draft) { return new WorkspaceLibrary(draft, saved); }
}
