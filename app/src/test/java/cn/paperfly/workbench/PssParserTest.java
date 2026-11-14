package cn.paperfly.workbench;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PssParserTest {
    @Test public void readsPssNotRssOrSwap(){assertEquals(219597,PssParser.totalKb("App Summary\n TOTAL PSS:   219597    TOTAL RSS:   420148   TOTAL SWAP (KB): 372\n"));}
    @Test public void missingProcessIsUnavailable(){assertEquals(-1,PssParser.totalKb("No process found for: 512\n"));assertEquals(-1,PssParser.totalKb("TOTAL RSS: 800\n"));}
    @Test public void malformedOversizedValueCannotWrap(){assertEquals(-1,PssParser.totalKb("TOTAL PSS: 99999999999999999999999999"));}
}
