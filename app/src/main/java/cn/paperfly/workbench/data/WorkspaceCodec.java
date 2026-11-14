package cn.paperfly.workbench.data;

import cn.paperfly.workbench.model.AppTarget;
import cn.paperfly.workbench.model.WorkspaceOptions;
import cn.paperfly.workbench.model.WindowPlacement;
import java.util.Map;
import java.util.LinkedHashMap;
import cn.paperfly.workbench.model.WorkspaceCard;
import cn.paperfly.workbench.model.WorkspaceDefinition;
import cn.paperfly.workbench.model.WorkspaceLibrary;
import cn.paperfly.workbench.model.WorkspaceStyle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Versioned local format. New fields require a schema migration, not silent fallback. */
public final class WorkspaceCodec {
    public static final int SCHEMA_VERSION = 8;

    public WorkspaceLibrary decode(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            int version = root.getInt("schemaVersion");
            if (version < 1 || version > SCHEMA_VERSION) throw new IOException("工作台数据版本不受支持，请使用对应版本的 App");
            List<WorkspaceDefinition> saved = new ArrayList<>();
            JSONArray items = root.getJSONArray("saved");
            for (int i = 0; i < items.length(); i++) saved.add(readDefinition(items.getJSONObject(i),version));
            return new WorkspaceLibrary(readDefinition(root.getJSONObject("current"),version), saved);
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("工作台配置无法读取，原文件已保留", error);
        }
    }

    public String encode(WorkspaceLibrary library) throws IOException {
        try {
            JSONArray saved = new JSONArray();
            for (WorkspaceDefinition definition : library.saved) saved.put(writeDefinition(definition));
            return new JSONObject().put("schemaVersion", SCHEMA_VERSION)
                    .put("current", writeDefinition(library.current)).put("saved", saved).toString();
        } catch (JSONException error) { throw new IOException("工作台配置无法保存", error); }
    }

    public WorkspaceLibrary migrateLegacy(String components) throws IOException {
        try {
            JSONArray values = new JSONArray(components);
            List<WorkspaceCard> cards = new ArrayList<>();
            HashSet<String> packages = new HashSet<>();
            for (int i = 0; i < values.length(); i++) {
                if (!(values.get(i) instanceof String)) continue;
                AppTarget app;
                try { app = new AppTarget(values.getString(i)); }
                catch (IllegalArgumentException invalid) { continue; }
                if (packages.add(app.packageName)) cards.add(WorkspaceCard.single(UUID.randomUUID().toString(), app));
            }
            return new WorkspaceLibrary(new WorkspaceDefinition(WorkspaceDefinition.CURRENT_ID, "当前工作台",
                    cards, WorkspaceStyle.DEFAULT), Collections.emptyList());
        } catch (JSONException error) { throw new IOException("旧工作台列表无法读取，原数据已保留", error); }
    }

    private WorkspaceDefinition readDefinition(JSONObject value,int version) throws JSONException {
        JSONObject style = value.getJSONObject("style");
        WorkspaceStyle preferences = new WorkspaceStyle(style.getInt("backgroundStart"), style.getInt("backgroundMiddle"),
                style.getInt("backgroundEnd"), style.getInt("portraitRailDp"), style.getInt("landscapeRailDp"),
                (float) style.getDouble("maxRailFraction"),version>=2&&style.getBoolean("railRight"),
                version>=2&&style.getBoolean("compactCards"),version>=2?(float)style.getDouble("primaryScale"):1f,
                version>=2?style.getString("backgroundUri"):"",version>=3?(float)style.getDouble("aspectRatio"):0,
                version>=4&&style.getBoolean("wallpaperBackground"));
        List<WorkspaceCard> cards = new ArrayList<>();
        JSONArray items = value.getJSONArray("cards");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            List<AppTarget> apps = new ArrayList<>();
            JSONArray targets = item.getJSONArray("apps");
            for (int j = 0; j < targets.length(); j++) {
                if(version>=6){JSONObject target=targets.getJSONObject(j);apps.add(new AppTarget(target.getString("id"),target.getString("component")));}
                else apps.add(new AppTarget(targets.getString(j)));
            }
            Map<String,WindowPlacement> placements=new LinkedHashMap<>();
            if(version>=5){
                JSONObject positions=item.getJSONObject("placements");
                for(java.util.Iterator<String> keys=positions.keys();keys.hasNext();){
                    String key=keys.next();
                    JSONObject p=positions.getJSONObject(key);
                    placements.put(key,new WindowPlacement((float)p.getDouble("x"),(float)p.getDouble("y"),(float)p.getDouble("width"),(float)p.getDouble("height"),version>=6?p.getInt("order"):0));
                }
                if(placements.size()!=apps.size())throw new IllegalArgumentException("Missing window placement");
            }
            Map<String,Float> weights;
            if(version>=8){weights=new LinkedHashMap<>();JSONObject panes=item.getJSONObject("paneWeights");
                for(java.util.Iterator<String> keys=panes.keys();keys.hasNext();){String key=keys.next();weights.put(key,(float)panes.getDouble(key));}}
            else weights=cn.paperfly.workbench.model.SplitWeights.legacy(apps,version>=2?(float)item.getDouble("firstWeight"):.5f);
            cards.add(new WorkspaceCard(item.getString("id"), apps, version>=6?item.getString("focusedWindowId"):new AppTarget(item.getString("focusedComponent")).id,
                    weights,version>=2?item.getInt("axis"):0,version>=5&&item.getBoolean("freeform"),placements));
        }
        WorkspaceOptions options=WorkspaceOptions.DEFAULT;
        if(version>=7){JSONObject o=value.getJSONObject("options");
            List<String> pinned=strings(o.getJSONArray("pinnedApps")),recent=strings(o.getJSONArray("recentApps")),minimized=strings(o.getJSONArray("minimizedWindows"));
            if(new HashSet<>(minimized).size()!=minimized.size())throw new IllegalArgumentException("Duplicate minimized window");
            options=new WorkspaceOptions(pinned,recent,new java.util.LinkedHashSet<>(minimized),o.getBoolean("dockVisible"),o.getBoolean("dockAutoHide"));
        }
        return new WorkspaceDefinition(value.getString("id"),value.getString("name"),cards,preferences,options);
    }

    private List<String> strings(JSONArray values)throws JSONException {List<String> result=new ArrayList<>();for(int i=0;i<values.length();i++){if(!(values.get(i) instanceof String))throw new IllegalArgumentException("Expected string");result.add(values.getString(i));}return result;}

    private JSONObject writeDefinition(WorkspaceDefinition definition) throws JSONException {
        WorkspaceStyle style = definition.style;
        JSONObject preferences = new JSONObject().put("backgroundStart", style.backgroundStart)
                .put("backgroundMiddle", style.backgroundMiddle).put("backgroundEnd", style.backgroundEnd)
                .put("portraitRailDp", style.portraitRailDp).put("landscapeRailDp", style.landscapeRailDp)
                .put("maxRailFraction", (double) style.maxRailFraction).put("railRight",style.railRight)
                .put("compactCards",style.compactCards).put("primaryScale",(double)style.primaryScale).put("backgroundUri",style.backgroundUri).put("aspectRatio",(double)style.aspectRatio)
                .put("wallpaperBackground",style.wallpaperBackground);
        JSONArray cards = new JSONArray();
        for (WorkspaceCard card : definition.cards) {
            JSONArray apps = new JSONArray();
            for (AppTarget app : card.apps) apps.put(new JSONObject().put("id",app.id).put("component",app.component));
            JSONObject positions=new JSONObject();
            for(Map.Entry<String,WindowPlacement> entry:card.placements.entrySet()){
                WindowPlacement p=entry.getValue();positions.put(entry.getKey(),new JSONObject().put("x",(double)p.x).put("y",(double)p.y).put("width",(double)p.width).put("height",(double)p.height).put("order",p.order));
            }
            JSONObject weights=new JSONObject();for(Map.Entry<String,Float> entry:card.paneWeights.entrySet())weights.put(entry.getKey(),(double)entry.getValue());
            cards.put(new JSONObject().put("id", card.id).put("apps", apps).put("focusedWindowId", card.focusedWindowId).put("paneWeights",weights).put("axis",card.axis).put("freeform",card.freeform).put("placements",positions));
        }
        return new JSONObject().put("id", definition.id).put("name", definition.name).put("cards", cards).put("style", preferences).put("options",new JSONObject().put("pinnedApps",new JSONArray(definition.options.pinnedApps)).put("recentApps",new JSONArray(definition.options.recentApps)).put("minimizedWindows",new JSONArray(definition.options.minimizedWindows)).put("dockVisible",definition.options.dockVisible).put("dockAutoHide",definition.options.dockAutoHide));
    }
}
