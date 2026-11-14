package cn.paperfly.workbench;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searchable icon library and editable workspace in one focusable, native dialog. */
final class AppPanel extends Dialog {
    static final int INK=Color.rgb(245,248,252), MUTED=Color.rgb(205,216,229);
    static final int PANEL=Color.rgb(29,43,57), TILE=Color.rgb(39,56,71), ACCENT=Color.rgb(117,226,201);
    static final class Item {
        final int id,slot,cardCount; final String component,name; final boolean primary,ready,failed;
        Item(int id,String component,String name,boolean primary,boolean ready,boolean failed,int slot,int cardCount){
            this.id=id;this.slot=slot;this.cardCount=cardCount;this.component=component;this.name=name;this.primary=primary;this.ready=ready;this.failed=failed;
        }
    }
    interface Host {
        List<Item> selected();
        void add(String component);
        void remove(int id);
        void move(int id,int delta);
        void select(int id);
    }
    private static final class Entry {
        final String name,component,packageName,key,section,searchKey;
        final android.graphics.drawable.Drawable icon;
        Entry(ResolveInfo info,Activity activity,android.icu.text.Transliterator latin){
            name=info.loadLabel(activity.getPackageManager()).toString();packageName=info.activityInfo.packageName;
            component=new ComponentName(packageName,info.activityInfo.name).flattenToString();
            key=latin.transliterate(name).toUpperCase(Locale.ROOT).trim();
            section=!key.isEmpty()&&key.charAt(0)>='A'&&key.charAt(0)<='Z'?key.substring(0,1):"#";
            StringBuilder initials=new StringBuilder();for(String word:key.split("\\s+"))if(!word.isEmpty())initials.append(word.charAt(0));
            searchKey=(name+" "+packageName+" "+key+" "+key.replace(" ","")+" "+initials).toLowerCase(Locale.ROOT);
            icon=info.loadIcon(activity.getPackageManager());
        }
    }
    private final Activity activity;
    private final Host host;
    private final Runnable confirmSelection;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final List<Entry> all=new ArrayList<>(),filtered=new ArrayList<>();
    private final List<String> recentPackages=new ArrayList<>();
    private final LibraryAdapter libraryAdapter=new LibraryAdapter();
    private final SelectedAdapter selectedAdapter=new SelectedAdapter();
    private final WallpaperBackdrop backdrop=new WallpaperBackdrop();
    private PickerBackdrop hostBackdrop;
    private LinearLayout box,index;
    private EditText search;
    private GridView grid;
    private ListView selectedList;
    private TextView title,subtitle,empty,hint;
    private Button selectedTab,sortButton;
    private boolean managing,compact,loaded,recentFirst;
    private boolean pinning;
    AppPanel forPins(){pinning=true;return this;}
    private int lastWidth,lastHeight;

