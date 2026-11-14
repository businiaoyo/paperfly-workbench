package cn.paperfly.workbench.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Serializable workspace recipe; first card is primary. Contains no View, Surface, Binder or task ID. */
public final class WorkspaceDefinition {
    public static final String CURRENT_ID = "current";
    public final String id, name;
    public final List<WorkspaceCard> cards;
    public final WorkspaceStyle style;
    public final WorkspaceOptions options;

    public WorkspaceDefinition(String id, String name, List<WorkspaceCard> cards, WorkspaceStyle style) {
        this(id,name,cards,style,WorkspaceOptions.DEFAULT);
    }
    public WorkspaceDefinition(String id,String name,List<WorkspaceCard> cards,WorkspaceStyle style,WorkspaceOptions options){
        this.id = WorkspaceCard.requireId(id);
        this.name = Objects.requireNonNull(name, "name");
        HashSet<String> ids = new HashSet<>(), packages = new HashSet<>(),windowIds=new HashSet<>();
        for (WorkspaceCard card : cards) {
            if (!ids.add(card.id)) throw new IllegalArgumentException("Duplicate card ID");
            // Each explicit window has one owner; launcher selection still deduplicates default instances.
            for (AppTarget app : card.apps)
                if (!windowIds.add(app.id)||(!app.additionalInstance()&&!packages.add(app.packageName))) throw new IllegalArgumentException("App in multiple cards");
        }
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        this.style = Objects.requireNonNull(style, "style");
        this.options=Objects.requireNonNull(options,"options");
        if(!windowIds.containsAll(options.minimizedWindows))throw new IllegalArgumentException("Unknown minimized window");
    }

    public WorkspaceDefinition withCards(List<WorkspaceCard> cards) {
        HashSet<String> ids=new HashSet<>();for(WorkspaceCard card:cards)for(AppTarget app:card.apps)ids.add(app.id);
        return new WorkspaceDefinition(id, name, cards, style,options.retainWindows(ids));
    }
    public WorkspaceDefinition withOptions(WorkspaceOptions options){return new WorkspaceDefinition(id,name,cards,style,options);}
    public WorkspaceDefinition withStyle(WorkspaceStyle style){return new WorkspaceDefinition(id,name,cards,style,options);}
    public WorkspaceDefinition named(String id,String name){return new WorkspaceDefinition(id,name,cards,style,options);}
}
