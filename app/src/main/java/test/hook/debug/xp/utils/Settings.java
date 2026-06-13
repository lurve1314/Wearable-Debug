package test.hook.debug.xp.utils;

import android.content.SharedPreferences;

import io.github.libxposed.api.XposedModule;

/**
 * Module settings manager using API 102 getRemotePreferences().
 *
 * In hook process: uses XposedModule.getRemotePreferences() to read/write settings.
 * In module app process (SettingsActivity): uses regular SharedPreferences.
 *
 * Both access the same underlying storage via the framework's cross-process mechanism.
 */
public class Settings {
    public static final String PREFS_NAME = "wearable_debug_settings";

    private static XposedModule module;
    private static SharedPreferences remotePrefs;

    /**
     * Initialize with the XposedModule instance (called from MainHook).
     */
    public static void init(XposedModule m) {
        module = m;
        try {
            remotePrefs = module.getRemotePreferences(PREFS_NAME);
        } catch (Throwable e) {
            module.log(android.util.Log.ERROR, "WearableDebug", "Settings.init() failed", e);
            remotePrefs = null;
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        if (remotePrefs == null) return defaultValue;
        try {
            return remotePrefs.getBoolean(key, defaultValue);
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    public static void setProperty(String key, String value) {
        if (remotePrefs == null) return;
        try {
            remotePrefs.edit().putBoolean(key, Boolean.parseBoolean(value)).apply();
        } catch (Throwable e) {
            // ignore
        }
    }

    // 试用表盘功能
    public static boolean isTrialWatchFaceEnabled() {
        return getBoolean("trial_watchface_enabled", true);
    }

    // 广告屏蔽
    public static boolean isDisableAdEnabled() {
        return getBoolean("disable_ad_enabled", true);
    }

    // 连接保护通知屏蔽
    public static boolean isDisableKeepLinkNotifyEnabled() {
        return getBoolean("disable_keep_link_notify_enabled", true);
    }

    // 勿扰模式同步（API35 + isSupportZenMode）
    public static boolean isZenModeSyncEnabled() {
        return getBoolean("zen_mode_sync_enabled", true);
    }

    // 微信通话6s强提醒
    public static boolean isWeChatCallAlertEnabled() {
        return getBoolean("wechat_call_alert_enabled", true);
    }

    // 一加日程导入修复
    public static boolean isOneplusCalendarFixEnabled() {
        return getBoolean("oneplus_calendar_fix_enabled", true);
    }

    // 调试日志
    public static boolean isDebugLogEnabled() {
        return getBoolean("debug_log_enabled", false);
    }
}