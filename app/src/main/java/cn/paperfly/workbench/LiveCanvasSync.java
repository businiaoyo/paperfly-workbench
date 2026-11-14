package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.SurfaceControl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Service-only BLAST callback. Waits for resized apps to draw before publishing their geometry. */
@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
final class LiveCanvasSync extends Binder {
    private static final String DESCRIPTOR="android.window.IWindowContainerTransactionCallback";
    private final CountDownLatch ready=new CountDownLatch(1);
    private final int transactionCode;
    private volatile long frameAfter;
    private volatile RuntimeException failure;
    private LiveCanvasSync() throws Exception {
        java.lang.reflect.Field code=Class.forName(DESCRIPTOR+"$Stub").getDeclaredField("TRANSACTION_onTransactionReady");
        code.setAccessible(true);transactionCode=code.getInt(null);
    }
    static long apply(Object transaction,Class<?> transactionType) throws Exception {
        LiveCanvasSync callback=new LiveCanvasSync();
        Class<?> callbackType=Class.forName(DESCRIPTOR);
        Object endpoint=Class.forName(DESCRIPTOR+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,callback);
        Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Object controller=manager.getClass().getMethod("getWindowOrganizerController").invoke(manager);
        controller.getClass().getMethod("applySyncTransaction",transactionType,callbackType).invoke(controller,transaction,endpoint);
        if(!callback.ready.await(6,TimeUnit.SECONDS))throw new IllegalStateException("等待应用画布超时");
        if(callback.failure!=null)throw callback.failure;
        return callback.frameAfter;
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
        if(code!=transactionCode)return super.onTransact(code,data,reply,flags);
        data.enforceInterface(DESCRIPTOR);data.readInt(); // Sync ID: each callback belongs to one request.
        SurfaceControl.Transaction transaction=data.readTypedObject(SurfaceControl.Transaction.CREATOR);
        try{
            if(transaction==null)throw new IllegalStateException("应用画布事务为空");
            frameAfter=System.nanoTime();transaction.apply();
        }catch(RuntimeException error){failure=error;}
        finally{if(transaction!=null)transaction.close();ready.countDown();}
        return true;
    }
}
