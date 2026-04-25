# BlackObfuscator Gradle Android Plugin 使用说明

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 适用对象：`com.android.application`
- 插件模块：`blackobfuscator-gradle-plugin`

## 兼容性

当前仓库已经验证以下组合可以完成插件模块构建：

| Gradle | JDK | 结果 |
|---|---|---|
| `6.9.1` | `11` | 通过 |
| `8.7` | `17` | 通过 |

本次兼容处理主要包含：

- 将仓库的旧式 `compile` / `testCompile` 升级为新依赖配置
- 移除 Gradle 8 不兼容的旧 `maven` 插件依赖
- 将跨模块依赖改为 `api`，保留老工程依赖的传递可见性
- 将插件实现改为尽量通过反射访问 Android Gradle Plugin API，减少对单一 AGP 版本的硬绑定

说明：

- 这里的“通过”是指当前仓库插件模块可成功构建
- 具体 app 项目接入时，还需要它自己的 AGP / Gradle / JDK 组合本身是合法的

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

如果你要分别验证两套环境，可以参考：

```powershell
# Gradle 6 + JDK 11
gradle-6.9.1\bin\gradle.bat :blackobfuscator-gradle-plugin:build

# Gradle 8 + JDK 17
gradle-8.7\bin\gradle.bat :blackobfuscator-gradle-plugin:build
```

## 在 Android 项目中使用

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator'
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

### Kotlin DSL 示例

```kotlin
plugins {
    id("com.android.application")
    id("zym.top.blackobfuscator")
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = "123456"
            keyAlias = "release"
            keyPassword = "123456"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

blackObfuscator {
    isEnabled = true
    isAutoRun = false
    depth = 1
    packageName = "com.example.app"
    // 或者 rulesFile = file("blackobfuscator-rules.txt")
    variants = mutableListOf("release")
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
- 插件当前更适合处理 `application` 产出的 APK，不适用于 `library`

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
- 如果是旧工程，优先使用 `Gradle 6.x + JDK 11`
- 如果是新工程，优先使用 `Gradle 8.x + JDK 17`
- 你的 Android 工程自身还需要满足对应 AGP 的官方版本要求

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
