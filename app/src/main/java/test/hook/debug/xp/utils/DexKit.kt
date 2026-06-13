package test.hook.debug.xp.utils

import org.luckypray.dexkit.DexKitBridge

/**
 * DexKit 工具
 *
 * @author YifePlayte
 */
object DexKit {
    private lateinit var hostDir: String
    private var isInitialized = false
    val dexKitBridge: DexKitBridge by lazy {
        System.loadLibrary("dexkit")
        DexKitBridge.create(hostDir)!!.also {
            isInitialized = true
        }
    }

    /**
     * 初始化 DexKit 的 apk 完整路径
     */
    fun initDexKit(sourceDir: String) {
        hostDir = sourceDir
    }

    /**
     * 关闭 DexKit bridge
     */
    fun closeDexKit() {
        if (isInitialized) dexKitBridge.close()
    }
}