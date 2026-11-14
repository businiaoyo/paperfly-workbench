package cn.paperfly.workbench;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Bounded menu/form surface, preserving the host's Dialog focus and back handling contract. */
final class WorkbenchDialog extends Dialog {
    final LinearLayout body;
    private final Activity activity;
    private final Panel panel;
    private final LinearLayout footer;
    private final int preferredWidth;
    private View anchor;
    private HostModalSurface hostSurface;
    private android.content.DialogInterface.OnDismissListener dismissed;
    WorkbenchDialog(Activity activity,String title,String subtitle,int widthDp){
        super(activity,R.style.WorkbenchDialogTheme);this.activity=activity;preferredWidth=widthDp;requestWindowFeature(Window.FEATURE_NO_TITLE);
        Context c=getContext();panel=new Panel(c);panel.setAccessibilityPaneTitle(title);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(16),dp(16),dp(16));
        GradientDrawable background=WorkbenchUi.shape(c,WorkbenchUi.SURFACE,26);background.setStroke(dp(1),WorkbenchUi.LINE);panel.setBackground(background);panel.setClipToOutline(true);
        LinearLayout heading=new LinearLayout(c);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(8),0,0,dp(12));
        LinearLayout words=WorkbenchUi.column(c);TextView name=WorkbenchUi.text(c,title,22,WorkbenchUi.INK);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));words.addView(name);
        if(subtitle!=null&&!subtitle.isEmpty()){TextView detail=WorkbenchUi.text(c,subtitle,12,WorkbenchUi.MUTED);detail.setPadding(0,dp(6),dp(4),0);detail.setMaxLines(2);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(detail);}
        heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));ImageButton close=WorkbenchUi.iconButton(c,WorkbenchIcon.Kind.CLOSE,"关闭"+title);close.setOnClickListener(v->dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(heading);
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);body=WorkbenchUi.column(c);scroll.addView(body);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        footer=new LinearLayout(c);footer.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(14),0,0);footer.setVisibility(View.GONE);panel.addView(footer);
        setContentView(panel);setCanceledOnTouchOutside(true);
        Window window=getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.20f);window.setElevation(dp(24));window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Contrast comes from the panel and dim layer, not another whole-window blur.
        panel.setOnApplyWindowInsetsListener((v,insets)->{panel.post(this::fit);return insets;});
    }
    WorkbenchDialog anchoredTo(View view){anchor=view;return this;}
    Button action(String label,boolean primary,Runnable action){Button button=WorkbenchUi.button(getContext(),label,primary);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(footer.getChildCount()>0)lp.leftMargin=dp(10);footer.addView(button,lp);footer.setVisibility(View.VISIBLE);button.setOnClickListener(v->action.run());return button;}
    void runAfterDismiss(Runnable action){dismiss();activity.getWindow().getDecorView().post(action);}
    void row(WorkbenchIcon.Kind icon,String label,String detail,boolean arrow,boolean danger,Runnable action){LinearLayout row=WorkbenchUi.row(getContext(),icon,label,detail,arrow,danger);body.addView(row);row.setOnClickListener(v->runAfterDismiss(action));}
    void divider(){View line=new View(getContext());line.setBackgroundColor(WorkbenchUi.LINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(12),dp(8),dp(12),dp(8));body.addView(line,lp);}
    /** Stage menus stay in the host window; text editors keep a separate IME-capable window. */
    void preferHostSurface(){if(!hasEditor(panel))hostSurface=new HostModalSurface(activity,panel,this::dismiss,this::fit);}
    private static boolean hasEditor(View view){
        if(view instanceof EditText)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasEditor(group.getChildAt(i)))return true;}
        return false;
    }
    boolean inHost(){return hostSurface!=null;}
    @Override public boolean isShowing(){return inHost()?hostSurface.isShowing():super.isShowing();}
    @Override public void setOnDismissListener(android.content.DialogInterface.OnDismissListener listener){dismissed=listener;super.setOnDismissListener(listener);}
    @Override public void show(){if(inHost()){hostSurface.show();fit();}else{fitWindow();super.show();}}
    @Override public void dismiss(){
        if(!inHost()){super.dismiss();return;}
        if(!isShowing())return;hostSurface.dismiss();if(dismissed!=null)dismissed.onDismiss(this);
    }
    @Override public void onBackPressed(){if(inHost())dismiss();else super.onBackPressed();}
    void fit(){
        if(isShowing())fitWindow();
    }
    private void fitWindow(){
        WindowMetrics metrics=activity.getWindowManager().getCurrentWindowMetrics();Rect bounds=metrics.getBounds();
        Insets safe=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        WindowInsets root=panel.getRootWindowInsets();int keyboard=root==null?0:root.getInsets(WindowInsets.Type.ime()).bottom;
        if(inHost()){
            Rect usable=hostSurface.available(bounds,safe,keyboard);
            int width=Math.max(dp(240),Math.min(dp(preferredWidth),usable.width()-dp(32)));
            panel.maxHeight=Math.max(dp(160),usable.height()-dp(32));
            panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(panel.maxHeight,View.MeasureSpec.AT_MOST));
            hostSurface.place(width,panel.getMeasuredHeight(),usable,anchor);return;
        }
        int available=Math.max(dp(160),bounds.height()-safe.top-Math.max(safe.bottom,keyboard)-dp(32));
        int width=Math.max(dp(240),Math.min(dp(preferredWidth),bounds.width()-safe.left-safe.right-dp(32)));
        boolean resized=panel.maxHeight!=available;panel.maxHeight=available;panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
        WindowManager.LayoutParams attrs=getWindow().getAttributes();int gravity=Gravity.CENTER,x=0,y=0;
        if(anchor!=null&&anchor.isAttachedToWindow()){
            int[] location=new int[2];anchor.getLocationOnScreen(location);gravity=Gravity.TOP|Gravity.LEFT;
            x=Math.max(dp(16),Math.min(location[0]-bounds.left-safe.left,bounds.width()-safe.left-safe.right-width-dp(16)));
            y=Math.max(dp(16),Math.min(location[1]+anchor.getHeight()+dp(8)-bounds.top-safe.top,bounds.height()-safe.top-safe.bottom-panel.getMeasuredHeight()-dp(16)));
        }
        if(attrs.width!=width||attrs.height!=WindowManager.LayoutParams.WRAP_CONTENT||attrs.gravity!=gravity||attrs.x!=x||attrs.y!=y){attrs.width=width;attrs.height=WindowManager.LayoutParams.WRAP_CONTENT;attrs.gravity=gravity;attrs.x=x;attrs.y=y;getWindow().setAttributes(attrs);}
        if(resized)panel.requestLayout();
    }
    private int dp(float value){return WorkbenchUi.dp(getContext(),value);}
    private static final class Panel extends LinearLayout {
        int maxHeight=Integer.MAX_VALUE;
        Panel(Context c){super(c);}
        @Override protected void onMeasure(int width,int height){int limit=Math.min(maxHeight,View.MeasureSpec.getSize(height));super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));}
    }
}
