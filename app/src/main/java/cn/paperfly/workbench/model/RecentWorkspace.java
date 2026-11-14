package cn.paperfly.workbench.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Recent apps lead the current draft; existing cards (including unavailable apps) remain intact. */
public final class RecentWorkspace {
    public static final int ENTRY_LIMIT=5;
    private RecentWorkspace(){}

    public static WorkspaceDefinition merge(WorkspaceDefinition previous,List<String> components){
        WorkspaceDraft draft=new WorkspaceDraft(previous);
        List<String> order=new ArrayList<>();Set<String> packages=new HashSet<>();
        for(String component:components){
            if(packages.size()>=ENTRY_LIMIT)break;
            AppTarget app;
            try{app=new AppTarget(component);}catch(IllegalArgumentException error){continue;}
            if(!packages.add(app.packageName))continue;
            // Preserve the saved identity/entry point of any app already in a card.
            String existing=null;
            for(WorkspaceCard card:previous.cards)for(AppTarget member:card.apps)
                if(member.packageName.equals(app.packageName))existing=member.id;
            if(existing==null){draft.add(app.component);existing=app.component;}
            order.add(existing);
        }
        if(order.isEmpty())return previous;
        draft.orderVisible(order);
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);
        WorkspaceCard first=cards.get(0);
        cards.set(0,first.withFocus(order.get(0)));
        WorkspaceOptions options=previous.options;
        for(String id:order)options=options.minimized(id,false);
        return previous.withCards(cards).withOptions(options);
    }
}
