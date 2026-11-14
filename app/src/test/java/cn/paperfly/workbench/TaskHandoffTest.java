package cn.paperfly.workbench;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TaskHandoffTest {
    private static TaskHandoff.Task task(int id,int display,int user){return new TaskHandoff.Task(id,display,user,new Object());}
    private static final class Fake implements TaskHandoff.Backend {
        final List<TaskHandoff.Task> tasks;final Set<Integer> hidden=new HashSet<>(),background=new HashSet<>();
        final List<Integer> moved=new ArrayList<>();int failMove=-1;boolean failHide,failRestore;
        Fake(TaskHandoff.Task...tasks){this.tasks=Arrays.asList(tasks);}
        public List<TaskHandoff.Task> snapshot(){return tasks;}
        public void hide(List<TaskHandoff.Task> selected)throws Exception{
            for(TaskHandoff.Task task:selected)hidden.add(task.id);
            if(failHide)throw new Exception("partial hide");
        }
        public void moveToMain(TaskHandoff.Task task)throws Exception{
            assertTrue("A transferring app must not appear over Home",hidden.contains(task.id));
            if(task.id==failMove)throw new Exception("move failed");moved.add(task.id);
        }
        public void restoreBehind(List<TaskHandoff.Task> selected)throws Exception{
            for(TaskHandoff.Task task:selected){background.add(task.id);hidden.remove(task.id);}
            if(failRestore)throw new Exception("restore failed");
        }
    }
    @Test public void returnsOnlyOwnedDisplayAndUserWithoutTouchingHome()throws Exception{
        Fake f=new Fake(task(1,10,0),task(2,11,0),task(3,0,0),task(4,12,0),task(5,10,10),task(6,-1,0));
        TaskHandoff.returnBehind(Map.of(10,0,11,0,0,0,-1,0),f);
        assertEquals(Arrays.asList(1,2),f.moved);assertEquals(Set.of(1,2),f.background);assertTrue(f.hidden.isEmpty());
    }
    @Test public void singleRemovalDoesNotTouchRemainingStageDisplays()throws Exception{
        Fake f=new Fake(task(1,10,0),task(2,11,0),task(3,12,0),task(4,0,0));
        TaskHandoff.returnBehind(Collections.singletonMap(11,0),f);
        assertEquals(Collections.singletonList(2),f.moved);assertEquals(Set.of(2),f.background);assertTrue(f.hidden.isEmpty());
    }
    @Test public void partialTransferStillRestoresEverySelectedTask(){
        Fake f=new Fake(task(1,10,0),task(2,11,0));f.failMove=2;
        Exception e=assertThrows(Exception.class,()->TaskHandoff.returnBehind(Map.of(10,0,11,0),f));
        assertEquals("move failed",e.getMessage());assertEquals(Collections.singletonList(1),f.moved);
        assertEquals(Set.of(1,2),f.background);assertTrue(f.hidden.isEmpty());
    }
    @Test public void partialHideFailureIsUndoneBeforeCallerReleasesDisplays(){
        Fake f=new Fake(task(1,10,0),task(2,11,0));f.failHide=true;
        assertThrows(Exception.class,()->TaskHandoff.returnBehind(Map.of(10,0,11,0),f));
        assertTrue(f.moved.isEmpty());assertTrue(f.hidden.isEmpty());assertEquals(Set.of(1,2),f.background);
    }
    @Test public void missingTokensAndEndedSessionsDoNotMutateTasks()throws Exception{
        Fake f=new Fake(new TaskHandoff.Task(1,10,0,null),task(2,11,0));
        TaskHandoff.returnBehind(Map.of(10,0),f);TaskHandoff.returnBehind(Collections.emptyMap(),f);
        assertTrue(f.moved.isEmpty());assertTrue(f.background.isEmpty());
    }
    @Test public void reportsCleanupFailureWithoutLosingTransferFailure(){
        Fake f=new Fake(task(1,10,0));f.failMove=1;f.failRestore=true;
        Exception e=assertThrows(Exception.class,()->TaskHandoff.returnBehind(Map.of(10,0),f));
        assertEquals("move failed",e.getMessage());assertEquals("restore failed",e.getSuppressed()[0].getMessage());
    }
    @Test public void cleanupFailureIsNotReportedAsSuccessfulReturn(){
        Fake f=new Fake(task(1,10,0));f.failRestore=true;
        assertEquals("restore failed",assertThrows(Exception.class,()->TaskHandoff.returnBehind(Map.of(10,0),f)).getMessage());
    }
}
