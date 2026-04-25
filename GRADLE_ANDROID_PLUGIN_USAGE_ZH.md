# BlackObfuscator Gradle Android Plugin 使用说明

## 插件信息

- 插件 ID：`top.niunaijun.blackobfuscator`
- 适用对象：`com.android.application`
- 插件模块：`blackobfuscator-gradle-plugin`

## 当前实现能力

当前插件实现的是 APK 后处理方案，而不是直接插入 AGP 内部字节码管线。

执行流程：

1. 先构建目标 variant 的 APK
2. 提取 APK 中的 `classes*.dex`
3. 对命中的 dex 调用 `BlackObfuscatorCmd`
4. 将混淆后的 dex 回填到 APK
5. 执行 `zipalign`
6. 使用 variant 的 `signingConfig` 重新签名
7. 输出新的 APK 文件

默认输出文件名后缀为：

```text
-blackobf.apk
```

## 在当前仓库中构建插件

```powershell
gradle :blackobfuscator-gradle-plugin:build
```

如果你补齐了 Gradle wrapper，也可以使用：

```powershell
.\gradlew :blackobfuscator-gradle-plugin:build
```

## 在 Android 项目中使用

```groovy
plugins {
    id 'com.android.application'
    id 'top.niunaijun.blackobfuscator'
}

blackObfuscator {
    enabled = true
    autoRun = false
    depth = 1
    packageName = "com.example.app"
    // 或者使用 rulesFile = file("blackobfuscator-rules.txt")
    variants = ["release"]
    outputSuffix = "-blackobf"
}
```

## 配置项说明

| 配置项 | 类型 | 说明 |
|---|---|---|
| `enabled` | `boolean` | 是否启用插件 |
| `autoRun` | `boolean` | 是否自动挂到 `assemble<Variant>` 后执行 |
| `depth` | `int` | 混淆深度，建议从 `1` 开始 |
| `packageName` | `String` | 需要混淆的包名 |
| `rulesFile` | `Object` | 规则文件路径，通常写成 `file("xxx.txt")` |
| `variants` | `List<String>` | 指定要处理的 variant，例如 `["release"]` |
| `outputSuffix` | `String` | 输出 APK 的后缀 |

约束：

- `packageName` 和 `rulesFile` 必须二选一
- 必须存在有效的 Android `signingConfig`
- 本机需要可用的 Android SDK 和 `build-tools`

## 手动执行任务

如果 `variants = ["release"]`，会生成任务：

```powershell
gradle blackObfuscateRelease
```

如果没有显式配置 `variants`，插件会为所有 application variant 注册任务。

## autoRun 行为

当你设置：

```groovy
blackObfuscator {
    autoRun = true
    variants = ["release"]
}
```

则插件会把 `blackObfuscateRelease` 挂到 `assembleRelease` 后面，在 APK 构建完成后自动执行。

## 输出位置

输出 APK 与原 APK 位于同级目录，默认文件名类似：

```text
app-release-blackobf.apk
```

## 运行要求

- Android SDK 可通过 `local.properties` 的 `sdk.dir` 找到
- 或设置 `ANDROID_SDK_ROOT` / `ANDROID_HOME`
- `build-tools` 中需要存在 `zipalign` 和 `apksigner`
- 当前工程建议使用 JDK 11 构建

## 常见问题

### 1. 没有找到 APK 输出

请先确认目标 variant 能正常执行 `assemble<Variant>`，并且 APK 确实生成在 `build/outputs/apk` 目录下。

### 2. 没有 dex 被混淆

通常是以下原因：

- `packageName` 没命中业务代码
- `rulesFile` 写得过窄
- 对应 dex 中没有匹配到类

### 3. 签名失败

请检查目标 variant 是否配置了完整的：

- `storeFile`
- `storePassword`
- `keyAlias`
- `keyPassword`

### 4. 安装失败

如果混淆后 APK 的签名与手机中已安装版本不一致，需要先卸载旧版本再安装。
