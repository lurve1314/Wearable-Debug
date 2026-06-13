package test.hook.debug.xp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Module settings manager using file-based properties
 * More compatible with modern Android/Xposed than XSharedPreferences
 */
public class Settings {
    private static final String PREF_FILE = "wearable_debug_prefs.properties";
    private static Properties props;
    private static File prefFile;
    
    public static void init() {
        props = new Properties();
        // Use module's own data directory
        prefFile = new File("/data/data/" + "test.hook.debug.xp" + "/shared_prefs/" + PREF_FILE);
        if (!prefFile.exists()) {
            prefFile = new File("/data/user_de/0/" + "test.hook.debug.xp" + "/shared_prefs/" + PREF_FILE);
        }
        load();
    }
    
    private static void load() {
        if (prefFile.exists()) {
            try (FileInputStream fis = new FileInputStream(prefFile)) {
                props.load(fis);
            } catch (IOException e) {
                // Use defaults
            }
        }
    }
    
    public static boolean isTrialWatchFaceEnabled() {
        return Boolean.parseBoolean(props.getProperty("trial_watchface_enabled", "true"));
    }
    
    public static boolean isDebugLogEnabled() {
        return Boolean.parseBoolean(props.getProperty("debug_log_enabled", "false"));
    }
    
    public static boolean isDisableAdEnabled() {
        return Boolean.parseBoolean(props.getProperty("disable_ad_enabled", "true"));
    }
    
    public static boolean isDisableKeepLinkNotifyEnabled() {
        return Boolean.parseBoolean(props.getProperty("disable_keep_link_notify_enabled", "true"));
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