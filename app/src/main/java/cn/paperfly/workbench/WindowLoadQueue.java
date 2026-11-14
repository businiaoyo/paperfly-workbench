package cn.paperfly.workbench;

import java.util.LinkedList;

/** Session-only launch pacing. Owned by the UI thread; no Android or persistent state. */
final class WindowLoadQueue<K> {
    static final long SETTLE_MS=900, REUSE_SETTLE_MS=180, FRAME_TIMEOUT_MS=8000, INTERACTION_MS=500;
    private final LinkedList<K> pending=new LinkedList<>();
    private K active;
    private boolean opened,frame,cancelled;
    private long deadline,nextAt,settle=SETTLE_MS;

    void add(K key){if(key!=active&&!pending.contains(key))pending.add(key);}
    void prioritize(K key){if(pending.remove(key))pending.addFirst(key);}
    K next(long now){
        if(active!=null||pending.isEmpty()||now<nextAt)return null;
        active=pending.removeFirst();opened=frame=cancelled=false;settle=SETTLE_MS;return active;
    }
    void opened(K key,long now){
        opened(key,now,false);
    }
    void opened(K key,long now,boolean reusedTask){
        if(key!=active)return;opened=true;deadline=now+FRAME_TIMEOUT_MS;
        settle=reusedTask?REUSE_SETTLE_MS:SETTLE_MS;
        if(frame||cancelled)finish(now);
    }
    void frame(K key,long now){if(key!=active)return;frame=true;if(opened)finish(now);}
    void failed(K key,long now){if(key==active)finish(now);else pending.remove(key);}
    void cancel(K key,long now){
        pending.remove(key);if(key!=active)return;cancelled=true;
        // Keep the slot until the in-flight Binder call returns; cancellation cannot overlap launches.
        if(opened)finish(now);
    }
    void clear(long now){pending.clear();if(active!=null)cancel(active,now);}
    K expire(long now){
        if(active==null||!opened||now<deadline)return null;
        K expired=active;finish(now);return expired;
    }
    void interaction(long now){nextAt=Math.max(nextAt,now+INTERACTION_MS);}
    boolean busy(){return active!=null||!pending.isEmpty();}
    private void finish(long now){active=null;nextAt=Math.max(nextAt,now+settle);}
}
