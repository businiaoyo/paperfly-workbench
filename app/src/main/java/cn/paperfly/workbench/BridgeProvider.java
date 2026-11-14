package cn.paperfly.workbench;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/** Transfers one typed Binder from the user-activated shell process. Exposes no data or commands. */
public final class BridgeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int version = extras == null ? -1 : extras.getInt("version", -1);
        if (!"attach".equals(method) || !BridgeAccess.accepts(Binder.getCallingUid(), version,
                BuildConfig.VERSION_CODE)) throw new SecurityException("Only the matching shell helper may attach");
        IBinder service = extras.getBinder("service");
        try {
            if (service == null || !"cn.paperfly.workbench.IWorkbenchService".equals(service.getInterfaceDescriptor()))
                throw new SecurityException("Unexpected service interface");
            BridgeState.attach(service);
        } catch (RemoteException error) { throw new IllegalStateException(error); }
        Bundle response = new Bundle();
        response.putBoolean("attached", true);
        return response;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
