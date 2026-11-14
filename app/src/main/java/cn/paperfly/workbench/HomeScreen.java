package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import cn.paperfly.workbench.model.*;
import java.util.*;

/** Responsive home presentation. Actions are delegated; no persistence or live-window operations. */
@SuppressLint("ViewConstructor")
final class HomeScreen extends ScrollView implements AutoCloseable {
    static final int PAPER=0xfff5f6f3,INK=0xff24372f,MUTED=0xff67746b,TEAL=0xff27735d,MINT=0xffc3efdb;
    interface Actions {
        void resume();void recent();void open(String id);void create();void manage();void activate();void help();void retry();
    }
    private final Actions actions;
    private final LinearLayout center,box;
    private final HomeAppAssets assetLoader;
    private Map<String,HomeAppAssets.Entry> assets=Collections.emptyMap();
    private WorkspaceLibrary library;
    private boolean authorized,loaded,launching,recentLoading,closed;
    private String error;
    private LinearLayout activationBanner;
    private TextView status;
    private Button resume,recent;
    private final List<View> configurationActions=new ArrayList<>(),openActions=new ArrayList<>();
    private int lastWidth;
    HomeScreen(Context c,Actions actions){
        super(c);this.actions=actions;assetLoader=new HomeAppAssets(c);
        setFillViewport(true);setVerticalScrollBarEnabled(false);setBackgroundColor(PAPER);
        center=column();center.setGravity(Gravity.CENTER_HORIZONTAL);addView(center);
        box=column();center.addView(box,new LinearLayout.LayoutParams(-1,-2));render();
    }
    void library(WorkspaceLibrary value){
        library=value;error=null;render();
        assetLoader.load(value,result->{assets=result;render();});
    }
    void error(String message){error=message;render();}
    void state(boolean authorized,boolean loaded,boolean launching,boolean recentLoading){
        this.authorized=authorized;this.loaded=loaded;this.launching=launching;this.recentLoading=recentLoading;updateState();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);if(w!=lastWidth){lastWidth=w;post(()->{if(!closed)render();});}
    }
    private void render(){
        int scroll=getScrollY();box.removeAllViews();configurationActions.clear();openActions.clear();
        int width=getWidth()>0?getWidth():getResources().getDisplayMetrics().widthPixels;
        int panelWidth=Math.min(width,dp(1080));boolean wide=panelWidth>=dp(840),small=panelWidth<dp(540);
        box.getLayoutParams().width=panelWidth;int padding=small?20:wide?36:28;box.setPadding(dp(padding),dp(22),dp(padding),dp(28));
        header(small);
        activationBanner=column();activationBanner.setPadding(dp(20),dp(16),dp(20),dp(16));activationBanner.setBackground(shape(0xffe6eee7,20));
        LinearLayout notice=row();LinearLayout words=column();words.addView(text(R.string.home_activation_title,15,INK,true));add(words,text(R.string.home_activation_detail,12,MUTED,false),6);
        notice.addView(words,new LinearLayout.LayoutParams(0,-2,1));Button activate=button(R.string.home_activate,TEAL,0xffffffff);activate.setOnClickListener(v->actions.activate());LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-2,dp(48));al.leftMargin=dp(16);notice.addView(activate,al);activationBanner.addView(notice);add(box,activationBanner,18);
        if(error!=null){LinearLayout failure=row();TextView message=text(error,13,TEAL,false);failure.addView(message,new LinearLayout.LayoutParams(0,-2,1));Button retry=button(R.string.home_retry,0,TEAL);retry.setOnClickListener(v->actions.retry());failure.addView(retry);add(box,failure,16);}
        LinearLayout starts=new LinearLayout(getContext());starts.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        View hero=hero(small);View recents=recents(wide,small);
        if(wide){starts.addView(hero,new LinearLayout.LayoutParams(0,-1,2.1f));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=dp(18);starts.addView(recents,rp);starts.setMinimumHeight(dp(258));}
        else{starts.addView(hero,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(16);starts.addView(recents,rp);}
        // An explicit shared row height prevents MATCH_PARENT cards from expanding to the
        // ScrollView viewport when the activation banner disappears.
        int heroHeight=dp(258+Math.max(0,getResources().getConfiguration().fontScale-1)*190);
        LinearLayout.LayoutParams startsParams=new LinearLayout.LayoutParams(-1,wide?heroHeight:-2);startsParams.topMargin=dp(26);box.addView(starts,startsParams);
        LinearLayout heading=row();LinearLayout labels=column();labels.addView(text(R.string.home_sets,21,INK,true));add(labels,text(R.string.home_saved_hint,12,MUTED,false),6);heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Button manage=button(R.string.home_manage,0,MUTED);manage.setOnClickListener(v->actions.manage());configurationActions.add(manage);heading.addView(manage);
        if(library!=null&&!library.saved.isEmpty()){
            Button create=button(R.string.home_new,0xffe7ece6,TEAL);glyph(create,WorkbenchIcon.Kind.PLUS,TEAL);create.setContentDescription(s(R.string.home_new_accessibility));create.setOnClickListener(v->actions.create());configurationActions.add(create);heading.addView(create);
        }
        add(box,heading,28);
        if(library==null)add(box,text(R.string.home_loading,14,MUTED,false),24);
        else if(library.saved.isEmpty())add(box,emptySets(small),18);
        else savedSets(panelWidth-dp(2*padding));
        updateState();box.requestLayout();post(()->{if(!closed)scrollTo(0,scroll);});
    }
    private void header(boolean small){
        LinearLayout header=row();FrameLayout logo=new FrameLayout(getContext());logo.setBackground(shape(0xffe5ede4,16));ImageView mark=icon(WorkbenchIcon.Kind.SPLIT,TEAL);FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER);logo.addView(mark,ml);header.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout words=column();words.addView(text(R.string.home_name,small?21:24,INK,true));if(!small)add(words,text(R.string.home_tagline,12,MUTED,false),6);
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1);wl.leftMargin=dp(14);header.addView(words,wl);
        status=text("",12,TEAL,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(14),0,dp(14),0);status.setBackground(touch(0xffe7ede5,24));status.setOnClickListener(v->actions.activate());status.setFocusable(true);header.addView(status,new LinearLayout.LayoutParams(-2,dp(48)));
        ImageButton help=new ImageButton(getContext());help.setImageDrawable(new WorkbenchIcon(WorkbenchIcon.Kind.MORE,INK));help.setPadding(dp(12),dp(12),dp(12),dp(12));help.setBackground(touch(0,24));help.setContentDescription(s(R.string.home_help));help.setTooltipText(s(R.string.home_help));help.setOnClickListener(v->actions.help());LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(48),dp(48));hp.leftMargin=dp(8);header.addView(help,hp);box.addView(header);
    }
    private View hero(boolean small){
        LinearLayout hero=new LinearLayout(getContext());hero.setOrientation(small?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(26),dp(26),dp(20),dp(26));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff203f36,0xff345e4f});background.setCornerRadius(dp(26));hero.setBackground(background);
        WorkspaceDefinition current=library==null?null:library.current;boolean empty=current!=null&&current.cards.isEmpty();
        LinearLayout words=column();words.addView(text(empty?R.string.home_workspace:R.string.home_last,12,0xffb5cfbf,false));add(words,text(empty?R.string.home_empty_title:R.string.home_continue_title,small?25:27,0xfff4faf5,true),16);
        TextView description=text(current==null?s(R.string.home_loading):empty?s(R.string.home_empty_detail):summary(current),13,0xffc0d8c9,false);description.setMaxLines(2);description.setEllipsize(TextUtils.TruncateAt.END);description.setLineSpacing(dp(4),1);add(words,description,10);
        if(current!=null&&!empty)add(words,text(countLabel(current),11,0xffa9c6b6,false),8);
        resume=button(R.string.home_continue,MINT,0xff203f36);glyph(resume,WorkbenchIcon.Kind.NEXT,0xff203f36);resume.setContentDescription(s(R.string.home_continue_accessibility));resume.setOnClickListener(v->{if(authorized)actions.resume();else actions.activate();});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(48));bp.topMargin=dp(22);words.addView(resume,bp);
        HomeWorkspacePreview preview=new HomeWorkspacePreview(getContext(),current,assets);
        if(small){hero.addView(words,new LinearLayout.LayoutParams(-1,-2));hero.addView(preview,new LinearLayout.LayoutParams(-1,dp(170)));}
        else{hero.addView(words,new LinearLayout.LayoutParams(0,-2,1.08f));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(206),1);pp.leftMargin=dp(4);hero.addView(preview,pp);}
        return hero;
    }
    private View recents(boolean wide,boolean small){
        LinearLayout card=column();card.setPadding(dp(24),dp(22),dp(24),dp(20));card.setBackground(shape(0xffffffff,26));
        LinearLayout top=row();ImageView symbol=icon(WorkbenchIcon.Kind.COLLECTION,TEAL);symbol.setPadding(dp(11),dp(11),dp(11),dp(11));symbol.setBackground(shape(0xffeff3e8,15));top.addView(symbol,new LinearLayout.LayoutParams(dp(46),dp(46)));
        if(!wide){LinearLayout copy=column();copy.addView(text(R.string.home_recent,18,INK,true));add(copy,text(R.string.home_recent_note,12,MUTED,false),6);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(14);top.addView(copy,cp);}
        card.addView(top);
        if(wide){add(card,text(R.string.home_recent,20,INK,true),17);TextView detail=text(R.string.home_recent_detail,13,MUTED,false);detail.setLineSpacing(dp(4),1);add(card,detail,9);}
        recent=button(R.string.home_recent_action,0,TEAL);recent.setPadding(0,0,0,0);recent.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);glyph(recent,WorkbenchIcon.Kind.NEXT,TEAL);recent.setOnClickListener(v->{if(authorized)actions.recent();else actions.activate();});
        if(wide){card.addView(new View(getContext()),new LinearLayout.LayoutParams(1,0,1));add(card,recent,10);}else if(small)add(card,recent,12);else{LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(48));rp.leftMargin=dp(12);top.addView(recent,rp);}
        return card;
    }
    private View emptySets(boolean small){
        LinearLayout empty=new LinearLayout(getContext());empty.setOrientation(small?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);empty.setGravity(Gravity.CENTER_VERTICAL);empty.setPadding(dp(24),dp(26),dp(24),dp(26));GradientDrawable bg=shape(0xffedf0e9,22);bg.setStroke(dp(1),0xffe1e6dc);empty.setBackground(bg);
        ImageView symbol=icon(WorkbenchIcon.Kind.APPS,0xff8fa68d);empty.addView(symbol,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=column();words.addView(text(R.string.home_sets_empty,17,INK,true));TextView detail=text(R.string.home_sets_detail,13,MUTED,false);detail.setLineSpacing(dp(4),1);add(words,detail,8);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(small?-1:0,-2,small?0:1);wp.leftMargin=small?0:dp(20);wp.topMargin=small?dp(12):0;empty.addView(words,wp);
        Button create=button(R.string.home_new,0xffffffff,TEAL);glyph(create,WorkbenchIcon.Kind.PLUS,TEAL);create.setContentDescription(s(R.string.home_new_accessibility));create.setOnClickListener(v->actions.create());configurationActions.add(create);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(48));cp.leftMargin=small?0:dp(24);cp.topMargin=small?dp(16):0;empty.addView(create,cp);return empty;
    }
    private void savedSets(int width){
        int columns=width>=dp(900)?3:width>=dp(560)?2:1;
        for(int start=0;start<library.saved.size();start+=columns){
            LinearLayout row=new LinearLayout(getContext());
            for(int i=0;i<columns;i++){
                int index=start+i;View tile=index<library.saved.size()?setTile(library.saved.get(index)):new View(getContext());
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=dp(16);row.addView(tile,lp);
            }
            add(box,row,16);
        }
    }
    private View setTile(WorkspaceDefinition definition){
        LinearLayout tile=column();tile.setPadding(dp(22),dp(22),dp(22),dp(20));tile.setBackground(touch(0xffffffff,22));tile.setMinimumHeight(dp(174));
        LinearLayout icons=row();int count=0;for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps){if(count++>=4)continue;ImageView icon=new ImageView(getContext());HomeAppAssets.Entry asset=assets.get(app.component);if(asset!=null&&asset.icon!=null)icon.setImageBitmap(asset.icon);else icon.setImageDrawable(new WorkbenchIcon(WorkbenchIcon.Kind.APPS,TEAL));icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(36),dp(36));if(count>1)ip.leftMargin=dp(10);icons.addView(icon,ip);}
        icons.addView(new View(getContext()),new LinearLayout.LayoutParams(0,1,1));icons.addView(icon(WorkbenchIcon.Kind.NEXT,0xffa0ada5),new LinearLayout.LayoutParams(dp(18),dp(18)));tile.addView(icons);
        TextView title=text(definition.name,18,INK,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);add(tile,title,18);
        TextView apps=text(summary(definition),12,MUTED,false);apps.setMaxLines(2);apps.setEllipsize(TextUtils.TruncateAt.END);apps.setLineSpacing(dp(3),1);add(tile,apps,7);add(tile,text(countLabel(definition),11,TEAL,false),10);
        tile.setContentDescription(getResources().getString(R.string.home_set_open,definition.name,appCount(definition)));tile.setFocusable(true);tile.setOnClickListener(v->{if(authorized)actions.open(definition.id);else actions.activate();});openActions.add(tile);return tile;
    }
    private void updateState(){
        if(status==null)return;status.setText(authorized?R.string.home_ready:R.string.home_inactive);activationBanner.setVisibility(authorized?GONE:VISIBLE);
        boolean enabled=loaded&&!launching;
        resume.setText(launching&&!recentLoading?R.string.home_opening:!authorized?R.string.home_activate_enter:library!=null&&library.current.cards.isEmpty()?R.string.home_start:R.string.home_continue);
        recent.setText(recentLoading?R.string.home_recent_loading:R.string.home_recent_action);
        enable(resume,enabled);enable(recent,enabled);for(View v:openActions)enable(v,enabled);for(View v:configurationActions)enable(v,enabled);
    }
    private static int appCount(WorkspaceDefinition definition){int count=0;for(WorkspaceCard card:definition.cards)count+=card.apps.size();return count;}
    private String countLabel(WorkspaceDefinition definition){boolean split=definition.cards.stream().anyMatch(c->c.apps.size()>1);return getResources().getString(split?R.string.home_split_count:R.string.home_apps_count,appCount(definition));}
    private String summary(WorkspaceDefinition definition){if(assets.isEmpty()&&appCount(definition)>0)return s(R.string.home_loading_apps);List<String> labels=new ArrayList<>();for(WorkspaceCard card:definition.cards)for(AppTarget app:card.apps){HomeAppAssets.Entry entry=assets.get(app.component);labels.add(entry==null?app.packageName:entry.label);}return TextUtils.join(" · ",labels);}
    private void enable(View v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1:.45f);}
    private int dp(float value){return WorkbenchUi.dp(getContext(),value);}
    private String s(int id){return getResources().getString(id);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getContext());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(int id,int sp,int color,boolean medium){return text(s(id),sp,color,medium);}
    private TextView text(String value,int sp,int color,boolean medium){TextView t=WorkbenchUi.text(getContext(),value,sp,color);if(medium)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private GradientDrawable shape(int color,int radius){return WorkbenchUi.shape(getContext(),color,radius);}
    private RippleDrawable touch(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x1527735d),shape(color,radius),shape(0xffffffff,radius));}
    private ImageView icon(WorkbenchIcon.Kind kind,int color){return WorkbenchUi.icon(getContext(),kind,color);}
    private Button button(int id,int color,int ink){Button b=WorkbenchUi.button(getContext(),s(id),false);b.setTextColor(ink);b.setBackground(touch(color,14));b.setTextSize(13);return b;}
    private void glyph(Button b,WorkbenchIcon.Kind kind,int color){WorkbenchIcon icon=new WorkbenchIcon(kind,color);icon.setBounds(0,0,dp(17),dp(17));b.setCompoundDrawablesRelative(null,null,icon,null);b.setCompoundDrawablePadding(dp(8));}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);}
    @Override public void close(){closed=true;assetLoader.close();}
}
