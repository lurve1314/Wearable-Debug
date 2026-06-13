package test.hook.debug.xp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.github.libxposed.api.XposedModule;

/**
 * DeviceLog - API 102 version
 * 移除了 EzXHelper、XposedHelpers 依赖，改为原生反射
 */
public class DeviceLog {
    /**
     * 获取设备日志输出路径
     *
     * @param classLoader 当前类加载器
     * @return 日志输出路径
     */
    private static String getOutputDir(ClassLoader classLoader) throws Exception {
        Class<?> feedbackFileUtils = Class.forName("com.xiaomi.fitness.feedback.util.FeedbackFileUtils", false, classLoader);
        Object instance = feedbackFileUtils.getDeclaredField("INSTANCE").get(null);

        Method getDebiceLogDirPath = feedbackFileUtils.getDeclaredMethod("getDebiceLogDirPath");
        getDebiceLogDirPath.setAccessible(true);
        return (String) getDebiceLogDirPath.invoke(instance);
    }

    /**
     * 拉取设备日志
     *
     * @param classLoader 当前类加载器
     * @param module      XposedModule 实例
     * @param cb          事件回调
     */
    public static void pullLog(ClassLoader classLoader, XposedModule module, Callback<String> cb) {
        try {
            Object currentDevice = Install.getCurrentDevice(classLoader);

            Class<?> deviceModelExtKt = Class.forName("com.xiaomi.fitness.device.manager.DeviceModelExtKt", false, classLoader);
            Method isWearOS = deviceModelExtKt.getDeclaredMethod("isWearOS", Object.class);
            isWearOS.setAccessible(true);
            boolean isWear = (boolean) isWearOS.invoke(null, currentDevice);
            if (isWear) {
                cb.onError("Not support wearos device", null);
                return;
            }

            Class<?> getDeviceLog = Class.forName("com.xiaomi.fitness.feedback.bugreport.GetDeviceLog", false, classLoader);
            Object instance = getDeviceLog.getDeclaredField("INSTANCE").get(null);

            // Find syncLogFromBleDevice method
            Method syncMethod = null;
            for (Method m : getDeviceLog.getDeclaredMethods()) {
                if ("syncLogFromBleDevice".equals(m.getName())) {
                    syncMethod = m;
                    break;
                }
            }
            if (syncMethod == null) {
                cb.onError("syncLogFromBleDevice method not found", null);
                return;
            }
            syncMethod.setAccessible(true);

            Class<?> callbackClass = syncMethod.getParameterTypes()[1];

            Object callback = Proxy.newProxyInstance(classLoader, new Class[]{callbackClass}, (proxy, method1, args) -> {
                String name = method1.getName();
                if ("onError".equals(name)) {
                    String s = (String) args[0];
                    int type = (int) args[1];
                    int code = (int) args[2];
                    cb.onError("syncLogFromBleDevice onError: " + s + " type=" + type + " code=" + code, null);
                } else if ("onSuccess".equals(name)) {
                    String s = (String) args[0];
                    int v = (int) args[1];
                    Object syncResult = args[2];
                    cb.onSuccess(syncResult.toString());
                }
                return null;
            });

            syncMethod.invoke(instance, currentDevice, callback);
        } catch (Exception e) {
            cb.onError(e.getMessage(), e);
        }
    }
}