package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public class InputPolicyTest {
 @Test public void systemActionsCannotCrossTheWindowBoundary(){for(int code:new int[]{0,3,4,5,6,24,25,26,27,79,85,91,164,187,219,223,224,10000})assertFalse("code "+code,InputPolicy.allowedKey(code));}
 @Test public void lettersNavigationAndModifiersRemainAvailable(){for(int code:new int[]{7,19,23,29,54,59,60,61,62,66,67,92,93,111,112,113,114,115,117,124,131,163})assertTrue("code "+code,InputPolicy.allowedKey(code));}
}
