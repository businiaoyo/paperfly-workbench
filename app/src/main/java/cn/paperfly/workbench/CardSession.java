package cn.paperfly.workbench;

import cn.paperfly.workbench.model.*;
import java.util.*;

/** Session-only window bindings and card-level commands. No Android or rendering dependencies. */
final class CardSession {
    private WorkspaceDraft draft;
    private final Map<Integer,String> bindings=new LinkedHashMap<>();
    private WorkspaceDefinition cached;
    private int revision,cachedRevision=-1;
    private final Map<String,WorkspaceCard> componentCards=new HashMap<>();
    private final List<WorkspaceCard> activeCache=new ArrayList<>();
    private void ensure(){
        WorkspaceDefinition value=draft==null?null:draft.snapshot();if(value==cached&&revision==cachedRevision)return;
        cached=value;cachedRevision=revision;componentCards.clear();activeCache.clear();if(value==null)return;
        for(WorkspaceCard card:value.cards){boolean available=false;for(AppTarget app:card.apps){componentCards.put(app.id,card);available|=bindings.containsValue(app.id);}if(available)activeCache.add(card);}
    }
    void bind(WorkspaceDraft draft){this.draft=draft;bindings.clear();revision++;}
    void add(int id,String component){bindings.put(id,component.contains("/")?new AppTarget(component).id:component);revision++;}
    WorkspaceCard card(int id){ensure();return componentCards.get(bindings.get(id));}
    private List<WorkspaceCard> active(){ensure();return activeCache;}
    int size(){return active().size();}
    int slotOf(int id){WorkspaceCard card=card(id);return card==null?-1:active().indexOf(card);}
    boolean primaryCard(int id){return slotOf(id)==0;}
    int representative(int id){
        WorkspaceCard card=card(id);if(card==null)return -1;
        for(AppTarget app:card.apps)for(Map.Entry<Integer,String> binding:bindings.entrySet())if(binding.getValue().equals(app.id))return binding.getKey();
        return -1;
    }
    int primary(){
        List<WorkspaceCard> cards=active();if(cards.isEmpty())return -1;WorkspaceCard card=cards.get(0);
        for(Map.Entry<Integer,String> b:bindings.entrySet())if(b.getValue().equals(card.focusedWindowId))return b.getKey();
        for(Map.Entry<Integer,String> b:bindings.entrySet())if(card.apps.stream().anyMatch(a->a.id.equals(b.getValue())))return b.getKey();
        return -1;
    }
    boolean select(int id){
        WorkspaceCard card=card(id);if(card==null)return false;
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);int from=cards.indexOf(card);
        boolean changed=from!=0||!card.focusedWindowId.equals(bindings.get(id));
        cards.set(from,card.withFocus(bindings.get(id)));
        if(from!=0)Collections.swap(cards,0,from);draft.replace(draft.snapshot().withCards(cards));return changed;
    }
    void remove(int id){String component=bindings.remove(id);revision++;if(component!=null)draft.removeWindow(component);}
    boolean moveSide(int id,int delta){
        WorkspaceCard card=card(id);if(card==null||Math.abs(delta)!=1)return false;
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);int from=cards.indexOf(card),to=from+delta;
        if(from<=0||to<=0||to>=cards.size())return false;Collections.swap(cards,from,to);draft.replace(draft.snapshot().withCards(cards));return true;
    }
    void mergeIntoPrimary(int id){
        WorkspaceCard other=card(id),main=card(primary());if(other==null||main==null||other.id.equals(main.id))return;
        List<AppTarget> apps=new ArrayList<>(main.apps);apps.addAll(other.apps);
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);cards.remove(other);
        cards.set(cards.indexOf(main),main.withApps(apps,main.focusedWindowId,SplitWeights.join(main.paneWeights,other.paneWeights)));
        draft.replace(draft.snapshot().withCards(cards));
    }
    void splitPrimary(){
        WorkspaceCard card=card(primary());if(card==null||card.apps.size()==1)return;
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);int at=cards.indexOf(card);cards.remove(at);
        for(int i=0;i<card.apps.size();i++)cards.add(at+i,WorkspaceCard.single(i==0?card.id:UUID.randomUUID().toString(),card.apps.get(i)));
        draft.replace(draft.snapshot().withCards(cards));
    }
    void splitOptions(float firstWeight,int axis){
        WorkspaceCard card=card(primary());if(card==null)return;List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);
        cards.set(cards.indexOf(card),card.withSplit(firstWeight,axis));draft.replace(draft.snapshot().withCards(cards));
    }
    void splitOptions(List<Float> weights,int axis){
        WorkspaceCard card=card(primary());if(card!=null)replace(card,card.withSplit(weights,axis));
    }

    void freeform(int id,boolean value){replace(card(id),card(id).withFreeform(value));}
    void place(int id,WindowPlacement value){WorkspaceCard card=card(id);if(card!=null)replace(card,card.withPlacement(bindings.get(id),value));}
    private void replace(WorkspaceCard before,WorkspaceCard after){
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);int at=cards.indexOf(before);
        if(at>=0){cards.set(at,after);draft.replace(draft.snapshot().withCards(cards));}
    }
    boolean detach(int id){
        WorkspaceCard card=card(id);if(card==null||card.apps.size()<2)return false;
        String component=bindings.get(id);List<AppTarget> apps=new ArrayList<>(card.apps);
        AppTarget target=apps.stream().filter(a->a.id.equals(component)).findFirst().get();apps.remove(target);
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);int at=cards.indexOf(card);
        cards.set(at,card.withApps(apps,card.focusedWindowId.equals(component)?apps.get(0).id:card.focusedWindowId));
        cards.add(at+1,WorkspaceCard.single(UUID.randomUUID().toString(),target).withFreeform(card.freeform));
        draft.replace(draft.snapshot().withCards(cards));return true;
    }
    void moveMember(int id,int targetId){
        WorkspaceCard source=card(id),target=card(targetId);if(source==null||target==null||source.id.equals(target.id))return;
        String component=bindings.get(id);AppTarget app=source.apps.stream().filter(a->a.id.equals(component)).findFirst().get();
        List<WorkspaceCard> cards=new ArrayList<>(draft.snapshot().cards);List<AppTarget> remaining=new ArrayList<>(source.apps);remaining.remove(app);
        if(remaining.isEmpty())cards.remove(source);else cards.set(cards.indexOf(source),source.withApps(remaining,source.focusedWindowId.equals(component)?remaining.get(0).id:source.focusedWindowId));
        List<AppTarget> joined=new ArrayList<>(target.apps);joined.add(app);cards.set(cards.indexOf(target),target.withApps(joined,component));
        draft.replace(draft.snapshot().withCards(cards));
    }
    List<Integer> ids(){return new ArrayList<>(bindings.keySet());}
}
