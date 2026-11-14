package cn.paperfly.workbench;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class Activation {
    static final String COMMAND = "adb shell sh /sdcard/Android/data/cn.paperfly.workbench/files/activate.sh";
    private Activation() {}
    static void writeScript(Context context) throws IOException {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) throw new IOException("应用文件目录不可用，请解锁设备后重试");
        String script = scriptBody();
        File temporary = new File(dir, "activate.sh.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(script.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temporary.renameTo(new File(dir, "activate.sh"))) throw new IOException("无法生成 USB 激活脚本");
    }
    static String scriptBody(){
        return "#!/system/bin/sh\n"
                + "APK=$(pm path cn.paperfly.workbench | sed -n 's/^package://p' | head -n 1)\n"
                + "[ -n \"$APK\" ] || { echo 'OpenWorkbench is not installed'; exit 1; }\n"
                + "PID_FILE=/data/local/tmp/paperfly-workbench-server.pid\n"
                + "if [ -f \"$PID_FILE\" ]; then\n"
                + "  OLD=$(cat \"$PID_FILE\")\n"
                + "  case \"$OLD\" in ''|*[!0-9]*) ;; *)\n"
                + "    NAME=$(tr '\\000' ' ' 2>/dev/null < /proc/$OLD/cmdline)\n"
                + "    case \"$NAME\" in paperfly_workbench_server*) kill \"$OLD\" 2>/dev/null; sleep 1 ;; esac\n"
                + "  esac\nfi\n"
                + "CLASSPATH=\"$APK\" nohup app_process /system/bin cn.paperfly.workbench.StandaloneServer"
                + " > /data/local/tmp/paperfly-workbench-server.log 2>&1 < /dev/null &\n"
                + "echo $! > \"$PID_FILE\"\n"
                + "echo 'OpenWorkbench helper started. Open the app to check activation.'\n";
    }
}
