package test.hook.debug.xp;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.base.StringMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedModule;
import test.hook.debug.xp.utils.DexKit;

public class EntryPoint {
    private static final String TAG = "EntryPoint";

    /**
     * Find the entry point method (startWebView) using DexKit
     */
    public static Method findEntryPoint(ClassLoader classLoader, XposedModule module) {
        Method method = findInvoker(classLoader, module);
        if (method == null) {
            return fallback(classLoader);
        }
        return method;
    }

    /**
     * Use DexKit to find the startWebView invoker
     */
    private static Method findInvoker(ClassLoader classLoader, XposedModule module) {
        try {
            DexKitBridge bridge = DexKit.INSTANCE.getDexKitBridge();
            ClassData classData = bridge.getClassData("com.xiaomi.fitness.about.AboutActivity");
            if (classData == null) {
                return null;
            }

            MethodDataList method = classData.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().name(StringMatcher.create("onCreate$lambda", StringMatchType.StartsWith))));

            for (int i = 0; i < method.size(); i++) {
                MethodData data = method.get(i);

                MethodDataList invokes = data.getInvokes();
                for (int j = 0; j < invokes.size(); j++) {
                    MethodData invoker = invokes.get(j);
                    ClassData invokerDeclaredClass = invoker.getDeclaredClass();
                    if (invokerDeclaredClass == null) {
                        continue;
                    }
                    String name = invokerDeclaredClass.getName();
                    if ("com.xiaomi.fitness.webview.WebViewUtilKt".equals(name)) {
                        try {
                            return invoker.getMethodInstance(classLoader);
                        } catch (NoSuchMethodException e) {
                            module.log(android.util.Log.ERROR, TAG, "Failed to instance entry point", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            module.log(android.util.Log.ERROR, TAG, "DexKit entry point search failed", e);
        }
        return null;
    }

    /**
     * Fallback: find startWebView method using reflection
     */
    private static Method fallback(ClassLoader classLoader) {
        try {
            Class<?> clazzWebViewUtilKt = Class.forName("com.xiaomi.fitness.webview.WebViewUtilKt", false, classLoader);

            // 小米运动健康 3.21.0+ 参数类型
            Class<?>[] paramTypes1 = {String.class, String.class, boolean.class, boolean.class, Integer.class, boolean.class, Boolean.class};
            Method method = findMethodByNameAndParams(clazzWebViewUtilKt, "startWebView", paramTypes1);
            if (method != null) {
                return method;
            }

            // 老版本参数类型
            Class<?>[] paramTypes2 = {String.class, String.class, boolean.class, boolean.class, Integer.class};
            return findMethodByNameAndParams(clazzWebViewUtilKt, "startWebView", paramTypes2);
        } catch (Exception e) {
            android.util.Log.e(TAG, "EntryPoint fallback failed", e);
        }
        return null;
    }

    /**
     * Find a method by name and parameter types
     */
    private static Method findMethodByNameAndParams(Class<?> clazz, String name, Class<?>[] paramTypes) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                if (!m.getName().equals(name)) {
                    continue;
                }
                Class<?>[] mParams = m.getParameterTypes();
                if (isAssignable(mParams, paramTypes)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "findMethodByNameAndParams failed", e);
        }
        return null;
    }

    /**
     * Check if method parameters are assignable from the given types
     */
    private static boolean isAssignable(Class<?>[] methodParams, Class<?>[] requiredParams) {
        if (methodParams.length != requiredParams.length) {
            return false;
        }
        for (int i = 0; i < methodParams.length; i++) {
            if (!methodParams[i].isAssignableFrom(requiredParams[i]) &&
                !isPrimitiveMatch(methodParams[i], requiredParams[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Handle primitive type matching
     */
    private static boolean isPrimitiveMatch(Class<?> primitive, Class<?> wrapper) {
        if (!primitive.isPrimitive()) {
            return false;
        }
        if (primitive == boolean.class && wrapper == Boolean.class) return true;
        if (primitive == int.class && wrapper == Integer.class) return true;
        if (primitive == long.class && wrapper == Long.class) return true;
        if (primitive == float.class && wrapper == Float.class) return true;
        if (primitive == double.class && wrapper == Double.class) return true;
        if (primitive == short.class && wrapper == Short.class) return true;
        if (primitive == byte.class && wrapper == Byte.class) return true;
        if (primitive == char.class && wrapper == Character.class) return true;
        return false;
    }
}