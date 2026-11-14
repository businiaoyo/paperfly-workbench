package cn.paperfly.workbench;

import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/** Gives modal windows the same edge input path while preserving every other window callback. */
final class GestureWindowCallback implements Window.Callback {
    private final Window.Callback delegate;
    private final StageBackGesture gestures;
    GestureWindowCallback(Window.Callback delegate,StageBackGesture gestures){this.delegate=delegate;this.gestures=gestures;}
    @Override public boolean dispatchTouchEvent(MotionEvent e){return gestures.dispatch(e,delegate::dispatchTouchEvent);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return delegate.dispatchKeyEvent(e);}
    @Override public boolean dispatchKeyShortcutEvent(KeyEvent e){return delegate.dispatchKeyShortcutEvent(e);}
    @Override public boolean dispatchTrackballEvent(MotionEvent e){return delegate.dispatchTrackballEvent(e);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return delegate.dispatchGenericMotionEvent(e);}
    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent e){return delegate.dispatchPopulateAccessibilityEvent(e);}
    @Override public View onCreatePanelView(int id){return delegate.onCreatePanelView(id);}
    @Override public boolean onCreatePanelMenu(int id,Menu menu){return delegate.onCreatePanelMenu(id,menu);}
    @Override public boolean onPreparePanel(int id,View view,Menu menu){return delegate.onPreparePanel(id,view,menu);}
    @Override public boolean onMenuOpened(int id,Menu menu){return delegate.onMenuOpened(id,menu);}
    @Override public boolean onMenuItemSelected(int id,MenuItem item){return delegate.onMenuItemSelected(id,item);}
    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams p){delegate.onWindowAttributesChanged(p);}
    @Override public void onContentChanged(){delegate.onContentChanged();}
    @Override public void onWindowFocusChanged(boolean focused){delegate.onWindowFocusChanged(focused);}
    @Override public void onAttachedToWindow(){delegate.onAttachedToWindow();}
    @Override public void onDetachedFromWindow(){delegate.onDetachedFromWindow();}
    @Override public void onPanelClosed(int id,Menu menu){delegate.onPanelClosed(id,menu);}
    @Override public boolean onSearchRequested(){return delegate.onSearchRequested();}
    @Override public boolean onSearchRequested(SearchEvent event){return delegate.onSearchRequested(event);}
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback){return delegate.onWindowStartingActionMode(callback);}
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type){return delegate.onWindowStartingActionMode(callback,type);}
    @Override public void onActionModeStarted(ActionMode mode){delegate.onActionModeStarted(mode);}
    @Override public void onActionModeFinished(ActionMode mode){delegate.onActionModeFinished(mode);}
    @Override public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> groups,Menu menu,int device){delegate.onProvideKeyboardShortcuts(groups,menu,device);}
    @Override public void onPointerCaptureChanged(boolean captured){delegate.onPointerCaptureChanged(captured);}
}