    AppPanel(Activity activity,Host host){this(activity,host,null);}
    AppPanel(Activity activity,Host host,Runnable confirmSelection){
        super(activity,R.style.AppDrawerTheme);this.activity=activity;this.host=host;this.confirmSelection=confirmSelection;
    }
    private boolean choosing(){return confirmSelection!=null;}
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(28),dp(14),dp(28),dp(12));
        LinearLayout heading=row();
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);
        title=text(pinning?"固定到 Dock":choosing()?"选择组合应用":"应用",28,INK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(title);
        subtitle=text("",12,MUTED);words.addView(subtitle);
        heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        selectedTab=action("已加入",INK);selectedTab.setOnClickListener(v->tab(!managing));heading.addView(selectedTab,new LinearLayout.LayoutParams(dp(98),dp(48)));
        sortButton=action("A–Z ↓",INK);sortButton.setContentDescription("应用排序");sortButton.setOnClickListener(v->{recentFirst=!recentFirst;sortButton.setText(recentFirst?"最近 ↓":"A–Z ↓");filter();});heading.addView(sortButton,new LinearLayout.LayoutParams(dp(76),dp(48)));
        Button done=action(choosing()?"选好了":"完成",ACCENT);done.setOnClickListener(v->{
            if(choosing()&&host.selected().isEmpty()){android.widget.Toast.makeText(activity,"至少选择一个应用",android.widget.Toast.LENGTH_SHORT).show();return;}
            dismiss();if(choosing())confirmSelection.run();});heading.addView(done,new LinearLayout.LayoutParams(dp(68),dp(48)));
        box.addView(heading,new LinearLayout.LayoutParams(-1,dp(72)));
        search=new EditText(activity);search.setSingleLine();search.setTextSize(15);search.setTextColor(INK);search.setHintTextColor(MUTED);
        search.setHint("搜索应用 · 支持拼音");search.setPadding(dp(20),0,dp(20),0);search.setBackground(round(0x30ffffff,24));
        search.setContentDescription("搜索应用");box.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        FrameLayout contents=new FrameLayout(activity);LinearLayout.LayoutParams contentLp=new LinearLayout.LayoutParams(-1,0,1);contentLp.topMargin=dp(16);box.addView(contents,contentLp);
        grid=new GridView(activity);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(8));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);grid.setPadding(dp(4),dp(8),dp(30),dp(8));grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        grid.setAdapter(libraryAdapter);contents.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        index=new LinearLayout(activity);index.setOrientation(LinearLayout.VERTICAL);index.setGravity(Gravity.CENTER);
        contents.addView(index,new FrameLayout.LayoutParams(dp(28),-1,Gravity.RIGHT));
        selectedList=new ListView(activity);selectedList.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));selectedList.setDividerHeight(dp(8));
        selectedList.setAdapter(selectedAdapter);contents.addView(selectedList,new FrameLayout.LayoutParams(-1,-1));
        empty=text("正在读取应用…",14,MUTED);empty.setGravity(Gravity.CENTER);contents.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        hint=text("",11,MUTED);hint.setPadding(0,dp(6),0,0);box.addView(hint);
        InsetBackdropHost backgroundHost=new InsetBackdropHost(activity);backgroundHost.avoidIme();
        backgroundHost.scene.addView(box,new FrameLayout.LayoutParams(-1,-1));
        setContentView(backgroundHost);getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);getWindow().setNavigationBarContrastEnforced(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.setFitInsetsTypes(0);getWindow().setAttributes(attrs);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        backdrop.apply(getWindow(),activity.getWindowManager(),backgroundHost,true);
        box.getViewTreeObserver().addOnGlobalLayoutListener(this::fitVisibleArea);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter();}
            public void afterTextChanged(android.text.Editable e){}
        });
        tab(false);
        loader.execute(()->{
            List<Entry> entries=new ArrayList<>();List<String> recent=new ArrayList<>();
            try{
                android.icu.text.Transliterator latin=android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII");
                List<ResolveInfo> choices=activity.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
                java.util.Set<String> packages=new java.util.HashSet<>();
                for(ResolveInfo r:choices)if(!r.activityInfo.packageName.equals(activity.getPackageName())&&r.activityInfo.exported&&r.activityInfo.enabled&&r.activityInfo.applicationInfo.enabled&&packages.add(r.activityInfo.packageName)){
                    try{entries.add(new Entry(r,activity,latin));}catch(RuntimeException ignored){}
                }
                entries.sort((a,b)->a.key.compareTo(b.key));
                IWorkbenchService service=BridgeState.service();if(service!=null){
                    Bundle result=service.recentApplications();List<String> values=result.getStringArrayList("components");
                    if(values!=null)for(String value:values){ComponentName name=ComponentName.unflattenFromString(value);if(name!=null)recent.add(name.getPackageName());}
                }
            }catch(Exception ignored){}
            activity.runOnUiThread(()->{if(!isShowing())return;all.clear();all.addAll(entries);recentPackages.addAll(recent);loaded=true;filter();});
        });
    }
    @Override public void show(){
        hostBackdrop=new PickerBackdrop(activity);
        try{super.show();getWindow().setLayout(-1,-1);
            android.view.WindowInsetsController bars=getWindow().getInsetsController();
            if(bars!=null)bars.setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            fitVisibleArea();}
        catch(RuntimeException error){hostBackdrop.close();hostBackdrop=null;backdrop.close();loader.shutdownNow();throw error;}
    }
    private void fitVisibleArea(){
        if(!isShowing()||search==null)return;
        android.graphics.Rect visible=new android.graphics.Rect();getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        android.graphics.Rect bounds=activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        int width=bounds.width(),height=bounds.height();boolean keyboard=height-visible.height()>dp(180);
        subtitle.setVisibility(keyboard?View.GONE:View.VISIBLE);hint.setVisibility(keyboard?View.GONE:View.VISIBLE);
        if(compact!=keyboard){compact=keyboard;libraryAdapter.notifyDataSetChanged();}
        if(width!=lastWidth||height!=lastHeight){
            lastWidth=width;lastHeight=height;grid.setNumColumns(Math.max(3,Math.min(10,(width-dp(78))/dp(100))));
            getWindow().setLayout(-1,-1);
        }
    }
    @Override public void dismiss(){if(hostBackdrop!=null){hostBackdrop.close();hostBackdrop=null;}backdrop.close();super.dismiss();loader.shutdownNow();}
    void refresh(){
        if(subtitle==null)return;int count=host.selected().size();
        subtitle.setText(pinning?"已固定 "+count+" 个 · 点选不会启动应用":choosing()?"已选择 "+count+" 个 · 保存后可一键打开":"已加入 "+count+" 个应用");
        selectedTab.setText(managing?"‹ 全部应用":(pinning?"已固定 ":choosing()?"已选择 ":"已加入 ")+count);
        libraryAdapter.notifyDataSetChanged();selectedAdapter.notifyDataSetChanged();updateEmpty();
    }
    void configurationChanged(){lastWidth=lastHeight=0;fitVisibleArea();}
    private void tab(boolean value){
        managing=value;title.setText(pinning?(value?"固定应用顺序":"固定到 Dock"):value?(choosing()?"组合顺序":"已加入的应用"):(choosing()?"选择组合应用":"应用"));
        search.setVisibility(value?View.GONE:View.VISIBLE);sortButton.setVisibility(value?View.GONE:View.VISIBLE);
        grid.setVisibility(value?View.GONE:View.VISIBLE);selectedList.setVisibility(value?View.VISIBLE:View.GONE);
        if(value){search.clearFocus();activity.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);}
        hint.setText(pinning?(value?"↑↓ 调整固定顺序 · 移除仅取消固定":"选择一个应用固定到 Dock"):choosing()?(value?"点名称设为默认主应用 · ↑↓ 调整顺序":"点图标选择或取消，选好后保存组合"):value?"点应用切换主窗 · ↑↓ 排序 · 移除只退出工作台":"选择一个应用，立即返回工作台");
        updateIndex();refresh();
    }
    private void filter(){
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);filtered.clear();
        for(Entry e:all)if(e.searchKey.contains(q))filtered.add(e);
        if(recentFirst)filtered.sort((a,b)->Integer.compare(recentRank(a),recentRank(b)));
        libraryAdapter.notifyDataSetChanged();grid.smoothScrollToPositionFromTop(0,0,0);updateIndex();updateEmpty();
    }
    private int recentRank(Entry e){int rank=recentPackages.indexOf(e.packageName);return rank<0?Integer.MAX_VALUE:rank;}
    private void updateIndex(){
        if(index==null)return;index.removeAllViews();index.setVisibility(managing||recentFirst||search.length()>0?View.GONE:View.VISIBLE);
        java.util.LinkedHashMap<String,Integer> sections=new java.util.LinkedHashMap<>();
        for(int i=0;i<filtered.size();i++)sections.putIfAbsent(filtered.get(i).section,i);
        for(java.util.Map.Entry<String,Integer> entry:sections.entrySet()){
            TextView letter=text(entry.getKey(),11,INK);letter.setGravity(Gravity.CENTER);letter.setContentDescription("跳转到 "+entry.getKey());
            index.addView(letter,new LinearLayout.LayoutParams(-1,0,1));letter.setOnClickListener(v->grid.setSelection(entry.getValue()));
        }
    }
    private void updateEmpty(){if(empty==null)return;boolean none=managing?host.selected().isEmpty():filtered.isEmpty();empty.setVisibility(none?View.VISIBLE:View.GONE);
        empty.setText(managing?"还没有应用，去「全部应用」选择":!loaded?"正在读取应用…":"没有找到匹配的应用");}
    private Item joined(String packageName){for(Item i:host.selected()){ComponentName c=ComponentName.unflattenFromString(i.component);if(c!=null&&c.getPackageName().equals(packageName))return i;}return null;}
    private final class LibraryAdapter extends BaseAdapter {
        public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,ViewGroup parent){
            Entry entry=filtered.get(pos);Item item=joined(entry.packageName);
            LinearLayout tile=new LinearLayout(activity);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(4),dp(6),dp(4),dp(6));
            tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(compact?90:110)));
            FrameLayout iconBox=new FrameLayout(activity);int size=dp(compact?44:54);
            ImageView icon=new ImageView(activity);icon.setImageDrawable(entry.icon);iconBox.addView(icon,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
            if(item!=null){TextView badge=text("✓",12,0xff16332b);badge.setGravity(Gravity.CENTER);badge.setBackground(round(ACCENT,12));iconBox.addView(badge,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.BOTTOM|Gravity.RIGHT));}
            tile.addView(iconBox,new LinearLayout.LayoutParams(size+dp(8),size+dp(8)));
            TextView name=text(entry.name,13,INK);name.setGravity(Gravity.CENTER);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-1,dp(28));nameLp.topMargin=dp(3);tile.addView(name,nameLp);
            tile.setContentDescription(entry.name+(pinning?(item==null?"，固定应用":"，已固定"):item==null?"，添加应用":"，已加入"));tile.setFocusable(true);
            tile.setOnClickListener(v->{
                if(!isShowing())return;Item selected=joined(entry.packageName);
                if(choosing()){if(selected==null)host.add(entry.component);else host.remove(selected.id);refresh();}
                else{dismiss();if(selected==null)host.add(entry.component);else host.select(selected.id);}
            });
            return tile;
        }
    }
    private final class SelectedAdapter extends BaseAdapter {
        public int getCount(){return host.selected().size();}public Object getItem(int p){return host.selected().get(p);}public long getItemId(int p){return host.selected().get(p).id;}
        public View getView(int pos,View convert,ViewGroup parent){
            List<Item> selected=host.selected();Item item=selected.get(pos);
            LinearLayout line=row();line.setPadding(dp(12),dp(8),dp(8),dp(8));line.setBackground(round(TILE,14));line.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(74)));
            ImageView icon=new ImageView(activity);try{icon.setImageDrawable(activity.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(item.component)));}catch(Exception ignored){}
            line.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
            LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,dp(8),0);
            TextView label=text(item.name,14,INK);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(label);
            words.addView(text(pinning?"固定应用 "+(item.slot+1):choosing()?(item.primary?"默认主应用":"侧栏应用 "+item.slot):item.failed?"无法打开 · 可移除后重试":!item.ready?"正在打开…":item.primary?"主卡片":"侧卡片 "+item.slot,11,item.primary?ACCENT:MUTED));
            line.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.setOnClickListener(v->{host.select(item.id);if(choosing())refresh();else dismiss();});
            Button up=action("↑",INK),down=action("↓",INK),remove=action("移除",Color.rgb(255,175,161));
            up.setContentDescription("上移"+item.name);down.setContentDescription("下移"+item.name);remove.setContentDescription("移除"+item.name);
            up.setEnabled(choosing()?pos>0:item.slot>1);down.setEnabled(choosing()?pos<selected.size()-1:item.slot>0&&item.slot<item.cardCount-1);up.setAlpha(up.isEnabled()?1:.2f);down.setAlpha(down.isEnabled()?1:.2f);
            up.setOnClickListener(v->{host.move(item.id,-1);refresh();});down.setOnClickListener(v->{host.move(item.id,1);refresh();});remove.setOnClickListener(v->{host.remove(item.id);refresh();});
            line.addView(up,new LinearLayout.LayoutParams(dp(38),dp(42)));line.addView(down,new LinearLayout.LayoutParams(dp(38),dp(42)));line.addView(remove,new LinearLayout.LayoutParams(dp(58),dp(42)));
            return line;
        }
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button action(String value,int color){Button b=new Button(activity);b.setText(value);b.setTextColor(color);b.setTextSize(13);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private TextView text(String value,int sp,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(sp);t.setTextColor(color);return t;}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
