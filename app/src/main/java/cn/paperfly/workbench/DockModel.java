package cn.paperfly.workbench;
import cn.paperfly.workbench.model.AppTarget;
import java.util.*;

/** Stable ordered Dock entries. A pinned running window appears once; other instances remain visible. */
final class DockModel {
    static final int PINNED=0,RUNNING=1,RECENT=2;
    static final class Window {final int id;final String component;Window(int id,String component){this.id=id;this.component=new AppTarget(component).component;}}
    static final class Entry {final int id,section;final String component;Entry(int id,String component,int section){this.id=id;this.component=component;this.section=section;}}
    static List<Entry> entries(List<String> pins,List<Window> windows,List<String> recent){
        List<Entry> result=new ArrayList<>();Set<Integer> bound=new HashSet<>();Set<String> packages=new HashSet<>();
        for(String pin:pins){int id=-1;String pkg=new AppTarget(pin).packageName;for(Window w:windows)if(new AppTarget(w.component).packageName.equals(pkg)){id=w.id;bound.add(id);break;}result.add(new Entry(id,pin,PINNED));packages.add(pkg);}
        for(Window w:windows){packages.add(new AppTarget(w.component).packageName);if(!bound.contains(w.id))result.add(new Entry(w.id,w.component,RUNNING));}
        int count=0;for(String value:recent){AppTarget app;try{app=new AppTarget(value);}catch(IllegalArgumentException ignored){continue;}if(packages.add(app.packageName)){result.add(new Entry(-1,app.component,RECENT));if(++count==3)break;}}
        return result;
    }
}
