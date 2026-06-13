# libxposed API 102
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# DexKit
-dontwarn org.luckypray.**

# Keep all hook-related classes
-keep class test.hook.debug.xp.MainHook { *; }
-keep class test.hook.debug.xp.DisableAd { *; }
-keep class test.hook.debug.xp.DisableKeepLinkNotify { *; }
-keep class test.hook.debug.xp.Install { *; }
-keep class test.hook.debug.xp.DeviceLog { *; }
-keep class test.hook.debug.xp.EncryptKey { *; }
-keep class test.hook.debug.xp.EntryPoint { *; }
-keep class test.hook.debug.xp.Callback { *; }
-keep class test.hook.debug.xp.Res { *; }
-keep class test.hook.debug.xp.ui.** { *; }
-keep class test.hook.debug.xp.utils.** { *; }