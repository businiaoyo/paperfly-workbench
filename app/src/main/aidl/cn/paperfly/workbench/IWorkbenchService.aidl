package cn.paperfly.workbench;

import android.os.Bundle;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.KeyEvent;

interface IWorkbenchService {
    String diagnostics() = 1;
    String launch(in String[] components, in int[] bounds) = 2;
    Bundle switchStage(in String[] components, int index, in int[] bounds, int hostTask, IBinder session, boolean stableSwitching) = 3;
    String endStage(int hostTask, boolean returnToHost) = 4;
    Bundle openLiveWindow(String component, in Surface surface, int width, int height, int density, int hostTask, IBinder session) = 5;
    oneway void liveTouch(int displayId, in MotionEvent event, IBinder session) = 6;
    oneway void liveKey(int displayId, int keyCode, IBinder session) = 7;
    String closeLiveStage(IBinder session) = 8;
    String closeLiveWindow(int displayId, IBinder session) = 9;
    oneway void focusLiveWindow(int displayId, IBinder session) = 10;
    Bundle liveMemory(IBinder session) = 11;
    Bundle resizeLiveStage(int width, int height, int density, IBinder session) = 12;
    Bundle rebindLiveSurface(String component, in Surface surface, IBinder session) = 13;
    Bundle resizeLiveWindow(int displayId, int width, int height, int density, IBinder session) = 14;
    Bundle backLiveWindow(int displayId, IBinder session) = 15;
    Bundle recentApplications() = 16;
    oneway void liveKeyEvent(int displayId, in KeyEvent event, IBinder session) = 17;
    Bundle openLiveInstance(String windowId, String component, in Surface surface, int width, int height, int density, int hostTask, IBinder session) = 18;
    Bundle rebindInstanceSurface(String windowId, in Surface surface, IBinder session) = 19;
    void destroy() = 16777114;
}
