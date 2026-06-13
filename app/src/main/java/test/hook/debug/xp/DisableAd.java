package test.hook.debug.xp;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;

import io.github.libxposed.api.XposedModule;

/**
 * DisableAd - API 102 version
 * 移除了 EzXHelper、HookFactory、XposedHelpers 依赖
 */
public class DisableAd {
    private static final String TAG = "DisableAd";

    /**
     * 国际版3.33.6i出现Banner广告，拦截广告加载
     */
    public static void interceptAd(ClassLoader classLoader, XposedModule module) {
        try {
            Class<?> impl = Class.forName("com.fitness.banner.export.BannerImpl", false, classLoader);
            for (Method method : impl.getDeclaredMethods()) {
                if (method.getName().startsWith("getBannerListAsync")) {
                    module.hook(method).intercept(chain -> null);
                } else if (method.getName().startsWith("getBannerList")) {
                    module.hook(method).intercept(chain -> Collections.emptyList());
                }
            }
        } catch (ClassNotFoundException e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to disable ad", e);
        }
    }

    public static void disableReport(ClassLoader classLoader, XposedModule module) {
        try {
            Class<?> reportImpl = Class.forName("com.xiaomi.fitness.statistics.OnetrackImpl", false, classLoader);
            for (Method method : reportImpl.getDeclaredMethods()) {
                if (!"reportData".equals(method.getName())) {
                    continue;
                }
                module.hook(method).intercept(chain -> null);
            }
        } catch (ClassNotFoundException e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to disable report", e);
        }
    }

    /**
     * 隐藏蚂蚁阿福和健康问诊横幅
     */
    public static void hideAqView(ClassLoader classLoader, XposedModule module) {
        Class<?> aqViewClass = null;
        Class<?> healthBannerCardSetViewClass = null;
        try {
            aqViewClass = classLoader.loadClass("com.xiaomi.fitness.view.AqView");
        } catch (ClassNotFoundException e) {
            // Class not found, skip
        }
        try {
            healthBannerCardSetViewClass = classLoader.loadClass("com.xiaomi.fitness.view.HealthBannerCardSetView");
        } catch (ClassNotFoundException e) {
            // Class not found, skip
        }

        final Class<?> finalAqViewClass = aqViewClass;
        final Class<?> finalHealthBannerCardSetViewClass = healthBannerCardSetViewClass;

        try {
            Class<?> extUtilKt = Class.forName("com.xiaomi.fitness.util.ExtUtilKt", false, classLoader);
            Method visibleMethod = extUtilKt.getDeclaredMethod("visible", View.class);
            visibleMethod.setAccessible(true);

            module.hook(visibleMethod).intercept(chain -> {
                View view = (View) chain.getArgs().get(0);
                Class<?> clazz = view.getClass();
                if (clazz.equals(finalAqViewClass) || clazz.equals(finalHealthBannerCardSetViewClass)) {
                    view.setVisibility(View.GONE);
                    return null;
                }
                return chain.proceed();
            });
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            module.log(android.util.Log.ERROR, TAG, "Failed to hook ExtUtilKt.visible", e);
        }
    }
}