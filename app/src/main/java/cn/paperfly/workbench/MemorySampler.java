package cn.paperfly.workbench;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Read-only, shell-side PSS sample. Shared PIDs are counted once, never once per window. */
final class MemorySampler {
    static final String HOST = "cn.paperfly.workbench";
    static final String HELPER = "@helper";

    static Bundle sample(Context context, Set<String> apps) {
        Bundle result = new Bundle(), usage = new Bundle();
        long begin = SystemClock.elapsedRealtime();
        Set<String> wanted = new TreeSet<>(apps);
        wanted.add(HOST);
        for (String name : wanted) usage.putLong(name, -1);
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            ActivityManager.MemoryInfo system = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(system);
            result.putLong("availableKb", system.availMem / 1024);
            result.putLong("deviceKb", system.totalMem / 1024);
            result.putBoolean("lowMemory", system.lowMemory);
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            Map<Integer, String> owners = new LinkedHashMap<>();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.uid / 100000 != android.os.Process.myUid() / 100000) continue;
                if (process.pkgList == null) continue;
                for (String candidate : wanted) {
                    boolean matches = false;
                    for (String name : process.pkgList) if (candidate.equals(name)) matches = true;
                    if (matches) { owners.put(process.pid, candidate); break; }
                }
            }
            int[] pids = new int[owners.size()]; int index = 0;
            for (int pid : owners.keySet()) pids[index++] = pid;
            // AMS's public API can reuse old samples for minutes. Ask the system service
            // for its current local PSS instead, without waking each app to dump Java details.
            Debug.MemoryInfo[] infos = null;
            boolean cached = false;
            Set<String> incomplete = new java.util.HashSet<>();
            for (int i = 0; i < pids.length; i++) {
                String name = owners.get(pids[i]);
                WorkbenchService.CommandResult fresh = WorkbenchService.run("/system/bin/dumpsys", "-t", "2",
                        "meminfo", "--local", "-s", Integer.toString(pids[i]));
                long kb = fresh.ok() ? PssParser.totalKb(fresh.output) : -1;
                if (kb <= 0) {
                    if (infos == null) infos = am.getProcessMemoryInfo(pids);
                    kb = i < infos.length && infos[i] != null ? infos[i].getTotalPss() : 0;
                    cached = true;
                }
                if (kb <= 0) { incomplete.add(name); continue; }
                usage.putLong(name, Math.max(0, usage.getLong(name)) + kb);
            }
            // A shell app_process is not an AMS application process, so sample it directly.
            Debug.MemoryInfo helper = new Debug.MemoryInfo();
            Debug.getMemoryInfo(helper);
            usage.putLong(HELPER, helper.getTotalPss() > 0 ? helper.getTotalPss() : -1);
            long total = 0; boolean complete = incomplete.isEmpty();
            for (String name : usage.keySet()) {
                long kb = usage.getLong(name);
                if (kb < 0) complete = false; else total += kb;
            }
            result.putLong("knownTotalKb", total);
            result.putBoolean("complete", complete);
            result.putBoolean("cached", cached);
            result.putStringArrayList("incomplete", new ArrayList<>(incomplete));
            result.putBoolean("success", true);
        } catch (Exception | LinkageError error) {
            result.putString("error", error.getClass().getSimpleName());
        }
        result.putBundle("usage", usage);
        result.putLong("sampledAt", SystemClock.elapsedRealtime());
        result.putLong("durationMs", SystemClock.elapsedRealtime() - begin);
        return result;
    }
}
