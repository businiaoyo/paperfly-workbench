package cn.paperfly.workbench;
import org.json.*;
/** Build genuine legacy fields, rather than just changing a new document's version number. */
public final class LegacyWorkspaceFixture {
 public static JSONObject downgrade(JSONObject root,int version)throws Exception{
  root.put("schemaVersion",version);convert(root.getJSONObject("current"),version);JSONArray saved=root.getJSONArray("saved");for(int i=0;i<saved.length();i++)convert(saved.getJSONObject(i),version);return root;
 }
 private static void convert(JSONObject definition,int version)throws Exception{
  if(version<7)definition.remove("options");
  JSONArray cards=definition.getJSONArray("cards");for(int i=0;i<cards.length();i++){
   JSONObject card=cards.getJSONObject(i);JSONArray targets=card.getJSONArray("apps");
   if(version<8){card.put("firstWeight",targets.length()==1?.5:card.getJSONObject("paneWeights").getDouble(targets.getJSONObject(0).getString("id")));card.remove("paneWeights");}
   if(version<6){JSONArray old=new JSONArray();for(int j=0;j<targets.length();j++)old.put(targets.getJSONObject(j).getString("component"));card.put("apps",old);card.put("focusedComponent",card.getString("focusedWindowId"));card.remove("focusedWindowId");}
  }
 }
}
