package cn.paperfly.workbench.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Editable current recipe. Group membership and unavailable applications survive edits and loading. */
public final class WorkspaceDraft {
    private WorkspaceDefinition definition;

    public WorkspaceDraft(WorkspaceDefinition definition) {
        this.definition = definition;
    }

    public WorkspaceDefinition snapshot() { return definition; }

    public String[] components() {
        return definition.cards.stream().flatMap(card -> card.apps.stream()).map(app -> app.component).toArray(String[]::new);
    }

    public AppTarget[] targets(){return definition.cards.stream().flatMap(card->card.apps.stream()).toArray(AppTarget[]::new);}
    public void addInstance(AppTarget target){
        for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps)if(app.id.equals(target.id))return;
        List<WorkspaceCard> cards=new ArrayList<>(definition.cards);cards.add(WorkspaceCard.single(UUID.randomUUID().toString(),target));definition=definition.withCards(cards);
    }
    public void removeWindow(String id){
        for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps)if(app.id.equals(id)){removeTarget(app);return;}
    }
    public void add(String component) {
        AppTarget app=new AppTarget(component);
        List<WorkspaceCard> cards=new ArrayList<>(definition.cards);
        for(int i=0;i<cards.size();i++){
            WorkspaceCard card=cards.get(i);List<AppTarget> apps=new ArrayList<>(card.apps);
            for(int j=0;j<apps.size();j++)if(!apps.get(j).additionalInstance()&&apps.get(j).packageName.equals(app.packageName)){
                AppTarget previous=apps.get(j);if(previous.equals(app))return;apps.set(j,app);
                cards.set(i,card.withApps(apps,card.focusedWindowId.equals(previous.id)?app.id:card.focusedWindowId));
                definition=definition.withCards(cards);return;
            }
        }
        cards.add(WorkspaceCard.single(UUID.randomUUID().toString(),app));definition=definition.withCards(cards);
    }
    public void remove(String component){
        removeTarget(new AppTarget(component));
    }
    private void removeTarget(AppTarget app){List<WorkspaceCard> cards=new ArrayList<>();
        for(WorkspaceCard card:definition.cards){
            List<AppTarget> apps=new ArrayList<>(card.apps);
            if(!apps.remove(app)){cards.add(card);continue;}
            if(!apps.isEmpty())cards.add(card.withApps(apps,card.focusedWindowId.equals(app.id)?apps.get(0).id:card.focusedWindowId));
        }
        definition=definition.withCards(cards);
    }
    public void replace(WorkspaceDefinition value){definition=value;}
    public void style(WorkspaceStyle style){definition=definition.withStyle(style);}
    public void options(WorkspaceOptions options){if(options!=definition.options)definition=definition.withOptions(options);}
    public void orderVisible(List<String> components) {
        List<WorkspaceCard> remaining=new ArrayList<>(definition.cards),ordered=new ArrayList<>();
        for(String component:components){String canonical=component.contains("/")?new AppTarget(component).id:component;
            for(int i=0;i<remaining.size();i++)if(remaining.get(i).apps.stream().anyMatch(a->a.id.equals(canonical))){ordered.add(remaining.remove(i));break;}}
        ordered.addAll(remaining);if(!ordered.equals(definition.cards))definition=definition.withCards(ordered);
    }
}
