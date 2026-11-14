package cn.paperfly.workbench;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.paperfly.workbench.model.WorkspaceStyle;
import java.util.function.Consumer;

final class WorkspaceStylePanel {
    private static final int[][] PALETTES={{0xff141d2b,0xff274049,0xff162131},{0xff202124,0xff202124,0xff202124},
            {0xff122b25,0xff345b45,0xff152a22},{0xff665e55,0xff665e55,0xff665e55},{0xff201b38,0xff514466,0xff211d34}};
    static void show(Activity activity,WorkspaceStyle value,Consumer<Dialog> show,Consumer<WorkspaceStyle> apply,Runnable image){
        WorkbenchDialog dialog=new WorkbenchDialog(activity,"外观与比例","调整布局，让工作台更顺手",480);Context c=dialog.getContext();LinearLayout box=dialog.body;
        WorkbenchUi.section(box,"侧栏");
        Switch right=WorkbenchUi.toggle(c,box,"侧栏放在右侧",null,value.railRight);
        Switch compact=WorkbenchUi.toggle(c,box,"紧凑卡片","缩小侧窗之间的间距",value.compactCards);
        WorkbenchUi.space(box,12);SeekBar width=WorkbenchUi.slider(c,box,"侧栏宽度",value.landscapeRailDp,70,260," dp");
        WorkbenchUi.section(box,"主窗口");SeekBar scale=WorkbenchUi.slider(c,box,"显示比例",Math.round(value.primaryScale*100),60,100,"%");
        float[] aspects={0,1.6f,16f/9f,4f/3f,1};int selectedAspect=0;for(int i=0;i<aspects.length;i++)if(Math.abs(aspects[i]-value.aspectRatio)<.01f)selectedAspect=i;
        WorkbenchUi.Choices aspect=new WorkbenchUi.Choices(c,new String[]{"自动","16:10","16:9","4:3","1:1"},selectedAspect);aspect.setContentDescription("主窗宽高比");box.addView(aspect);
        WorkbenchUi.section(box,"背景");BackgroundChoices backgrounds=new BackgroundChoices(c,value);box.addView(backgrounds);
        WorkbenchUi.space(box,8);dialog.row(WorkbenchIcon.Kind.IMAGE,"选择背景图片",value.wallpaperBackground?"当前：桌面壁纸 · 虚化":value.backgroundUri.isEmpty()?"当前：颜色背景":"当前：自选图片",true,false,image);
        dialog.action("取消",false,dialog::dismiss);dialog.action("应用",true,()->{
            int selected=backgrounds.selected,rail=width.getProgress()+70;int[] colors=selected<2?new int[]{value.backgroundStart,value.backgroundMiddle,value.backgroundEnd}:PALETTES[selected-2];
            WorkspaceStyle changed=new WorkspaceStyle(colors[0],colors[1],colors[2],rail==value.landscapeRailDp?value.portraitRailDp:Math.max(70,Math.round(rail*.59f)),rail,
                    rail==value.landscapeRailDp?value.maxRailFraction:Math.max(.35f,value.maxRailFraction),right.isChecked(),compact.isChecked(),(scale.getProgress()+60)/100f,
                    selected==0?value.backgroundUri:"",aspects[aspect.selected()],selected==1||(selected==0&&value.wallpaperBackground));
            dialog.runAfterDismiss(()->apply.accept(changed));
        });show.accept(dialog);
    }
    private static final class BackgroundChoices extends LinearLayout {
        int selected;final View[] options=new View[7];
        BackgroundChoices(Context c,WorkspaceStyle style){super(c);setOrientation(VERTICAL);
            String[] names={"保留当前","桌面壁纸","深海","石墨","森林","暖灰","紫夜"};
            for(int start=0;start<7;start+=3){LinearLayout row=new LinearLayout(c);LayoutParams rl=new LayoutParams(-1,-2);rl.bottomMargin=WorkbenchUi.dp(c,8);addView(row,rl);
                for(int col=0;col<3;col++){int index=start+col;LayoutParams lp=new LayoutParams(0,WorkbenchUi.dp(c,60),1);if(col>0)lp.leftMargin=WorkbenchUi.dp(c,8);
                    if(index>=7){row.addView(new View(c),lp);continue;}
                    LinearLayout tile=WorkbenchUi.column(c);tile.setGravity(Gravity.CENTER);tile.setContentDescription(names[index]+"背景");tile.setFocusable(true);
                    int[] colors=index==0?new int[]{style.backgroundStart,style.backgroundMiddle,style.backgroundEnd}:index==1?new int[]{0xff5bbba8,0xff567cba,0xff9c71b6}:PALETTES[index-2];
                    View swatch=new View(c);GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);gradient.setCornerRadius(WorkbenchUi.dp(c,5));swatch.setBackground(gradient);tile.addView(swatch,new LayoutParams(WorkbenchUi.dp(c,38),WorkbenchUi.dp(c,18)));
                    TextView label=WorkbenchUi.text(c,names[index],12,WorkbenchUi.INK);label.setGravity(Gravity.CENTER);label.setPadding(0,WorkbenchUi.dp(c,6),0,0);tile.addView(label);row.addView(tile,lp);options[index]=tile;tile.setOnClickListener(v->select(index));
                }
            }select(0);
        }
        void select(int value){selected=value;for(int i=0;i<options.length;i++){GradientDrawable bg=WorkbenchUi.shape(getContext(),i==selected?0xff3a514f:WorkbenchUi.TILE,12);if(i==selected)bg.setStroke(WorkbenchUi.dp(getContext(),1.5f),WorkbenchUi.ACCENT);options[i].setBackground(bg);options[i].setSelected(i==selected);}}
    }
}
