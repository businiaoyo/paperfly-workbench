package cn.paperfly.workbench;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Short transaction-scoped settings with durable recovery records; no permanent preference. */
final class TransitionScale {
    private static final String[] KEYS = {"transition_animation_scale", "animator_duration_scale"};
    private final AtomicFile[] journals = {
            new AtomicFile(new File("/data/local/tmp/paperfly-workbench-animation.txt")),
            new AtomicFile(new File("/data/local/tmp/paperfly-workbench-animator.txt"))
    };
    void begin() throws Exception {
        restore();
        // Record every original before changing any setting, including the absence of a value.
        for (int i = 0; i < KEYS.length; i++) {
            String original = read(KEYS[i]);
            if (!"null".equals(original) && !Float.isFinite(Float.parseFloat(original)))
                throw new IllegalStateException("无法读取系统动画倍率");
            AtomicFile journal = journals[i];
            FileOutputStream out = journal.startWrite();
            try {
                android.system.Os.chmod(journal.getBaseFile().getPath() + ".new", 0600);
                out.write(original.getBytes(StandardCharsets.UTF_8));
                journal.finishWrite(out);
            } catch (Exception error) { journal.failWrite(out); throw error; }
        }
        for (String key : KEYS)
            require(WorkbenchService.run("/system/bin/settings", "put", "global", key, "0"));
        android.os.SystemClock.sleep(120); // Let system UI and the window manager observe the scales.
    }
    void restore() throws Exception {
        Exception failure = null;
        for (int i = 0; i < KEYS.length; i++) {
            try { restore(KEYS[i], journals[i]); }
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
    private static void restore(String key, AtomicFile journal) throws Exception {
        if (!journal.getBaseFile().exists()) return;
        String original = new String(journal.readFully(), StandardCharsets.UTF_8).trim();
        if (!"null".equals(original) && !Float.isFinite(Float.parseFloat(original)))
            throw new IllegalStateException("动画恢复记录无效");
        String current = read(key);
        // Preserve a change made by the user or another application in the meantime.
        if (!"null".equals(current) && Float.parseFloat(current) == 0f) {
            require("null".equals(original)
                    ? WorkbenchService.run("/system/bin/settings", "delete", "global", key)
                    : WorkbenchService.run("/system/bin/settings", "put", "global", key, original));
        }
        journal.delete();
    }
    private static String read(String key) {
        WorkbenchService.CommandResult value = WorkbenchService.run("/system/bin/settings", "get", "global", key);
        require(value);
        return value.output.trim();
    }
    private static void require(WorkbenchService.CommandResult result) {
        if (!result.ok()) throw new IllegalStateException(result.describe());
    }
}
