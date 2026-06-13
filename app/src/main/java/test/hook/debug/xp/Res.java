package test.hook.debug.xp;

import android.content.Context;
import android.content.res.Resources;

import test.hook.debug.R;

/**
 * Res - API 102 version
 * 
 * 旧版使用 IXposedHookInitPackageResources 将模块资源注入到目标应用。
 * API 102 中 XposedModule 继承自 Application，可直接作为 Context 使用。
 * 这里保留 resource ID 常量，通过 XposedModule 实例获取字符串。
 */
public class Res {
    // 保留这些字段用于兼容 MainHook 中的 getString 调用
    // 但不再使用 addResource 方式注入，而是通过模块 Context 直接读取
    public static int firmware_warning = R.string.firmware_warning;
    public static int firmware_warning_title = R.string.firmware_warning_title;
    public static int fail_watchface = R.string.fail_watchface;
    public static int fail_firmware = R.string.fail_firmware;
    public static int fail_log = R.string.fail_log;
    public static int success_log = R.string.success_log;
    public static int main = R.layout.wearable_main;
    public static int options = R.id.wearable_options;

    // 模块 Context，用于获取模块资源
    private static Context sModuleContext;

    /**
     * 使用 XposedModule（继承自 Application）作为 Context 初始化
     */
    public static void init(Context moduleContext) {
        sModuleContext = moduleContext;
    }

    /**
     * 获取模块字符串资源
     * 与旧版不同，这里使用模块自己的 Context 而非目标应用的 resource ID
     */
    public static String getString(int resId) {
        if (sModuleContext != null) {
            return sModuleContext.getString(resId);
        }
        return "Resource not available";
    }
}