package cn.paperfly.workbench.model;

import java.util.Objects;

/** Persisted launch identity. Android task/display IDs belong only to a live session. */
public final class AppTarget {
    public final String id;
    public final String component;
    public final String packageName;

    public AppTarget(String component) {
        this(null,component);
    }
    public AppTarget(String id,String component) {
        Objects.requireNonNull(component, "component");
        int slash = component.indexOf('/');
        if (slash <= 0 || slash == component.length() - 1 || component.indexOf('/', slash + 1) >= 0
                || component.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Invalid app component");
        packageName = component.substring(0, slash);
        String activity = component.substring(slash + 1);
        this.component = packageName + "/" + (activity.startsWith(".") ? packageName + activity : activity);
        this.id=id==null?this.component:id;
        if(!this.id.equals(this.component)&&!this.id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid window identity");
    }

    public boolean additionalInstance(){return !id.equals(component);}

    @Override public boolean equals(Object other) {
        return other instanceof AppTarget && id.equals(((AppTarget) other).id)&&component.equals(((AppTarget) other).component);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
