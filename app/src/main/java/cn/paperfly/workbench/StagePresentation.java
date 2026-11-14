package cn.paperfly.workbench;

/** Transient presentation only. Never changes card ownership, focus, order or saved configuration. */
final class StagePresentation {
    private boolean workspaceFullscreen;
    private int appWindow = -1;
    private final java.util.Set<Integer> minimized=new java.util.HashSet<>();
    boolean minimized(int id){return minimized.contains(id);}
    void minimize(int id){minimized.add(id);}
    void restore(int id){minimized.remove(id);}
    void resetWindows(){minimized.clear();appWindow=-1;}

    boolean workspaceFullscreen() { return workspaceFullscreen; }
    boolean appFullscreen() { return appWindow >= 0; }
    boolean immersive() { return workspaceFullscreen || appFullscreen(); }
    boolean isFullscreenApp(int windowId) { return appFullscreen() && appWindow == windowId; }
    boolean showsWindow(int windowId) { return !minimized(windowId)&&(!appFullscreen() || isFullscreenApp(windowId)); }

    boolean setWorkspaceFullscreen(boolean value) {
        if (workspaceFullscreen == value) return false;
        workspaceFullscreen = value;
        return true;
    }

    boolean enterApp(int windowId) {
        // A loading/absent app or a delayed selection must not replace an active full-screen app.
        if (windowId < 0 || appFullscreen()) return false;
        restore(windowId);appWindow = windowId;
        return true;
    }

    boolean leaveApp() {
        if (!appFullscreen()) return false;
        appWindow = -1;
        return true;
    }

    boolean windowRemoved(int windowId) {
        minimized.remove(windowId);return isFullscreenApp(windowId) && leaveApp();
    }
}
