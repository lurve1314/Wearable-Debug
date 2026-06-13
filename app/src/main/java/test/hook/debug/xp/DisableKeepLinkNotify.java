package test.hook.debug.xp;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.UsingFieldData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import test.hook.debug.xp.utils.DexKit;

/**
 * 关闭3.46.0i版本中出现的连接保护弹窗和红点提示
 * API 102 版本 - 移除 XposedHelpers/HookFactory 依赖
 */
public class DisableKeepLinkNotify {
    private static final String TAG = "DisableKeepLinkNotify";

    public static void disableDeviceSystemRedDot(ClassLoader loader, XposedModule module) {
        try {
            Class<?> target = Class.forName("com.xiaomi.fitness.device.manager.export.bean.TabContentItem", false, loader);
            DexKitBridge bridge = DexKit.INSTANCE.getDexKitBridge();
            ClassData classData = bridge.getClassData(target);
            MethodDataList method = classData.getMethods().findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().name("<init>")));
            String targetName = null;
            for (int i = 0; i < method.size(); i++) {
                MethodData methodData = method.get(i);
                for (UsingFieldData field : methodData.getUsingFields()) {
                    String name = field.getField().getName();
                    if (name.endsWith("Dot")) {
                        targetName = name;
                        break;
                    }
                }
                if (targetName != null) {
                    break;
                }
            }

            if (targetName == null) {
                return;
            }
            Field field = target.getDeclaredField(targetName);
            field.setAccessible(true);

            for (int i = 0; i < method.size(); i++) {
                MethodData methodData = method.get(i);
                Constructor<?> constructor = methodData.getConstructorInstance(loader);
                module.hook(constructor).intercept(chain -> {
                    try {
                        field.set(chain.getThisObject(), null);
                    } catch (IllegalAccessException e) {
                        module.log(android.util.Log.ERROR, TAG, "Failed to set field", e);
                    }
                    return chain.proceed();
                });
            }
        } catch (NoSuchMethodError | Exception e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to disable red dot for device settings", e);
        }
    }

    public static void disableTabRedDot(ClassLoader loader, XposedModule module) {
        try {
            Class<?> mainActivity = Class.forName("com.xiaomi.fitness.main.MainActivity", false, loader);
            Method refreshDeviceTabIcon = mainActivity.getDeclaredMethod("refreshDeviceTabIcon");
            refreshDeviceTabIcon.setAccessible(true);
            module.hook(refreshDeviceTabIcon).intercept(chain -> null);
        } catch (NoSuchMethodError | Exception e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to disable red dot for tab", e);
        }
    }

    public static void disableDialog(ClassLoader loader, XposedModule module) {
        try {
            Class<?> mainActivity = Class.forName("com.xiaomi.fitness.main.MainActivity", false, loader);
            Method showKeepLinkDialog = mainActivity.getDeclaredMethod("showKeepLinkDialog");
            showKeepLinkDialog.setAccessible(true);
            module.hook(showKeepLinkDialog).intercept(chain -> null);
        } catch (NoSuchMethodError | Exception e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to disable keep link dialog", e);
        }
    }
}