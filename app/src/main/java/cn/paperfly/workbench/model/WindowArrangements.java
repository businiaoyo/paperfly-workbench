package cn.paperfly.workbench.model;

/** Explicit layout commands. These intentionally change canvas size, unlike fullscreen transforms. */
public final class WindowArrangements {
    private WindowArrangements() {}
    public static WindowPlacement tile(int index,int count,boolean horizontal) {
        if(count<1||index<0||index>=count||count>36)throw new IllegalArgumentException("Invalid grid");
        int columns=(int)Math.ceil(Math.sqrt(count)),rows=(int)Math.ceil((double)count/columns);
        if(!horizontal){int swap=columns;columns=rows;rows=swap;}
        return new WindowPlacement((index%columns)/(float)columns,(index/columns)/(float)rows,1f/columns,1f/rows,index);
    }
    public static WindowPlacement half(boolean trailing,boolean horizontal){
        return new WindowPlacement(horizontal&&trailing?.5f:0,!horizontal&&trailing?.5f:0,horizontal?.5f:1,horizontal?1:.5f);
    }
}
