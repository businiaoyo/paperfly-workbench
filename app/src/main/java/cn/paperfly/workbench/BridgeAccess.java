package cn.paperfly.workbench;

/** Privilege boundary for the sole exported endpoint. No normal app UID is accepted. */
final class BridgeAccess {
    private BridgeAccess() {}
    static boolean accepts(int senderUid, int serverVersion, int appVersion) {
        return (senderUid == 2000 || senderUid == 0)
                && appVersion > 0 && serverVersion == appVersion;
    }
}
