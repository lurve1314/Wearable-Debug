package test.hook.debug.xp;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.github.libxposed.api.XposedModule;
import test.hook.debug.xp.utils.Save;

/**
 * Install - API 102 version
 * 移除了 EzXHelper、XposedHelpers、MethodFinder 依赖，改为原生反射
 */
public class Install {
    private static final String TAG = "Install";

    /**
     * 读取指定表盘文件ID
     *
     * @param file 表盘文件路径
     * @return 表盘文件ID
     */
    public static String getWatchFaceId(File file) {
        if (!file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (FileInputStream stream = new FileInputStream(file)) {
            if (stream.skip(40) != 40) {
                return null;
            }
            int read;
            while ((read = stream.read()) != 0) {
                builder.append((char) read);
            }
        } catch (IOException e) {
            Log.e(TAG, "getWatchFaceId", e);
        }
        return builder.toString();
    }

    /**
     * 启动升级页面
     *
     * @param loader  当前类加载器
     * @param context 上下文用于Intent
     * @param path    固件文件位置
     * @param module  XposedModule 实例
     */
    public static void invokeUpdate(ClassLoader loader, Object context, String path, XposedModule module) {
        try {
            Class<?> checkUpdateExtKt = Class.forName("com.mi.fitness.checkupdate.export.CheckUpdateExtKt", false, loader);
            Method getCheckUpdateManager = checkUpdateExtKt.getDeclaredMethod("getCheckUpdateManager");
            getCheckUpdateManager.setAccessible(true);
            Object manager = getCheckUpdateManager.invoke(null);

            Method manualUpgrade = manager.getClass().getDeclaredMethod("manualUpgrade", Context.class, String.class, boolean.class);
            manualUpgrade.setAccessible(true);
            manualUpgrade.invoke(manager, context, path, false);
        } catch (Throwable e) {
            Log.w(TAG, "invokeUpdate new version not found", e);

            try {
                // 旧版本
                Class<?> checkUpdateManagerImpl = Class.forName("com.mi.fitness.checkupdate.util.CheckUpdateManagerImpl", false, loader);
                Object manager = checkUpdateManagerImpl.newInstance();

                Method manualUpgrade = manager.getClass().getDeclaredMethod("manualUpgrade", Context.class, String.class, boolean.class);
                manualUpgrade.setAccessible(true);
                manualUpgrade.invoke(manager, context, path, false);
            } catch (Throwable e2) {
                Log.e(TAG, "invokeUpdate fallback also failed", e2);
            }
        }
    }

    /**
     * 安装表盘文件
     *
     * @param loader  当前类加载器
     * @param file    表盘文件路径
     * @param context context上下文
     * @param module  XposedModule 实例
     */
    public static void installWatchFace(ClassLoader loader, File file, Context context, XposedModule module) {
        try {
            String watchFaceId = getWatchFaceId(file);
            if (watchFaceId == null) {
                Log.e(TAG, "Failed to get id from " + file.getAbsolutePath());
                return;
            }

            Class<?> model;
            try {
                model = Class.forName("com.xiaomi.fitness.watch.face.viewmodel.FaceBleInfoViewModel", false, loader);
            } catch (ClassNotFoundException e) {
                model = Class.forName("com.xiaomi.fitness.watch.face.viewmodel.FaceDetailViewModel", false, loader);
            }

            Object instance = model.newInstance();
            Field controllerField = instance.getClass().getDeclaredField("faceInstallController");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(instance);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(false);

            builder.setView(progressBar);
            AlertDialog dialog = builder.create();
            dialog.show();

            Class<?> callbackClass = Class.forName("com.xiaomi.fitness.watch.face.install.FaceInstallPushCallback", false, loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                try {
                    switch (method.getName()) {
                        case "onProgress": {
                            int pos = (int) args[0];
                            Log.i(TAG, "p: " + pos);
                            progressBar.setProgress(pos);
                            break;
                        }
                        case "onFinish": {
                            boolean success = (boolean) args[0];
                            int code = (int) args[1];
                            Log.i(TAG, "success: " + success + " code: " + code);
                            dialog.dismiss();
                            break;
                        }
                        case "onStart": {
                            Log.i(TAG, "start install");
                            break;
                        }
                        default: {
                            Log.e(TAG, "Unknown value: " + method.getName());
                        }
                    }
                } catch (Throwable e) {
                    Log.e(TAG, method.toString(), e);
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
                return null;
            });

            try {
                // 构造 Kotlin 的 Function3 代理，用于 preInstall 回调
                Class<?> function3Class = Class.forName("kotlin.jvm.functions.Function3", false, loader);
                Object dummyAction = Proxy.newProxyInstance(loader, new Class<?>[]{function3Class}, (proxy, method, args) -> {
                    // 安全起见，返回 kotlin.Unit.INSTANCE 以绕过 Kotlin 的非空检查
                    Class<?> unitClass = Class.forName("kotlin.Unit", false, loader);
                    return unitClass.getDeclaredField("INSTANCE").get(null);
                });

                // 先调用 preInstall 建立握手
                Method preInstall = controller.getClass().getDeclaredMethod("preInstall",
                        String.class, String.class, long.class, long.class, boolean.class,
                        String.class, String.class, Integer.class, function3Class);
                preInstall.setAccessible(true);
                preInstall.invoke(controller,
                        watchFaceId, file.getAbsolutePath(), 0L, file.length(), true,
                        null, null, 0, dummyAction);
            } catch (Throwable error) {
                // 旧版没这步骤
            }

            // 调用 doInstall 正式传输文件
            Method doInstall = controller.getClass().getDeclaredMethod("doInstall",
                    String.class, String.class, Integer.class, callbackClass);
            doInstall.setAccessible(true);
            doInstall.invoke(controller,
                    file.getAbsolutePath(), watchFaceId, 0, callback);

        } catch (Throwable e) {
            Log.e(TAG, "installWatchFace", e);
        }
    }

    /**
     * 获取设备管理器
     *
     * @param loader 当前类加载器
     * @return com.xiaomi.fitness.device.manager.WearableDeviceManagerImpl
     */
    public static Object getDeviceManager(ClassLoader loader) throws Exception {
        Class<?> deviceManager;
        try {
            deviceManager = Class.forName("com.xiaomi.fitness.device.manager.export.DeviceManager", false, loader);
        } catch (ClassNotFoundException e) {
            deviceManager = Class.forName("com.xiaomi.fitness.device.manager.export.WearableDeviceManager", false, loader);
        }

        Object companion = deviceManager.getDeclaredField("Companion").get(null);

        Class<?> deviceManagerExtKt = Class.forName("com.xiaomi.fitness.device.manager.export.DeviceManagerExtKt", false, loader);
        Method[] methods = deviceManagerExtKt.getDeclaredMethods();
        for (Method m : methods) {
            if ("getInstance".equals(m.getName()) && m.getParameterCount() == 1) {
                m.setAccessible(true);
                return m.invoke(null, companion);
            }
        }
        throw new NoSuchMethodException("getInstance not found in DeviceManagerExtKt");
    }

    public static Object getCurrentDevice(ClassLoader loader) throws Exception {
        Object instance = getDeviceManager(loader);

        Method getCurrentDeviceModel = instance.getClass().getDeclaredMethod("getCurrentDeviceModel");
        getCurrentDeviceModel.setAccessible(true);
        Object deviceModel = getCurrentDeviceModel.invoke(instance);

        if (deviceModel == null) {
            return null;
        }

        Method isDeviceConnected = deviceModel.getClass().getDeclaredMethod("isDeviceConnected");
        isDeviceConnected.setAccessible(true);
        if (!(boolean) isDeviceConnected.invoke(deviceModel)) {
            return null;
        }
        return deviceModel;
    }

    /**
     * 卸载应用
     *
     * @param loader  当前类加载器
     * @param thisObj 当前方法对象
     * @param module  XposedModule 实例
     */
    public static Object unInstall(ClassLoader loader, Object thisObj, XposedModule module) throws Exception {
        Object deviceModel = getCurrentDevice(loader);
        if (deviceModel == null) {
            return true;
        }

        Method getDid = deviceModel.getClass().getDeclaredMethod("getDid");
        getDid.setAccessible(true);
        Object did = getDid.invoke(deviceModel);

        Field pkgNameField = thisObj.getClass().getDeclaredField("pkgName");
        pkgNameField.setAccessible(true);
        Object pkgName = pkgNameField.get(thisObj);

        Class<?> deviceModelExtKt = Class.forName("com.xiaomi.xms.wearable.extensions.DeviceModelExtKt", false, loader);
        Class<?> callbackClass = Class.forName("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment$unInstallApp$1", false, loader);
        Class<?> fragmentClass = Class.forName("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", false, loader);
        Object callbackObj = callbackClass.getConstructor(fragmentClass, String.class).newInstance(thisObj, did);

        Method uninstallApp = null;
        for (Method m : deviceModelExtKt.getDeclaredMethods()) {
            if ("uninstallApp".equals(m.getName())) {
                uninstallApp = m;
                break;
            }
        }
        if (uninstallApp == null) {
            throw new NoSuchMethodException("uninstallApp not found in DeviceModelExtKt");
        }
        uninstallApp.setAccessible(true);

        // 如果 Save.sign 为空（未安装），造一个空的 20 字节签名数组
        byte[] signToUse = Save.sign;
        if (signToUse == null) {
            signToUse = new byte[20]; // SHA-1 的长度
        }

        uninstallApp.invoke(deviceModelExtKt, deviceModel, pkgName, signToUse, callbackObj);
        return false;
    }

    /**
     * 创建临时文件
     *
     * @param context 上下文
     * @param data    文件来源
     * @return 临时文件位置
     */
    public static File saveTmpFile(Context context, Uri data) throws Throwable {
        File tmpFace = new File(context.getCacheDir(), "tmpFile");
        try (FileOutputStream stream = new FileOutputStream(tmpFace)) {
            byte[] bytes = new byte[0x400];
            try (InputStream inputStream = context.getContentResolver().openInputStream(data)) {
                if (inputStream == null) {
                    return null;
                }
                int read;
                while ((read = inputStream.read(bytes)) != -1) {
                    stream.write(bytes, 0, read);
                }
            }
        }
        return tmpFace;
    }

    /**
     * 处理试用表盘文件
     *
     * @param context 上下文
     * @return true if success
     */
    public static boolean handleTrialWatchFace(Context context) {
        try {
            String pkg = context.getPackageName();
            File watchFaceDir = new File(context.getExternalFilesDir(null).getParentFile().getParentFile(), pkg + "/files/WatchFace");

            if (!watchFaceDir.exists()) {
                watchFaceDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + pkg + "/files/WatchFace");
            }

            if (!watchFaceDir.exists() || !watchFaceDir.isDirectory()) {
                Log.e(TAG, "WatchFace dir not found: " + watchFaceDir.getAbsolutePath());
                return false;
            }

            File[] faceFolders = watchFaceDir.listFiles();
            if (faceFolders == null || faceFolders.length == 0) {
                Log.e(TAG, "No trial watch faces found");
                return false;
            }

            // 找最新的
            File latestFolder = null;
            long lastMod = 0;
            for (File f : faceFolders) {
                if (f.lastModified() > lastMod) {
                    lastMod = f.lastModified();
                    latestFolder = f;
                }
            }

            if (latestFolder == null) return false;

            // 查找 resource.bin
            File resFile = findResourceFile(latestFolder);
            if (resFile == null) {
                Log.e(TAG, "resource.bin not found in " + latestFolder.getName());
                return false;
            }

            // 复制到 Download 目录
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputDir = new File(downloadDir, "WearableDebugTrials");
            if (!outputDir.exists()) outputDir.mkdirs();

            File destFile = new File(outputDir, "watchface_" + System.currentTimeMillis() + ".bin");

            // 复制文件
            copyFile(resFile, destFile);

            Toast.makeText(context, "已导出表盘到: " + destFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;

        } catch (Throwable e) {
            Log.e(TAG, "handleTrialWatchFace", e);
            return false;
        }
    }

    private static File findResourceFile(File dir) {
        File resource = new File(dir, "resource.bin");
        if (resource.exists()) return resource;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    resource = new File(f, "resource.bin");
                    if (resource.exists()) return resource;
                }
            }
        }
        return null;
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream is = new FileInputStream(src); FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }
}