package cn.paperfly.workbench;

import java.util.ArrayDeque;

/** Associates new canvas geometry with the first composed frame after its WM transaction. */
final class CanvasCommitQueue {
    private static final class Commit {
        final CanvasSize size;
        final long after;
        Commit(CanvasSize size,long after){this.size=size;this.after=after;}
    }
    private final ArrayDeque<Commit> pending=new ArrayDeque<>();
    void add(CanvasSize size,long frameAfterNanos){pending.addLast(new Commit(size,frameAfterNanos));}
    CanvasSize ready(long frameNanos){
        CanvasSize size=null;
        while(!pending.isEmpty()&&frameNanos>=pending.peekFirst().after)size=pending.removeFirst().size;
        return size;
    }
    void clear(){pending.clear();}
}
