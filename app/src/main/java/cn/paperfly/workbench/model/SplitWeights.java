package cn.paperfly.workbench.model;

import java.util.*;

/** Normalized pane shares keyed by stable window identity, independent of devices and pixels. */
public final class SplitWeights {
    private SplitWeights() {}
    public static Map<String,Float> equal(List<AppTarget> apps) {
        Map<String,Float> weights=new LinkedHashMap<>();
        for(AppTarget app:apps)weights.put(app.id,1f/apps.size());return weights;
    }
    public static Map<String,Float> legacy(List<AppTarget> apps,float first) {
        if(!Float.isFinite(first)||first<.2f||first>.8f)throw new IllegalArgumentException("Invalid legacy split");
        Map<String,Float> weights=new LinkedHashMap<>();
        for(int i=0;i<apps.size();i++)weights.put(apps.get(i).id,apps.size()==1?1:i==0?first:(1-first)/(apps.size()-1));return weights;
    }
    public static Map<String,Float> validate(List<AppTarget> apps,Map<String,Float> weights) {
        if(apps.isEmpty()||weights.size()!=apps.size())throw new IllegalArgumentException("Missing pane weights");
        Map<String,Float> checked=new LinkedHashMap<>();double sum=0;
        for(AppTarget app:apps){Float value=weights.get(app.id);
            if(value==null||!Float.isFinite(value)||value<=0||value>1)throw new IllegalArgumentException("Invalid pane weight");
            checked.put(app.id,value);sum+=value;}
        if(Math.abs(sum-1)>.0001)throw new IllegalArgumentException("Pane weights must sum to one");
        return Collections.unmodifiableMap(checked);
    }
    public static Map<String,Float> ordered(List<AppTarget> apps,List<Float> weights) {
        if(apps.size()!=weights.size())throw new IllegalArgumentException("Missing pane weights");
        Map<String,Float> result=new LinkedHashMap<>();for(int i=0;i<apps.size();i++)result.put(apps.get(i).id,weights.get(i));return result;
    }
    public static Map<String,Float> reconcile(List<AppTarget> apps,Map<String,Float> previous) {
        int kept=0;float sum=0;for(AppTarget app:apps)if(previous.containsKey(app.id)){kept++;sum+=previous.get(app.id);}
        if(kept==0)return equal(apps);Map<String,Float> result=new LinkedHashMap<>();
        float retained=kept/(float)apps.size();
        for(AppTarget app:apps)result.put(app.id,previous.containsKey(app.id)?previous.get(app.id)*retained/sum:1f/apps.size());return result;
    }
    public static Map<String,Float> join(Map<String,Float> first,Map<String,Float> second) {
        Map<String,Float> result=new LinkedHashMap<>();float share=first.size()/(float)(first.size()+second.size());
        for(Map.Entry<String,Float> entry:first.entrySet())result.put(entry.getKey(),entry.getValue()*share);
        for(Map.Entry<String,Float> entry:second.entrySet())if(result.put(entry.getKey(),entry.getValue()*(1-share))!=null)throw new IllegalArgumentException("Duplicate pane");
        return result;
    }
}
