package test.hook.debug.xp;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Random;

import de.robv.android.xposed.XposedHelpers;
import test.hook.debug.xp.utils.Save;

public class Install {
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
            Log.e(e, "getWatchFaceId");
        }
        return builder.toString();
    }

    /**
     * 启动升级页面
     *
     * @param loader  当前类加载器
     * @param context 上下文用于Intent
     * @param path    固件文件位置
     */
    public static void invokeUpdate(ClassLoader loader, Object context, String path) {
        try {
            Class<?> checkUpdateExtKt = XposedHelpers.findClass("com.mi.fitness.checkupdate.export.CheckUpdateExtKt", loader);
            Object manager = XposedHelpers.callStaticMethod(checkUpdateExtKt, "getCheckUpdateManager");

            XposedHelpers.callMethod(manager, "manualUpgrade", new Class[]{Context.class, String.class, boolean.class},
                    context, path, false);
        } catch (Throwable e) {
            Log.e(e, "invokeUpdate new version not found");

            // 旧版本
            Class<?> checkUpdateManagerImpl = XposedHelpers.findClass("com.mi.fitness.checkupdate.util.CheckUpdateManagerImpl", loader);
            Object manager = XposedHelpers.newInstance(checkUpdateManagerImpl);
            // boolean参数为true时为静默安装
            XposedHelpers.callMethod(manager, "manualUpgrade", new Class[]{Context.class, String.class, boolean.class},
                    context, path, false);
        }
    }

    /**
     * 安装表盘文件
     *
     * @param loader  当前类加载器
     * @param file    表盘文件路径
     * @param context context上下文
     */
    public static void installWatchFace(ClassLoader loader, File file, Context context) {
        try {
            String watchFaceId = getWatchFaceId(file);
            if (watchFaceId == null) {
                Log.e("Failed to get id from " + file.getAbsolutePath(), null);
                return;
            }

            Class<?> model;
            try {
                // 新
                model = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.viewmodel.FaceBleInfoViewModel", loader);
            } catch (XposedHelpers.ClassNotFoundError e) {
                model = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.viewmodel.FaceDetailViewModel", loader);
            }

            Object instance = model.newInstance();
            Object controller = XposedHelpers.getObjectField(instance, "faceInstallController");

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(false);

            builder.setView(progressBar);
            AlertDialog dialog = builder.create();
            dialog.show();

            Class<?> callbackClass = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.install.FaceInstallPushCallback", loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                try {
                    switch (method.getName()) {
                        case "onProgress": {
                            int pos = (int) args[0];
                            Log.i("p: " + pos, null);
                            progressBar.setProgress(pos);
                            break;
                        }
                        case "onFinish": {
                            boolean success = (boolean) args[0];
                            int code = (int) args[1];
                            Log.i("success: " + success + " code: " + code, null);
                            dialog.dismiss();
                            break;
                        }
                        case "onStart": {
                            Log.i("start install", null);
                            break;
                        }
                        default: {
                            Log.e("Unknown value: " + method.getName(), null);
                        }
                    }
                } catch (Throwable e) {
                    Log.e(e, method.toString());
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
                return null;
            });

            try {
                // 构造 Kotlin 的 Function3 代理，用于 preInstall 回调
                Class<?> function3Class = XposedHelpers.findClass("kotlin.jvm.functions.Function3", loader);
                Object dummyAction = Proxy.newProxyInstance(loader, new Class<?>[]{function3Class}, (proxy, method, args) -> {
                    // 安全起见，返回 kotlin.Unit.INSTANCE 以绕过 Kotlin 的非空检查
                    return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", loader), "INSTANCE");
                });

                // 先调用 preInstall 建立握手
                // preInstall(String id, String path, long version, long size, boolean needCheckStorage, String license, String sign, Integer trialDuration, Function3 action)
                XposedHelpers.callMethod(controller, "preInstall",
                        new Class<?>[]{
                                String.class, String.class, long.class, long.class, boolean.class,
                                String.class, String.class, Integer.class, function3Class
                        },
                        watchFaceId, file.getAbsolutePath(), 0L, file.length(), true,
                        null, null, 0, dummyAction
                );
            } catch (Throwable error) {
                // 旧版没这步骤
            }

            // 调用 doInstall 正式传输文件
            // doInstall(String path, String id, Integer segmentLength, FaceInstallPushCallback callback)
            XposedHelpers.callMethod(controller, "doInstall",
                    new Class<?>[]{
                            String.class, String.class, Integer.class, callbackClass
                    },
                    file.getAbsolutePath(), watchFaceId, 0, callback
            );

        } catch (Throwable e) {
            Log.e(e, "installWatchFace");
        }
    }

    /**
     * 获取设备管理器
     *
     * @param loader 当前类加载器
     * @return com.xiaomi.fitness.device.manager.WearableDeviceManagerImpl
     */
    public static Object getDeviceManager(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> deviceManager = ClassUtils.loadFirstClass("com.xiaomi.fitness.device.manager.export.DeviceManager", "com.xiaomi.fitness.device.manager.export.WearableDeviceManager");
        Object companion = XposedHelpers.getStaticObjectField(deviceManager, "Companion");
        Class<?> deviceManagerExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManagerExtKt", loader);
        return ClassUtils.invokeStaticMethodBestMatch(deviceManagerExtKt, "getInstance", null, companion);
    }

    public static Object getCurrentDevice(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException {
        Object instance = getDeviceManager(loader);
        Object deviceModel = XposedHelpers.callMethod(instance, "getCurrentDeviceModel");
        if (deviceModel == null || !(boolean) XposedHelpers.callMethod(deviceModel, "isDeviceConnected")) {
            return null;
        }
        return deviceModel;
    }

    /**
     * 卸载应用
     *
     * @param loader  当前类加载器
     * @param thisObj 当前方法对象
     */
    public static Object unInstall(ClassLoader loader, Object thisObj) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
        Object deviceModel = getCurrentDevice(loader);
        if (deviceModel == null) {
            return true;
        }

        Object did = XposedHelpers.callMethod(deviceModel, "getDid");
        Object pkgName = XposedHelpers.getObjectField(thisObj, "pkgName");
        Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.xms.wearable.extensions.DeviceModelExtKt", loader);
        Class<?> callback = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment$unInstallApp$1", loader);
        Object callbackObj = XposedHelpers.newInstance(callback, new Class<?>[]{XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", loader), String.class}, thisObj, did);

        Method uninstallApp = MethodFinder.fromClass(deviceModelExtKt).filterByName("uninstallApp").first();
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
     * 1. 从试用目录复制到 Download 目录
     * 2. 修改 ID (需要二进制修改) -> 暂时保留原 ID (需注意风险)，但必须复制出来
     *
     * @param context 上下文
     * @return true if success
     */
    public static boolean handleTrialWatchFace(Context context) {
        try {
            // 试用表盘通常路径: Android/data/com.mi.health/files/WatchFace/
            // 或者 Android/data/com.xiaomi.wearable/files/WatchFace/
            
            String pkg = context.getPackageName();
            File watchFaceDir = new File(context.getExternalFilesDir(null).getParentFile().getParentFile(), pkg + "/files/WatchFace");
            
            if (!watchFaceDir.exists()) {
                // 尝试旧路径
                watchFaceDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + pkg + "/files/WatchFace");
            }

            if (!watchFaceDir.exists() || !watchFaceDir.isDirectory()) {
                Log.e("WatchFace dir not found: " + watchFaceDir.getAbsolutePath(), null);
                return false;
            }

            File[] faceFolders = watchFaceDir.listFiles();
            if (faceFolders == null || faceFolders.length == 0) {
                Log.e("No trial watch faces found", null);
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
                Log.e("resource.bin not found in " + latestFolder.getName(), null);
                return false;
            }

            // 复制到 Download 目录
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputDir = new File(downloadDir, "WearableDebugTrials");
            if (!outputDir.exists()) outputDir.mkdirs();
            
            // 读取原始 ID
            String originalId = getWatchFaceId(resFile);
            String newId = generateNewId(originalId);
            
            File destFile = new File(outputDir, "watchface_" + System.currentTimeMillis() + ".bin");
            
            // 复制文件
            copyFile(resFile, destFile);
            
            // TODO: 修改二进制中的 ID (hard)
            // 暂时简单提示用户手动修改 ID，或者尝试简单的二进制替换 (如果 ID 长度相同)
            // 这里为了安全和实现复杂度，先只做提取，后续如果需要自动改 ID 再做。
            
            Toast.makeText(context, "已导出表盘到: " + destFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;

        } catch (Throwable e) {
            Log.e(e, "handleTrialWatchFace");
            return false;
        }
    }

    private static File findResourceFile(File dir) {
        File resource = new File(dir, "resource.bin");
        if (resource.exists()) return resource;
        
        // 递归查找一层
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

    private static String generateNewId(String original) {
        if (original == null) return String.valueOf(System.currentTimeMillis());
        // 简单修改前几位防止重复
        return "trial_" + original; 
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