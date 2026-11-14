package cn.paperfly.workbench;

/** Keep intermediate configurations invisible, and always undo temporary visibility changes. */
final class TaskRestore {
    interface Backend {
        void hide() throws Exception;
        void move() throws Exception;
        void show() throws Exception;
    }
    static void run(Backend backend) throws Exception {
        Throwable failure=null;
        try{backend.hide();backend.move();}
        catch(Exception|Error error){failure=error;throw error;}
        finally{
            // A Binder exception may arrive after applying hide; always reveal and retry once.
            try{backend.show();}
            catch(Exception first){
                try{backend.show();}
                catch(Exception retry){
                    first.addSuppressed(retry);
                    if(failure!=null)failure.addSuppressed(first);else throw first;
                }
            }
        }
    }
}
