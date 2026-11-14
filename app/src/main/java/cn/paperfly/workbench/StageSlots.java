package cn.paperfly.workbench;
import java.util.ArrayList;
import java.util.List;

/** Slot 0 is primary. Selection swaps identities, never sorts the other slots. */
final class StageSlots {
    private final List<Integer> slots=new ArrayList<>();
    void add(int id){if(slots.contains(id))throw new IllegalArgumentException("Duplicate window");slots.add(id);}
    boolean select(int id){int i=slots.indexOf(id);if(i<=0)return false;int old=slots.get(0);slots.set(0,id);slots.set(i,old);return true;}
    void remove(int id){slots.remove(Integer.valueOf(id));}
    int primary(){return slots.isEmpty()?-1:slots.get(0);}
    int slotOf(int id){return slots.indexOf(id);}
    int size(){return slots.size();}
    boolean moveSide(int id,int delta){
        int from=slots.indexOf(id),to=from+delta;
        if(from<=0||to<=0||to>=slots.size()||Math.abs(delta)!=1)return false;
        slots.remove(from);slots.add(to,id);return true;
    }
}
