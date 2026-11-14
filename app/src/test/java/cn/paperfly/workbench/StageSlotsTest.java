package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;

public final class StageSlotsTest {
    @Test public void swappingUsesSelectedSlotAndLeavesOthersStill(){
        StageSlots s=new StageSlots();for(int i=0;i<4;i++)s.add(i);
        assertTrue(s.select(2));assertEquals(2,s.primary());assertEquals(2,s.slotOf(0));
        assertEquals(1,s.slotOf(1));assertEquals(3,s.slotOf(3));
    }
    @Test public void latestChoiceWinsWithoutWaitingForAnimation(){
        StageSlots s=new StageSlots();for(int i=0;i<3;i++)s.add(i);
        s.select(1);s.select(2);s.select(0);
        assertEquals(0,s.primary());assertEquals(1,s.slotOf(2));assertEquals(2,s.slotOf(1));
    }
    @Test public void removingMainPromotesExistingSideAndAddingKeepsMain(){
        StageSlots s=new StageSlots();s.add(5);s.add(7);s.add(11);s.select(11);s.remove(11);
        assertEquals(7,s.primary());s.add(13);assertEquals(7,s.primary());assertEquals(2,s.slotOf(13));
    }
    @Test public void absentOrCurrentSelectionCannotCorruptSlots(){
        StageSlots s=new StageSlots();s.add(6);assertFalse(s.select(9));assertFalse(s.select(6));s.remove(9);
        assertEquals(6,s.primary());s.remove(6);assertEquals(-1,s.primary());assertEquals(0,s.size());
    }
    @Test public void manyWindowsCanBeReorderedWithoutChangingMain(){
        StageSlots s=new StageSlots();for(int i=0;i<20;i++)s.add(i);
        assertEquals(20,s.size());assertTrue(s.moveSide(19,-1));assertEquals(18,s.slotOf(19));assertEquals(19,s.slotOf(18));assertEquals(0,s.primary());
        assertFalse(s.moveSide(1,-1));assertFalse(s.moveSide(0,1));assertFalse(s.moveSide(18,1));
        s.select(19);assertEquals(19,s.primary());assertEquals(18,s.slotOf(0));
    }
}
