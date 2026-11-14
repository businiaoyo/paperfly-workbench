package cn.paperfly.workbench;

/** One automatic entry per launch intent; explicit management visits never bounce back. */
final class EntryRoute {
    private boolean pending;
    EntryRoute(boolean pending){this.pending=pending;}
    void launch(boolean showHome){pending=!showHome;}
    boolean pending(){return pending;}
    void stayHome(){pending=false;}
    boolean take(boolean active,boolean authorized,boolean loaded,boolean busy,boolean shortcut){
        if(!pending||!active||!authorized||!loaded||busy||shortcut)return false;
        pending=false;return true;
    }
}
