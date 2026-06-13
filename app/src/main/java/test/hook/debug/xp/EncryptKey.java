package test.hook.debug.xp;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedModule;

/**
 * EncryptKey - API 102 version
 * 移除了 XposedHelpers 依赖，改为原生反射
 */
public class EncryptKey {
    /**
     * 获取当前保存的EncryptKey信息
     * 返回 did -> [设备名称, EncryptKey]
     *
     * @param classLoader 当前类加载器
     * @param module      XposedModule 实例
     * @param cb          数据回调
     */
    public static void showEncryptKey(ClassLoader classLoader, XposedModule module, Callback<Map<String, String[]>> cb) {
        try {
            Object deviceManager = Install.getDeviceManager(classLoader);
            if (deviceManager == null) {
                cb.onError("Failed to getDeviceManager", null);
                return;
            }

            Method getDeviceList = deviceManager.getClass().getDeclaredMethod("getDeviceList");
            getDeviceList.setAccessible(true);
            List<?> infoList = (List<?>) getDeviceList.invoke(deviceManager);

            Map<String, String[]> result = new HashMap<>();

            for (Object o : infoList) {
                Method getDid = o.getClass().getDeclaredMethod("getDid");
                getDid.setAccessible(true);
                String did = (String) getDid.invoke(o);
                if (did == null) {
                    continue;
                }

                Method getName = o.getClass().getDeclaredMethod("getName");
                getName.setAccessible(true);
                String name = (String) getName.invoke(o);

                Method getDetail = o.getClass().getDeclaredMethod("getDetail");
                getDetail.setAccessible(true);
                Object detail = getDetail.invoke(o);
                if (detail == null) {
                    continue;
                }

                Method getEncryptKey = detail.getClass().getDeclaredMethod("getEncryptKey");
                getEncryptKey.setAccessible(true);
                String encryptKey = (String) getEncryptKey.invoke(detail);
                result.put(did, new String[]{name, encryptKey});
            }
            cb.onSuccess(result);
        } catch (Exception e) {
            cb.onError(e.getMessage(), e);
        }
    }
}