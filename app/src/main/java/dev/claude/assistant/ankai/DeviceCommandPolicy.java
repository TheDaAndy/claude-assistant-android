package dev.claude.assistant.ankai;

/** Explicit capability boundary; unknown actions never become shell commands or intents. */
public final class DeviceCommandPolicy {
    public static boolean allowed(String action, String target, long expiresAt, long now) {
        if (expiresAt <= now) return false;
        if ("ui_snapshot".equals(action)||"ui_screenshot".equals(action)||"ui_tap".equals(action)||"ui_swipe".equals(action)||"ui_text".equals(action)||"ui_global".equals(action)||"ui_launch".equals(action))return true;
        if ("device_info".equals(action) || "settings_snapshot".equals(action) || "remote_shell".equals(action)) return true;
        return "open_settings".equals(action) && ("general".equals(target) || "wifi".equals(target)
                || "application".equals(target) || "accessibility".equals(target) || "developer".equals(target));
    }
    public static boolean matchesGrant(long commandRevision, long currentRevision) {
        return commandRevision >= 0 && commandRevision == currentRevision;
    }
    public static int retrySeconds(int failures) {
        return Math.min(300, 5 << Math.min(6, Math.max(0, failures - 1)));
    }
}
