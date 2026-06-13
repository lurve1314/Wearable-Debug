# Wearable Debug

Xposed插件启用Mi Fitness第三方小程序安装卸载，表盘安装，固件安装，试用表盘导出。

激活插件后会将Profile->About this app->User Agreement页面替换为隐藏的小程序安装界面。

## 注意

当前代码仅**8Pro**在**Mi Fitness 3.43.0i**版本和**小米运动健康3.44.0**测试正常。

## 功能开关

本模块提供了设置界面，可以控制以下功能的开启/关闭：

1. **试用表盘功能** - 从试用目录提取表盘文件到 Download/WearableDebugTrials 目录
2. **屏蔽广告** - 拦截广告加载、隐藏健康问诊横幅
3. **屏蔽连接保护通知** - 关闭连接保护弹窗和红点提示
4. **调试日志** - 开启调试日志输出

### 打开设置界面

安装APK后，打开应用即可进入设置界面。

## 使用

### 试用表盘导出

1. 在小米运动健康App中点击"试用"表盘
2. 回到本模块，选择"Trial WatchFace"
3. 表盘文件将导出到 `/sdcard/Download/WearableDebugTrials/` 目录
4. **注意**：导出后需要修改表盘ID才能正常安装（手动修改或使用AstroBox等工具）

### 表盘安装

点击Profile->About this app->User Agreement后在弹窗中选择WatchFace。不需要输入包名，点击install third app选择表盘文件。等待安装完成。

### 固件安装

**注意该功能可能导致设备变砖，请谨慎使用**

点击Profile->About this app->User Agreement后在弹窗中选择Firmware。不需要输入包名，点击install third app选择固件文件。在更新页面点击Download。

### 小程序安装

点击Profile->About this app->User Agreement后在弹窗中选择App。先输入包名后点击install third app选择小程序文件，安装时可以随便输入包名。仅卸载时需要完整包名。

### 日志拉取

点击Profile->About this app->User Agreement后在弹窗中选择Pull log。日志会输出到/sdcard/Android/data/[应用包名]/files/log/devicelog/

### 获取EncryptKey

点击Profile->About this app->User Agreement后在弹窗中选择Encrypt Key。获取当前设备上保存的EncryptKey信息。输出格式为：设备Did: [设备名称, Encrypt Key]
