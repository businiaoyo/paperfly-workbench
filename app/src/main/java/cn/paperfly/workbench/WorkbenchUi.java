package cn.paperfly.workbench;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared visual primitives, independent of storage and live windows. */
final class WorkbenchUi {
    static final int INK=0xfff3f6fa,MUTED=0xffb8c3d0,ACCENT=0xff8be3cf,SURFACE=0xfa252e3a,TILE=0xff323d4b,LINE=0x20ffffff,DANGER=0xffffb4ab;
    private WorkbenchUi(){}
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static RippleDrawable touch(Context c,int color,float radius){return new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(c,color,radius),shape(c,0xffffffff,radius));}
    static TextView text(Context c,String value,int sp,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    static LinearLayout column(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);return b;}
    static void space(LinearLayout box,int height){box.addView(new View(box.getContext()),new LinearLayout.LayoutParams(1,dp(box.getContext(),height)));}
    static void section(LinearLayout box,String label){Context c=box.getContext();TextView t=text(c,label,12,MUTED);t.setPadding(dp(c,4),dp(c,20),0,dp(c,10));box.addView(t);}
    static ImageView icon(Context c,WorkbenchIcon.Kind kind,int color){ImageView v=new ImageView(c);v.setImageDrawable(new WorkbenchIcon(kind,color));v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static ImageButton iconButton(Context c,WorkbenchIcon.Kind kind,String label){ImageButton b=new ImageButton(c);b.setImageDrawable(new WorkbenchIcon(kind,INK));b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setBackground(touch(c,0,24));b.setContentDescription(label);b.setTooltipText(label);return b;}
    static Button button(Context c,String label,boolean primary){Button b=new Button(c);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setTextColor(primary?0xff15372f:INK);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setMinWidth(0);b.setMinimumWidth(0);b.setStateListAnimator(null);b.setBackground(touch(c,primary?ACCENT:TILE,14));return b;}
    static LinearLayout row(Context c,WorkbenchIcon.Kind glyph,String label,String detail,boolean arrow,boolean danger){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));row.setMinimumHeight(dp(c,56));row.setBackground(touch(c,0,14));
        if(glyph!=null)row.addView(icon(c,glyph,danger?DANGER:INK),new LinearLayout.LayoutParams(dp(c,22),dp(c,22)));
        LinearLayout words=column(c);TextView title=text(c,label,15,danger?DANGER:INK);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(title);
        if(detail!=null&&!detail.isEmpty()){TextView subtitle=text(c,detail,12,MUTED);subtitle.setPadding(0,dp(c,5),0,0);subtitle.setMaxLines(2);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(subtitle);}
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1);wl.leftMargin=dp(c,glyph==null?0:14);row.addView(words,wl);
        if(arrow){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,16),dp(c,16));lp.leftMargin=dp(c,8);row.addView(icon(c,WorkbenchIcon.Kind.NEXT,MUTED),lp);}
        row.setFocusable(true);return row;
    }
    static EditText input(Context c,String hint){EditText input=new EditText(c);input.setSingleLine();input.setTextSize(16);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint(hint);input.setPadding(dp(c,16),dp(c,15),dp(c,16),dp(c,15));input.setBackground(shape(c,TILE,14));input.setSelectAllOnFocus(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});return input;}
    static Switch toggle(Context c,LinearLayout box,String label,String detail,boolean checked){
        LinearLayout row=row(c,null,label,detail,false,false);Switch toggle=new Switch(c);toggle.setShowText(false);toggle.setContentDescription(label);toggle.setChecked(checked);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xffd4dce6}));toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff4c8076,0xff586372}));
        row.addView(toggle);row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));box.addView(row);return toggle;
    }
    static SeekBar slider(Context c,LinearLayout box,String label,int initial,int min,int max,String suffix){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(c,label,14,INK),new LinearLayout.LayoutParams(0,-2,1));TextView value=text(c,"",13,ACCENT);row.addView(value);box.addView(row);
        SeekBar bar=new SeekBar(c);bar.setMax(max-min);bar.setProgress(initial-min);bar.setProgressTintList(ColorStateList.valueOf(ACCENT));bar.setThumbTintList(ColorStateList.valueOf(ACCENT));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff526071));bar.setContentDescription(label);box.addView(bar,new LinearLayout.LayoutParams(-1,dp(c,48)));
        Runnable update=()->value.setText((bar.getProgress()+min)+suffix);update.run();bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});return bar;
    }
    static final class Choices extends LinearLayout {
        private int selected;private final Button[] buttons;
        Choices(Context c,String[] labels,int initial){super(c);setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));setBackground(shape(c,0xff1c2530,14));buttons=new Button[labels.length];
            for(int i=0;i<labels.length;i++){final int index=i;Button b=button(c,labels[i],false);b.setTextSize(12);b.setPadding(dp(c,3),0,dp(c,3),0);b.setMinHeight(dp(c,40));b.setMinimumHeight(dp(c,40));buttons[i]=b;addView(b,new LayoutParams(0,dp(c,44),1));b.setOnClickListener(v->select(index));}select(initial);}
        int selected(){return selected;}
        private void select(int value){selected=value;for(int i=0;i<buttons.length;i++){boolean active=i==value;buttons[i].setSelected(active);buttons[i].setTextColor(active?ACCENT:MUTED);buttons[i].setBackground(touch(getContext(),active?TILE:0,10));}}
    }
}
