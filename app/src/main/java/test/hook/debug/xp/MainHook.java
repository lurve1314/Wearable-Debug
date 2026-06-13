package test.hook.debug.xp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedModule;
import test.hook.debug.xp.ui.DialogView;
import test.hook.debug.xp.utils.DexKit;
import test.hook.debug.xp.utils.Save;
import test.hook.debug.xp.utils.Settings;

/**
 * MainHook - API 102 XposedModule entry point.
 *
 * Replaces old IXposedHookLoadPackage + IXposedHookInitPackageResources + IXposedHookZygoteInit.
 */
public class MainHook extends XposedModule {
    private static final String TAG = "WearableDebug";
    private static Context moduleContext;

    public MainHook() {
    }

    /**
     * Get a static method by name from a class.
     */
    private static Method findStaticMethod(String className, ClassLoader cl, String methodName, Class<?>... paramTypes) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> clazz = Class.forName(className, false, cl);
        Method m = clazz.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m;
    }

    /**
     * Find a method by name in a class.
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m;
    }

    /**
     * Get a class by name, returns null if not found.
     */
    private static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded: " + param.getProcessName());
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        Settings.init(this);
        if (Settings.isZenModeSyncEnabled()) {
            loadHookForApi35Zen(param.getClassLoader());
        }
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
        Settings.init(this);

        if ("android".equals(packageName)) {
            // Android system server - zen mode hook handled in onSystemServerStarting
            return;
        }

        if (!"com.xiaomi.wearable".equals(packageName) && !"com.mi.health".equals(packageName)) {
            return;
        }

        log(Log.INFO, TAG, "onPackageLoaded: " + packageName);
        ClassLoader classLoader = param.getDefaultClassLoader();

        // Initialize DexKit with the target app's source dir
        DexKit.INSTANCE.initDexKit(param.getApplicationInfo().sourceDir);

        // 广告屏蔽
        if (Settings.isDisableAdEnabled()) {
            DisableAd.interceptAd(classLoader, this);
            DisableAd.disableReport(classLoader, this);
            DisableAd.hideAqView(classLoader, this);
        }

        // 连接保护通知屏蔽
        if (Settings.isDisableKeepLinkNotifyEnabled()) {
            DisableKeepLinkNotify.disableDeviceSystemRedDot(classLoader, this);
            DisableKeepLinkNotify.disableTabRedDot(classLoader, this);
            DisableKeepLinkNotify.disableDialog(classLoader, this);
        }

        // Main hooks
        loadHook(classLoader);

        DexKit.INSTANCE.closeDexKit();
    }

    private void gotoDebugPage(ClassLoader classLoader, Activity activity) {
        try {
            Class<?> xmsManager = findClass("com.xms.wearable.export.XmsManager", classLoader);
            if (xmsManager == null) return;
            Object companionObj = xmsManager.getDeclaredField("Companion").get(null);

            Class<?> xmsManagerExtKt = findClass("com.xms.wearable.export.XmsManagerExtKt", classLoader);
            if (xmsManagerExtKt == null) return;

            Method getInstance = xmsManagerExtKt.getDeclaredMethod("getInstance",
                    findClass("com.xms.wearable.export.XmsManager$Companion", classLoader));
            getInstance.setAccessible(true);
            Object instance = getInstance.invoke(null, companionObj);

            Method gotoDebug = instance.getClass().getDeclaredMethod("gotoDebugPage", Activity.class);
            gotoDebug.setAccessible(true);
            gotoDebug.invoke(instance, activity);
        } catch (Throwable e) {
            log(Log.ERROR, TAG, "gotoDebugPage", e);
        }
    }

    private AlertDialog.Builder createWarningDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(Res.getString(Res.firmware_warning_title));
        builder.setMessage(Res.getString(Res.firmware_warning));
        builder.setCancelable(false);
        return builder;
    }

    private Dialog createSelectDialog(ClassLoader loader, Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        DialogView view = DialogView.create(context);

        builder.setView(view.getView());
        AlertDialog result = builder.create();

        view.addNode(Save.Type.APP.getText(), v -> {
            Save.status = Save.Type.APP;
            gotoDebugPage(loader, (Activity) context);
            result.dismiss();
        });

        view.addNode(Save.Type.WATCHFACE.getText(), v -> {
            Save.status = Save.Type.WATCHFACE;
            gotoDebugPage(loader, (Activity) context);
            result.dismiss();
        });

        view.addNode(Save.Type.TRIAL_WATCHFACE.getText(), v -> {
            if (Install.handleTrialWatchFace(context)) {
                Toast.makeText(context, "试用表盘已导出", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "试用表盘导出失败", Toast.LENGTH_SHORT).show();
            }
            result.dismiss();
        });

        view.addNode(Save.Type.FIRMWARE.getText(), v -> {
            AlertDialog.Builder warningDialog = createWarningDialog(context);
            warningDialog.setPositiveButton("OK", (dialog, which) -> {
                Save.status = Save.Type.FIRMWARE;
                gotoDebugPage(loader, (Activity) context);
            });
            warningDialog.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            warningDialog.show();
            result.dismiss();
        });

        view.addNode(Save.Type.PULL_LOG.getText(), v -> {
            DeviceLog.pullLog(loader, this, new Callback<String>() {
                @Override
                public void onError(String msg, Throwable e) {
                    Toast.makeText(context, String.format(java.util.Locale.getDefault(), "%s: %s\n%s",
                                    Res.getString(Res.fail_log), msg, Log.getStackTraceString(e)),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(String path) {
                    Toast.makeText(context, String.format(java.util.Locale.getDefault(), "%s: %s",
                            Res.getString(Res.success_log), path), Toast.LENGTH_SHORT).show();
                }
            });
            result.dismiss();
        });

        view.addNode(Save.Type.ENCRYPT_KEY.getText(), v -> {
            EncryptKey.showEncryptKey(loader, this, new Callback<Map<String, String[]>>() {
                @Override
                public void onError(String msg, Throwable e) {
                    Toast.makeText(context, String.format(java.util.Locale.getDefault(), "%s: %s\n%s",
                                    Res.getString(Res.fail_log), msg, Log.getStackTraceString(e)),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(Map<String, String[]> obj) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    EditText text = new EditText(context);
                    text.setTextColor(context.getColor(android.R.color.primary_text_light));
                    text.setBackground(context.getDrawable(android.R.drawable.edit_text));
                    text.setHintTextColor(context.getColor(android.R.color.darker_gray));

                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String[]> entry : obj.entrySet()) {
                        sb.append(entry.getKey()).append(": ").append(Arrays.toString(entry.getValue())).append("\n");
                    }
                    text.setText(sb.toString());
                    builder.setView(text);
                    builder.show();
                }
            });
            result.dismiss();
        });

        return result;
    }

    private static void onHandleApp(Object thisObj, Intent intent) {
        try {
            Method prepareInstall = findMethod(thisObj.getClass(), "prepareInstall", String.class, Intent.class);
            prepareInstall.invoke(thisObj, "thirdapp.rpk", intent);
        } catch (Throwable e) {
            Log.e(TAG, "onHandleApp", e);
        }
    }

    private boolean onHandleWatchFace(ClassLoader loader, Context context, Uri data) throws Throwable {
        File tmpFace = Install.saveTmpFile(context, data);
        if (tmpFace == null) {
            return false;
        }
        Install.installWatchFace(loader, tmpFace, context, this);
        return true;
    }

    private boolean onHandleFirmware(ClassLoader loader, Context context, Uri data) throws Throwable {
        File tmpFace = Install.saveTmpFile(context, data);
        if (tmpFace == null) {
            return false;
        }
        Install.invokeUpdate(loader, context, tmpFace.getAbsolutePath(), this);
        return true;
    }

    @SuppressLint("DiscouragedApi")
    private void loadHook(ClassLoader classLoader) {
        try {
            // Initialize context from AboutActivity
            Class<?> clazzAboutActivity = findClass("com.xiaomi.fitness.about.AboutActivity", classLoader);
            if (clazzAboutActivity == null) {
                log(Log.ERROR, TAG, "AboutActivity not found");
                return;
            }

            Method methodOnCreate = findMethod(clazzAboutActivity, "onCreate", Bundle.class);
            hook(methodOnCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (moduleContext == null) {
                    moduleContext = activity;
                    // Initialize Res
                    Res.init(moduleContext);
                }
                return chain.proceed();
            });

            Class<?> thirdAppDebugFragment = findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader);
            if (thirdAppDebugFragment == null) {
                log(Log.ERROR, TAG, "ThirdAppDebugFragment not found");
                return;
            }

            Method methodStartWebView = EntryPoint.findEntryPoint(classLoader, this);
            if (methodStartWebView == null) {
                log(Log.ERROR, TAG, "Entry point not found - current version may not be supported");
                return;
            }

            log(Log.INFO, TAG, "Entry point: " + methodStartWebView);

            hook(methodStartWebView).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                if (args.length < 2 || moduleContext == null) {
                    return chain.proceed();
                }

                Context appContext = moduleContext;
                Resources appResources = appContext.getResources();
                int identifierAboutPrivacyLicenseAgreement = appResources.getIdentifier(
                        "about_privacy_license_agreement", "string", "com.xiaomi.wearable");
                if (identifierAboutPrivacyLicenseAgreement == 0) {
                    identifierAboutPrivacyLicenseAgreement = appResources.getIdentifier(
                            "about_privacy_license_agreement", "string", "com.mi.health");
                }
                if (identifierAboutPrivacyLicenseAgreement == 0) {
                    return chain.proceed();
                }
                String stringAboutPrivacyLicenseAgreement = appResources.getString(identifierAboutPrivacyLicenseAgreement);

                if (!stringAboutPrivacyLicenseAgreement.equals(args[1])) {
                    return chain.proceed();
                }

                Activity activity = (Activity) appContext;
                Dialog dialog = createSelectDialog(classLoader, activity);
                activity.runOnUiThread(dialog::show);

                // Intercept onActivityResult
                hook(findMethod(thirdAppDebugFragment, "onActivityResult", int.class, int.class, Intent.class))
                        .intercept(chain2 -> {
                            if (((int) chain2.getArgs().get(0)) != 10 || ((int) chain2.getArgs().get(1)) != -1) {
                                return chain2.proceed();
                            }
                            Intent arg = (Intent) chain2.getArgs().get(2);
                            Uri data = arg.getData();
                            if (data == null) {
                                return null;
                            }

                            Method getMActivity = findMethod(thirdAppDebugFragment, "getMActivity");
                            Context context = (Context) getMActivity.invoke(chain2.getThisObject());

                            switch (Save.status) {
                                case APP:
                                    onHandleApp(chain2.getThisObject(), arg);
                                    break;
                                case WATCHFACE:
                                    if (!onHandleWatchFace(classLoader, context, data)) {
                                        Toast.makeText(context, Res.getString(Res.fail_watchface), Toast.LENGTH_LONG).show();
                                    }
                                    break;
                                case TRIAL_WATCHFACE:
                                    if (Install.handleTrialWatchFace(context)) {
                                        Toast.makeText(context, "试用表盘已导出", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, "试用表盘导出失败", Toast.LENGTH_SHORT).show();
                                    }
                                    break;
                                case FIRMWARE:
                                    if (!onHandleFirmware(classLoader, context, data)) {
                                        Toast.makeText(context, Res.getString(Res.fail_firmware), Toast.LENGTH_LONG).show();
                                    }
                                    break;
                                default:
                                    throw new IllegalStateException("Unexpected value: " + Save.status);
                            }
                            return null;
                        });

                return null;
            });

            // unInstallApp
            Method unInstallAppMethod = findMethod(thirdAppDebugFragment, "unInstallApp");
            hook(unInstallAppMethod).intercept(chain -> {
                Install.unInstall(classLoader, chain.getThisObject(), this);
                return null;
            });

            // sendThirdAppFile - save sign
            Method sendThirdAppFile = findMethod(thirdAppDebugFragment, "sendThirdAppFile", File.class, int.class);
            hook(sendThirdAppFile).intercept(chain -> {
                Save.sign = (byte[]) findMethod(
                        findClass("test.hook.debug.xp.utils.SignUtils", classLoader),
                        "generateSign", File.class
                ).invoke(null, (File) chain.getArgs().get(0));
                return chain.proceed();
            });

            // isPackageReady - don't restrict package name for watchface install
            Method isPackageReady = findMethod(thirdAppDebugFragment, "isPackageReady");
            hook(isPackageReady).intercept(chain -> {
                if (Save.status == Save.Type.APP) {
                    return chain.proceed();
                }
                return true;
            });

            // 微信通话6s强提醒
            if (Settings.isWeChatCallAlertEnabled()) {
                try {
                    Class<?> nhc = findClass("com.xiaomi.fitness.notify.util.NotificationFilterHelper", classLoader);
                    if (nhc != null) {
                        Method isWeChatIncomingCall = findMethod(nhc, "isWeChatIncomingCall",
                                findClass("android.service.notification.StatusBarNotification", classLoader),
                                String.class, String.class, String.class);
                        hook(isWeChatIncomingCall).intercept(chain -> {
                            String title = (String) chain.getArgs().get(1);
                            String text = (String) chain.getArgs().get(2);
                            boolean result = (boolean) chain.proceed();

                            if (result && title != null && text != null) {
                                try {
                                    Class<?> bleNotifyModelClass = findClass("com.xiaomi.fitness.notify.BleNotifyModel", classLoader);
                                    Object bleNotifyModel = bleNotifyModelClass.getDeclaredField("INSTANCE").get(null);
                                    Method updatePhoneStatus = findMethod(bleNotifyModelClass, "updatePhoneStatus", int.class);
                                    Method addCallNotification = findMethod(bleNotifyModelClass, "addCallNotification",
                                            String.class, String.class, int.class,
                                            findClass("com.xiaomi.fitness.settingitem.settingitem.InCall", classLoader));

                                    updatePhoneStatus.invoke(bleNotifyModel, 1);
                                    addCallNotification.invoke(bleNotifyModel, text, null, 1, null);

                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(6000);
                                        } catch (InterruptedException ignored) {
                                        }
                                        try {
                                            updatePhoneStatus.invoke(bleNotifyModel, 0);
                                            findMethod(bleNotifyModelClass, "alertInCallStop").invoke(bleNotifyModel);
                                        } catch (Throwable e) {
                                            log(Log.ERROR, TAG, "alertInCallStop", e);
                                        }
                                    }).start();
                                } catch (Throwable e) {
                                    log(Log.ERROR, TAG, "WeChat call alert error", e);
                                }
                            }
                            return result;
                        });
                    }
                } catch (Throwable e) {
                    log(Log.WARN, TAG, "WeChat call alert hook failed", e);
                }
            }

            // 勿扰模式同步 - isSupportZenMode
            if (Settings.isZenModeSyncEnabled()) {
                try {
                    Class<?> zenUtils = findClass("com.xiaomi.fitness.devicesettings.utils.ZenUtils", classLoader);
                    Class<?> wearableDeviceModel = findClass("com.xiaomi.fitness.device.manager.export.WearableDeviceModel", classLoader);
                    if (zenUtils != null && wearableDeviceModel != null) {
                        Method isSupportZenMode = findMethod(zenUtils, "isSupportZenMode", wearableDeviceModel);
                        hook(isSupportZenMode).intercept(chain -> {
                            return true;
                        });
                    }
                } catch (Throwable e) {
                    log(Log.WARN, TAG, "isSupportZenMode hook failed", e);
                }
            }

            // 一加日程导入修复
            if (Settings.isOneplusCalendarFixEnabled()) {
                try {
                    Class<?> romUtils = findClass("com.xiaomi.fitness.common.utils.RomUtils", classLoader);
                    if (romUtils != null) {
                        Method isOppo = findMethod(romUtils, "isOppo");
                        hook(isOppo).intercept(chain -> {
                            if (Build.BRAND.toLowerCase().contains("oneplus") || Build.MANUFACTURER.toLowerCase().contains("oneplus")) {
                                return true;
                            }
                            return chain.proceed();
                        });
                    }
                } catch (Throwable e) {
                    log(Log.WARN, TAG, "OnePlus calendar fix hook failed", e);
                }
            }

        } catch (Throwable e) {
            log(Log.ERROR, TAG, "loadHook error", e);
        }
    }

    private void loadHookForApi35Zen(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < 35) return;

        try {
            log(Log.INFO, TAG, "Searching SystemServer anonymous inner classes for setInterruptionFilter...");

            for (int i = 1; i <= 25; i++) {
                String className = "com.android.server.notification.NotificationManagerService$" + i;

                Class<?> innerClass = findClass(className, classLoader);
                if (innerClass == null) {
                    break;
                }
                try {
                    Method method = findMethod(innerClass, "setInterruptionFilter", String.class, int.class, boolean.class);

                    log(Log.INFO, TAG, "Found target anonymous class: " + className);

                    hook(method).intercept(chain -> {
                        String pkg = (String) chain.getArgs().get(0);

                        if ("com.xiaomi.wearable".equals(pkg) || "com.mi.health".equals(pkg)) {
                            long token = android.os.Binder.clearCallingIdentity();
                            chain.getArgs().set(0, "android");
                            log(Log.INFO, TAG, "SystemServer: matched Mi Fitness, UID elevated and package name disguised");

                            try {
                                Object result = chain.proceed();
                                return result;
                            } finally {
                                android.os.Binder.restoreCallingIdentity(token);
                            }
                        }
                        return chain.proceed();
                    });

                    log(Log.INFO, TAG, "SystemServer: anonymous inner class hook injected successfully");
                    return;
                } catch (NoSuchMethodException e) {
                    // Not the right anonymous class, continue
                }
            }

            log(Log.WARN, TAG, "setInterruptionFilter method not found in any anonymous class");

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "SystemServer exhaustive hook error: " + t);
        }
    }
}