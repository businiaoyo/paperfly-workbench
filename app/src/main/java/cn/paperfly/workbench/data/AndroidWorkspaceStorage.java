package cn.paperfly.workbench.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AndroidWorkspaceStorage implements WorkspaceStorage {
    private final File path;
    private final AtomicFile file;
    private final SharedPreferences legacy;

    AndroidWorkspaceStorage(Context context) {
        path = new File(context.getFilesDir(), "workspaces.json");
        file = new AtomicFile(path);
        legacy = context.getSharedPreferences("workspace", Context.MODE_PRIVATE);
    }

    @Override public String read() throws IOException {
        if (!path.exists() && !new File(path.getPath() + ".bak").exists()) return null;
        return new String(file.readFully(), StandardCharsets.UTF_8);
    }

    @Override public void write(String document) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(document.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException | RuntimeException error) {
            if (stream != null) file.failWrite(stream);
            throw new IOException("工作台配置写入失败，先前数据已保留", error);
        }
    }

    @Override public String legacyComponents() { return legacy.getString("components", "[]"); }
}
