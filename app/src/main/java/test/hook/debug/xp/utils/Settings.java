package test.hook.debug.xp.utils;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Module settings manager using file-based properties on shared storage.
 * Uses /sdcard/ public root location so both the module app and target app processes can read.
 * Note: /sdcard/Android/data/xxx/ is scoped storage and only accessible by the owner app.
 *       Using /sdcard/ root ensures the target app process (com.xiaomi.wearable) can also read it.
 */
public class Settings {
    private static final String PREF_FILE = "wearable_debug_prefs.properties";
    private static Properties props;
    private static File prefFile;
    
    public static void init() {
        props = new Properties();
        // Store at sdcard root so any app with storage permission can access
        // /sdcard/Android/data/xxx/ is scoped and NOT accessible from other apps on Android 11+
        File sdcardDir = Environment.getExternalStorageDirectory();
        prefFile = new File(sdcardDir, PREF_FILE);
        load();
    }
    
    private static void load() {
        if (prefFile != null && prefFile.exists()) {
            try (FileInputStream fis = new FileInputStream(prefFile)) {
                props.load(fis);
            } catch (IOException e) {
                // Use defaults
            }
        }
    }
    
    // 试用表盘功能
    public static boolean isTrialWatchFaceEnabled() {
        return Boolean.parseBoolean(props.getProperty("trial_watchface_enabled", "true"));
    }
    
    // 广告屏蔽
    public static boolean isDisableAdEnabled() {
        return Boolean.parseBoolean(props.getProperty("disable_ad_enabled", "true"));
    }
    
    // 连接保护通知屏蔽
    public static boolean isDisableKeepLinkNotifyEnabled() {
        return Boolean.parseBoolean(props.getProperty("disable_keep_link_notify_enabled", "true"));
    }
    
    // 勿扰模式同步（API35 + isSupportZenMode）
    public static boolean isZenModeSyncEnabled() {
        return Boolean.parseBoolean(props.getProperty("zen_mode_sync_enabled", "true"));
    }
    
    // 微信通话6s强提醒
    public static boolean isWeChatCallAlertEnabled() {
        return Boolean.parseBoolean(props.getProperty("wechat_call_alert_enabled", "true"));
    }
    
    // 一加日程导入修复
    public static boolean isOneplusCalendarFixEnabled() {
        return Boolean.parseBoolean(props.getProperty("oneplus_calendar_fix_enabled", "true"));
    }
    
    // 调试日志
    public static boolean isDebugLogEnabled() {
        return Boolean.parseBoolean(props.getProperty("debug_log_enabled", "false"));
    }
    
    public static void setProperty(String key, String value) {
        props.setProperty(key, value);
        save();
    }
    
    private static void save() {
        if (prefFile == null) return;
        try {
            File parent = prefFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(prefFile)) {
                props.store(fos, "Wearable Debug Settings");
            }
        } catch (IOException e) {
            // ignore
        }
    }
}