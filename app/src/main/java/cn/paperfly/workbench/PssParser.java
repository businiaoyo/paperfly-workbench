package cn.paperfly.workbench;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse the explicit PSS field; never confuse RSS, swap, free RAM or missing data with it. */
final class PssParser {
    private static final Pattern TOTAL=Pattern.compile("(?m)^\\s*TOTAL PSS:\\s*(\\d+)\\b");
    static long totalKb(String text) {
        Matcher match=TOTAL.matcher(text);
        if(!match.find())return -1;
        try{return Long.parseLong(match.group(1));}catch(NumberFormatException error){return -1;}
    }
}
