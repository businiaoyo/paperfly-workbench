package cn.paperfly.workbench;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.Binder;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** Uses system-checked task transactions from the USB-activated shell process. */
final class TaskTransactionBridge {
    private TaskTransactionBridge() {}

    // This code runs in app_process with shell UID; system Binder permission checks still apply.
    @SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    static String requestFreeform(WorkbenchService.TaskRecord target, int user) {
        int uid = android.os.Process.myUid();
        if (uid != 2000 && uid != 0) return "未转换：请先完成无线或 USB 激活。";
        long identity = Binder.clearCallingIdentity();
        try {
            Class<?> managerClass = Class.forName("android.app.ActivityTaskManager");
            Object manager = managerClass.getDeclaredMethod("getService").invoke(null);
            List<?> roots = (List<?>) manager.getClass().getMethod("getAllRootTaskInfos")
                    .invoke(manager);
            Object matched = null;
            for (Object info : roots) {
                Class<?> type = info.getClass();
                int taskId = type.getField("taskId").getInt(info);
                if (taskId != target.id) continue;
                int display = type.getField("displayId").getInt(info);
                int owner = type.getField("userId").getInt(info);
                int[] children = (int[]) type.getField("childTaskIds").get(info);
                Object configuration = type.getField("configuration").get(info);
                Object window = configuration.getClass().getField("windowConfiguration")
                        .get(configuration);
                int activityType = (int) window.getClass().getMethod("getActivityType")
                        .invoke(window);
                int windowMode = (int) window.getClass().getMethod("getWindowingMode")
                        .invoke(window);
                ComponentName base = (ComponentName) type.getField("baseActivity").get(info);
                if (!isIsolatedTarget(taskId, target.id, display, owner, user,
                        activityType, windowMode, children) || base == null
                        || !base.flattenToString().equals(target.component)) {
                    return "未转换：任务身份、用户或任务组已变化。";
                }
                if (matched != null) return "未转换：任务身份不唯一。";
                matched = info;
            }
            if (matched == null) return "未转换：没有找到可独立调整的目标任务。";
            Object token = matched.getClass().getField("token").get(matched);
            if (token == null) return "未转换：系统没有提供任务令牌。";
            Class<?> tokenClass = Class.forName("android.window.WindowContainerToken");
            Class<?> transactionClass = Class.forName("android.window.WindowContainerTransaction");
            Object transaction = transactionClass.getConstructor().newInstance();
            transactionClass.getMethod("setWindowingMode", tokenClass, int.class)
                    .invoke(transaction, token, 5);
            transactionClass.getMethod("reorder", tokenClass, boolean.class)
                    .invoke(transaction, token, true);
            Class<?> organizerClass = Class.forName("android.window.WindowOrganizer");
            organizerClass.getMethod("applyTransaction", transactionClass)
                    .invoke(organizerClass.getConstructor().newInstance(), transaction);
            return "已提交系统任务切换请求，等待回读确认；没有修改系统设置。";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException
                    && exception.getCause() != null ? exception.getCause() : exception;
            return "系统任务切换不可用：" + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : " · " + cause.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /** Never convert a shared root, another display/user, Home, PiP, or a task container. */
    static boolean isIsolatedTarget(int actualId, int expectedId, int display, int owner,
                                    int expectedUser, int activityType, int windowMode, int[] children) {
        return expectedId >= 0 && actualId == expectedId && display == 0
                && owner == expectedUser && expectedUser >= 0 && activityType == 1 && windowMode == 1
                && children != null && children.length == 1 && children[0] == expectedId;
    }
}
