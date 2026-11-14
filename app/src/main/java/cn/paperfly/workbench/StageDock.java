package cn.paperfly.workbench;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Content-sized Dock: only app shortcuts scroll; the add action always stays reachable. */
@android.annotation.SuppressLint("ViewConstructor")
final class StageDock extends LinearLayout {
    static final int HEIGHT_DP=56;
    static final class Drag {final int id;final String component;boolean moved;Drag(Item item){id=item.id;component=item.component;}}
    static final class Item {final int id,section;final String component,name;final Drawable icon;final boolean selected,minimized;
        Item(int id,String component,String name,Drawable icon,boolean selected,boolean minimized,int section){this.id=id;this.component=component;this.name=name;this.icon=icon;this.selected=selected;this.minimized=minimized;this.section=section;}}
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final FrameLayout separator;
    private final ImageButton addButton;
    private final Map<String,Tile> tiles=new LinkedHashMap<>();
    private List<String> structure=Collections.emptyList();
    private Consumer<Item> select,menu;
    private String selectedKey;

    StageDock(Context context){
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(6),dp(4),dp(6),dp(4));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xd5334052,0xdc253142});
        background.setCornerRadius(dp(20));background.setStroke(dp(1),0x2effffff);setBackground(background);setClipToOutline(true);setElevation(dp(4));
        scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);scroll.setFillViewport(false);
        row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,dp(48)));
        addView(scroll,new LayoutParams(0,dp(48),1));separator=divider();addView(separator,new LayoutParams(dp(12),dp(48)));
        addButton=WorkbenchUi.iconButton(context,WorkbenchIcon.Kind.PLUS,"添加应用");addButton.setPadding(dp(13),dp(13),dp(13),dp(13));
        addButton.setBackground(WorkbenchUi.touch(context,0x0cffffff,14));addView(addButton,new LayoutParams(dp(48),dp(48)));
    }
    void render(List<Item> items,Consumer<Item> select,Consumer<Item> menu,Runnable add){
        this.select=select;this.menu=menu;addButton.setOnClickListener(v->add.run());
        List<String> next=new ArrayList<>();Map<String,Tile> retained=new LinkedHashMap<>();String active=null;int previous=-1;
        for(Item item:items){
            if(previous>=0&&previous!=item.section)next.add("section:"+item.section);previous=item.section;
            String key=item.id>=0?"window:"+item.id:"app:"+item.component;next.add(key);
            Tile tile=tiles.get(key);if(tile==null)tile=new Tile();tile.bind(item);retained.put(key,tile);if(item.selected)active=key;
        }
        if(!next.equals(structure)){
            row.removeAllViews();for(int i=0;i<next.size();i++){String key=next.get(i);Tile tile=retained.get(key);
                LayoutParams lp=new LayoutParams(dp(tile==null?12:48),dp(48));
                if(tile!=null&&i+1<next.size()&&retained.containsKey(next.get(i+1)))lp.rightMargin=dp(4);
                row.addView(tile==null?divider():tile,lp);
            }structure=next;
        }
        tiles.clear();tiles.putAll(retained);scroll.setVisibility(items.isEmpty()?GONE:VISIBLE);separator.setVisibility(items.isEmpty()?GONE:VISIBLE);
        if(!Objects.equals(selectedKey,active)){selectedKey=active;String focus=active;
            post(()->{if(!Objects.equals(selectedKey,focus))return;Tile tile=tiles.get(focus);if(tile==null)return;
                int x=scroll.getScrollX();if(tile.getLeft()<x)scroll.smoothScrollTo(tile.getLeft(),0);
                else if(tile.getRight()>x+scroll.getWidth())scroll.smoothScrollTo(tile.getRight()-scroll.getWidth(),0);
            });
        }
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        row.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(dp(48),MeasureSpec.EXACTLY));
        int apps=scroll.getVisibility()==GONE?0:row.getMeasuredWidth()+dp(12);
        int width=resolveSize(getPaddingLeft()+getPaddingRight()+apps+dp(48),widthSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),heightSpec);
    }
    private FrameLayout divider(){FrameLayout holder=new FrameLayout(getContext());View line=new View(getContext());line.setBackground(WorkbenchUi.shape(getContext(),0x32ffffff,1));holder.addView(line,new FrameLayout.LayoutParams(dp(1),dp(22),Gravity.CENTER));holder.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return holder;}
    private Drawable tileBackground(boolean selected){GradientDrawable shape=WorkbenchUi.shape(getContext(),selected?0x24ffffff:0,13);if(selected)shape.setStroke(dp(1),0x408be3cf);
        return new RippleDrawable(ColorStateList.valueOf(0x24ffffff),shape,WorkbenchUi.shape(getContext(),0xffffffff,13));}
    private int dp(float n){return WorkbenchUi.dp(getContext(),n);}

    private final class Tile extends FrameLayout {
        private final ImageView icon;
        private final View indicator;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Item item;
        Tile(){
            super(StageDock.this.getContext());setFocusable(true);
            icon=new ImageView(getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.TOP|Gravity.CENTER_HORIZONTAL);ip.topMargin=dp(4);addView(icon,ip);
            indicator=new View(getContext()){@Override protected void onDraw(Canvas canvas){
                if(item==null||item.id<0)return;paint.setColor(item.selected?WorkbenchUi.ACCENT:0xb3e0e8f0);
                float x=getWidth()/2f,y=getHeight()/2f;
                if(item.selected||item.minimized){float half=dp(item.selected?5:3);canvas.drawRoundRect(x-half,y-dp(1),x+half,y+dp(1),dp(1),dp(1),paint);}
                else canvas.drawCircle(x,y,dp(1.2f),paint);
            }};
            indicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(dp(16),dp(4),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dp.bottomMargin=dp(3);addView(indicator,dp);
            setOnClickListener(v->select.accept(item));
            setOnLongClickListener(v->{if(!v.startDragAndDrop(android.content.ClipData.newPlainText("工作台窗口",item.name),new View.DragShadowBuilder(v),new Drag(item),0))menu.accept(item);return true;});
            setOnContextClickListener(v->{menu.accept(item);return true;});
        }
        void bind(Item value){
            if(item==null||item.icon!=value.icon){Drawable source=value.icon;
                if(source==null)icon.setImageResource(android.R.drawable.sym_def_app_icon);
                else{Drawable.ConstantState state=source.getConstantState();icon.setImageDrawable(state==null?source:state.newDrawable(getResources()).mutate());}}
            if(item==null||item.selected!=value.selected){setSelected(value.selected);setBackground(tileBackground(value.selected));}
            item=value;String section=item.section==DockModel.PINNED?"固定应用 · ":item.section==DockModel.RECENT?"最近使用 · ":"";
            setContentDescription(section+(item.minimized?"还原 ":item.id<0?"打开 ":"切换 ")+item.name);setTooltipText(section+item.name);indicator.invalidate();
        }
    }
}
