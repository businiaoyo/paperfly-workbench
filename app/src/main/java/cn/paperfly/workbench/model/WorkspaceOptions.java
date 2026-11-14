package cn.paperfly.workbench.model;

import java.util.*;

/** Persistent workspace preferences and recoverable window visibility; never runtime resources. */
public final class WorkspaceOptions {
    public static final WorkspaceOptions DEFAULT=new WorkspaceOptions(Collections.emptyList(),Collections.emptyList(),Collections.emptySet(),true,false);
    public final List<String> pinnedApps,recentApps;
    public final Set<String> minimizedWindows;
    public final boolean dockVisible,dockAutoHide;
    public WorkspaceOptions(List<String> pinned,List<String> recent,Set<String> minimized,boolean visible,boolean autoHide){
        pinnedApps=components(pinned,24);recentApps=components(recent,8);
        if(minimized.size()>1000||minimized.stream().anyMatch(id->id==null||id.isEmpty()))throw new IllegalArgumentException("Invalid minimized windows");
        minimizedWindows=Collections.unmodifiableSet(new LinkedHashSet<>(minimized));dockVisible=visible;dockAutoHide=autoHide;
    }
    private static List<String> components(List<String> values,int limit){
        if(values.size()>limit)throw new IllegalArgumentException("Too many Dock entries");
        List<String> result=new ArrayList<>();Set<String> packages=new HashSet<>();
        for(String value:values){AppTarget app=new AppTarget(value);if(!packages.add(app.packageName))throw new IllegalArgumentException("Duplicate Dock application");result.add(app.component);}
        return Collections.unmodifiableList(result);
    }
    public WorkspaceOptions pin(String component,boolean value){
        AppTarget target=new AppTarget(component);List<String> pins=new ArrayList<>(pinnedApps);pins.removeIf(c->new AppTarget(c).packageName.equals(target.packageName));if(value)pins.add(target.component);
        if(pins.equals(pinnedApps))return this;return new WorkspaceOptions(pins,recentApps,minimizedWindows,dockVisible,dockAutoHide);
    }
    public WorkspaceOptions movePin(int from,int delta){
        int to=from+delta;if(Math.abs(delta)!=1||from<0||to<0||from>=pinnedApps.size()||to>=pinnedApps.size())return this;
        List<String> pins=new ArrayList<>(pinnedApps);Collections.swap(pins,from,to);return new WorkspaceOptions(pins,recentApps,minimizedWindows,dockVisible,dockAutoHide);
    }
    public WorkspaceOptions remember(String component){
        AppTarget target=new AppTarget(component);List<String> recent=new ArrayList<>(recentApps);recent.removeIf(c->new AppTarget(c).packageName.equals(target.packageName));recent.add(0,target.component);if(recent.size()>8)recent.remove(8);
        return recent.equals(recentApps)?this:new WorkspaceOptions(pinnedApps,recent,minimizedWindows,dockVisible,dockAutoHide);
    }
    public WorkspaceOptions minimized(String id,boolean value){
        if(minimizedWindows.contains(id)==value)return this;Set<String> ids=new LinkedHashSet<>(minimizedWindows);if(value)ids.add(id);else ids.remove(id);return new WorkspaceOptions(pinnedApps,recentApps,ids,dockVisible,dockAutoHide);
    }
    public WorkspaceOptions retainWindows(Set<String> valid){Set<String> ids=new LinkedHashSet<>(minimizedWindows);ids.retainAll(valid);return ids.equals(minimizedWindows)?this:new WorkspaceOptions(pinnedApps,recentApps,ids,dockVisible,dockAutoHide);}
    public WorkspaceOptions visible(boolean value){return value==dockVisible?this:new WorkspaceOptions(pinnedApps,recentApps,minimizedWindows,value,dockAutoHide);}
    public WorkspaceOptions autoHide(boolean value){return value==dockAutoHide?this:new WorkspaceOptions(pinnedApps,recentApps,minimizedWindows,dockVisible,value);}
    public WorkspaceOptions recipe(){return new WorkspaceOptions(pinnedApps,Collections.emptyList(),Collections.emptySet(),dockVisible,dockAutoHide);}
}
