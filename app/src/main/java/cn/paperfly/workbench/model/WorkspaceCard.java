package cn.paperfly.workbench.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/** A card is a group, including the existing one-app case. ID survives reordering/renaming. */
public final class WorkspaceCard {
    public final String id;
    public final List<AppTarget> apps;
    public final String focusedWindowId;
    /** Legacy accessor only; schema 8 persists all paneWeights instead. */
    public final float firstWeight;
    public final Map<String,Float> paneWeights;
    public final List<Float> weights;
    public final boolean freeform;
    public final Map<String,WindowPlacement> placements;
    public final int axis; // 0: adaptive, 1: horizontal, 2: vertical

    public WorkspaceCard(String id, List<AppTarget> apps, String focusedWindowId) {
        this(id,apps,focusedWindowId,SplitWeights.equal(apps),0,false,Collections.emptyMap());
    }
    public WorkspaceCard(String id,List<AppTarget> apps,String focusedWindowId,float firstWeight,int axis) {
        this(id,apps,focusedWindowId,firstWeight,axis,false,Collections.emptyMap());
    }
    public WorkspaceCard(String id,List<AppTarget> apps,String focusedWindowId,float firstWeight,int axis,
                         boolean freeform,Map<String,WindowPlacement> placements) {
        this(id,apps,focusedWindowId,SplitWeights.legacy(apps,firstWeight),axis,freeform,placements);
    }
    public WorkspaceCard(String id,List<AppTarget> apps,String focusedWindowId,Map<String,Float> weights,int axis,
                         boolean freeform,Map<String,WindowPlacement> placements) {
        this.paneWeights=SplitWeights.validate(apps,weights);
        this.weights=Collections.unmodifiableList(new ArrayList<>(this.paneWeights.values()));
        LinkedHashMap<String,WindowPlacement> positions=new LinkedHashMap<>();
        for(int i=0;i<apps.size();i++) {
            String key=apps.get(i).id;
            positions.put(key,placements.containsKey(key)?Objects.requireNonNull(placements.get(key)):WindowPlacement.initial(i,apps.size()));
        }
        for(String key:placements.keySet())if(apps.stream().noneMatch(a->a.id.equals(key)))throw new IllegalArgumentException("Unknown placed member");
        this.freeform=freeform;this.placements=Collections.unmodifiableMap(positions);
        if(axis<0||axis>2)throw new IllegalArgumentException("Invalid split");
        this.firstWeight=apps.size()==1?.5f:this.weights.get(0);this.axis=axis;
        this.id = requireId(id);
        if (apps.isEmpty()) throw new IllegalArgumentException("Empty card");
        HashSet<String> packages = new HashSet<>(),windowIds=new HashSet<>();
        for (AppTarget app : apps)
            if (!windowIds.add(app.id)||(!app.additionalInstance()&&!packages.add(app.packageName))) throw new IllegalArgumentException("Duplicate app in card");
        if (apps.stream().noneMatch(app -> app.id.equals(focusedWindowId)))
            throw new IllegalArgumentException("Focused app is not in card");
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        this.focusedWindowId = focusedWindowId;
    }

    public WorkspaceCard withFocus(String component) {
        Map<String,WindowPlacement> ordered=new LinkedHashMap<>(placements);
        if(freeform&&!focusedWindowId.equals(component)){
            List<String> keys=new ArrayList<>(placements.keySet());keys.sort(java.util.Comparator.comparingInt(k->placements.get(k).order));keys.remove(component);keys.add(component);
            for(int i=0;i<keys.size();i++)ordered.put(keys.get(i),placements.get(keys.get(i)).withOrder(i));
        }
        return new WorkspaceCard(id,apps,component,paneWeights,axis,freeform,ordered);
    }
    public WorkspaceCard withApps(List<AppTarget> members,String focus) {
        return withApps(members,focus,SplitWeights.reconcile(members,paneWeights));
    }
    public WorkspaceCard withApps(List<AppTarget> members,String focus,Map<String,Float> weights) {
        Map<String,WindowPlacement> kept=new LinkedHashMap<>();
        for(AppTarget member:members)if(placements.containsKey(member.id))kept.put(member.id,placements.get(member.id));
        return new WorkspaceCard(id,members,focus,weights,axis,freeform,kept);
    }
    public WorkspaceCard withFreeform(boolean value) {
        return new WorkspaceCard(id,apps,focusedWindowId,paneWeights,axis,value,placements);
    }
    public WorkspaceCard withPlacement(String component,WindowPlacement placement) {
        Map<String,WindowPlacement> changed=new LinkedHashMap<>(placements);changed.put(component,placement.withOrder(placements.get(component).order));
        return new WorkspaceCard(id,apps,focusedWindowId,paneWeights,axis,freeform,changed);
    }
    public WorkspaceCard withSplit(float weight,int direction) {
        return new WorkspaceCard(id,apps,focusedWindowId,weight,direction,false,placements);
    }
    public WorkspaceCard withSplit(List<Float> weights,int direction) {
        return new WorkspaceCard(id,apps,focusedWindowId,SplitWeights.ordered(apps,weights),direction,false,placements);
    }

    public static WorkspaceCard single(String id, AppTarget app) {
        return new WorkspaceCard(id, Collections.singletonList(app), app.id);
    }

    static String requireId(String id) {
        if (Objects.requireNonNull(id, "id").trim().isEmpty()) throw new IllegalArgumentException("Empty ID");
        return id;
    }
}
