package cn.paperfly.workbench.probe;
import android.app.Activity;
import android.content.ClipData;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
/** Disposable, account-free validation fixture. Never a dependency of the Workbench APK. */
public final class ProbeActivity extends Activity {
 private int taps,ticks;private final Handler handler=new Handler(Looper.getMainLooper());private final String identity=java.util.UUID.randomUUID().toString().substring(0,8);
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);root.setBackgroundColor(Color.rgb(236,246,246));setContentView(root);
  TextView heading=new TextView(this);heading.setTextSize(24);heading.setText("验证窗口 "+identity+" · task "+getTaskId());root.addView(heading);
  TextView counter=new TextView(this);counter.setTextSize(22);root.addView(counter);
  Button tap=new Button(this);tap.setText("计数 +1");tap.setOnClickListener(v->{taps++;counter.setText("点击 "+taps+" · 帧心跳 "+ticks);});root.addView(tap,new LinearLayout.LayoutParams(-1,90));
  EditText editor=new EditText(this);editor.setSingleLine();editor.setHint("键盘输入验证");editor.setContentDescription("验证输入框");root.addView(editor,new LinearLayout.LayoutParams(-1,100));
  TextView drag=new TextView(this);drag.setText("长按拖动：WorkBench drag payload");drag.setTextSize(20);drag.setGravity(Gravity.CENTER);drag.setBackgroundColor(0xffb5dfe4);root.addView(drag,new LinearLayout.LayoutParams(-1,120));
  drag.setOnLongClickListener(v->{boolean started=v.startDragAndDrop(ClipData.newPlainText("Workbench validation","WorkBench drag payload"),new View.DragShadowBuilder(v),null,View.DRAG_FLAG_GLOBAL);android.util.Log.i("WorkbenchProbe","DRAG_START "+started+" task="+getTaskId());return started;});
  TextView drop=new TextView(this);drop.setText("拖放接收区");drop.setTextSize(22);drop.setGravity(Gravity.CENTER);drop.setBackgroundColor(0xffd5e4c5);root.addView(drop,new LinearLayout.LayoutParams(-1,0,1));
  drop.setOnDragListener((v,event)->{android.util.Log.i("WorkbenchProbe","DRAG event="+event.getAction()+" task="+getTaskId());if(event.getAction()==DragEvent.ACTION_DROP){drop.setText("已收到："+event.getClipData().getItemAt(0).coerceToText(this));android.util.Log.i("WorkbenchProbe","DROP_SUCCESS task="+getTaskId());}return true;});
  handler.post(new Runnable(){public void run(){counter.setText("点击 "+taps+" · 帧心跳 "+(++ticks));handler.postDelayed(this,1000);}});
 }
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
