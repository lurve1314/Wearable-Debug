package test.hook.debug.xp.utils;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Module settings manager.
 *
 * In hook process (target app): uses XSharedPreferences to read settings
 * written by the module's SettingsActivity.
 *
 * In module process (SettingsActivity): uses regular SharedPreferences directly.
 * This class is NOT used for reading in the module process.
 */
public class Settings {
    static final String PREFS_NAME = "wearable_debug_settings";
    private static final String PACKAGE_NAME = "test.hook.debug";
    private static XSharedPreferences xPrefs;

    public static void init() {
        try {
            xPrefs = new XSharedPreferences(PACKAGE_NAME, PREFS_NAME);
            xPrefs.makeWorldReadable();
            XposedBridge.log("WearableDebug: Settings.init() - XSharedPreferences loaded successfully");
        } catch (Throwable e) {
            xPrefs = null;
            XposedBridge.log("WearableDebug: Settings.init() - XSharedPreferences failed: " + e.getMessage());
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        if (xPrefs == null) return defaultValue;
        try {
            xPrefs.reload();
            return xPrefs.getBoolean(key, defaultValue);
        } catch (Throwable e) {
            return defaultValue;
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
