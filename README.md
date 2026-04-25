# BlackObfuscator

![](https://img.shields.io/badge/language-java-brightgreen.svg)

BlackObfuscator 是一个面向 Android Dex/APK 的控制流混淆工具，基于 `dex2jar` 修改而来。它可以对命中的 `classes*.dex` 做控制流混淆，并提供一个可接入 Android `application` 工程的 Gradle 插件，在 APK 生成后自动执行混淆、重打包、`zipalign` 和重新签名。

## 项目说明

- 本项目基于 [dex2jar](https://github.com/pxb1988/dex2jar) 修改
- 更适合混淆业务代码，不建议无差别混淆第三方库
- 当前支持命令行方式和 Gradle Android Plugin 方式

## 版本兼容

当前仓库已经验证以下组合可完成插件模块构建：

| Gradle | JDK | 状态 |
|---|---|---|
| `6.9.1` | `11` | 通过 |
| `8.7` | `17` | 通过 |

为了兼容新旧环境，仓库已完成：

- 旧依赖 DSL 向新 DSL 迁移
- 跨模块依赖传递兼容处理
- Gradle 插件对 AGP API 的反射式适配

## 仓库结构

- `dex-obfuscator`
  - 混淆核心逻辑
- `dex-tools`
  - 命令行入口，包含 `BlackObfuscatorCmd`
- `blackobfuscator-gradle-plugin`
  - Gradle Android Plugin 模块
- `obfuscate-apk.ps1`
  - Windows / PowerShell 的 APK 混淆脚本

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 适用范围：`com.android.application`
- 当前模式：APK 后处理，不是 AGP 编译期字节码插桩

插件的执行流程如下：

1. 先执行 `assemble<Variant>`
2. 找到该变体输出的 APK
3. 提取 APK 中的 `classes*.dex`
4. 调用 `BlackObfuscatorCmd` 对命中的 dex 做混淆
5. 重新打包 APK
6. 执行 `zipalign`
7. 使用当前 variant 的 `signingConfig` 重新签名

## 接入步骤

下面先按“本地源码接入”的方式说明，这是最适合当前仓库的接入方式。

### 1. 准备条件

你的 Android app 项目需要满足：

- 使用 `com.android.application`
- 能正常执行 `assembleRelease` 或其他目标 variant
- 已配置可用的 `signingConfig`
- 本机有 Android SDK
- `build-tools` 中存在 `zipalign` 和 `apksigner`

建议版本：

- 旧工程优先使用 `Gradle 6.x + JDK 11`
- 新工程优先使用 `Gradle 8.x + JDK 17`

### 2. 让 app 工程能找到这个插件

最直接的方式是在你的 app 工程根目录 `settings.gradle` 或 `settings.gradle.kts` 中，把当前仓库作为一个本地 included build 引入。

Groovy DSL:

```groovy
pluginManagement {
    includeBuild("../BlackObfuscator")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YourApp"
include(":app")
```

Kotlin DSL:

```kotlin
pluginManagement {
    includeBuild("../BlackObfuscator")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YourApp"
include(":app")
```

说明：

- `../BlackObfuscator` 需要改成你本机这个仓库的真实路径
- 这样做之后，你的 app 项目就可以直接通过插件 ID 使用本地源码里的插件

### 3. 在 app 模块里应用插件

Groovy DSL，`app/build.gradle`：

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator'
}
```

Kotlin DSL，`app/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application")
    id("zym.top.blackobfuscator")
}
```

### 4. 配置 Android 签名

插件最终会重新签名 APK，所以目标 variant 必须有完整的 `signingConfig`。

Groovy DSL:

```groovy
android {
    signingConfigs {
        release {
            storeFile file("keystore/release.jks")
            storePassword "123456"
            keyAlias "release"
            keyPassword "123456"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

Kotlin DSL:

```kotlin
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
```

### 5. 配置 `blackObfuscator`

你必须二选一配置：

- `packageName`
- `rulesFile`

Groovy DSL:

```groovy
blackObfuscator {
    enabled = true
    autoRun = false
    depth = 1
    packageName = "com.example.app"
    // 或者 rulesFile = file("blackobfuscator-rules.txt")
    variants = ["release"]
    outputSuffix = "-blackobf"
}
```

Kotlin DSL:

```kotlin
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

### 6. 手动执行插件任务

如果你配置了：

```text
variants = ["release"]
```

那么会生成任务：

```bash
./gradlew blackObfuscateRelease
```

插件会先确保 `assembleRelease` 已执行，再对生成的 APK 做后处理。

### 7. 自动在 assemble 后执行

如果你希望每次 `assembleRelease` 后自动执行混淆，可以打开：

Groovy DSL:

```groovy
blackObfuscator {
    autoRun = true
    variants = ["release"]
}
```

Kotlin DSL:

```kotlin
blackObfuscator {
    isAutoRun = true
    variants = mutableListOf("release")
}
```

这样 `assembleRelease` 完成后会自动触发 `blackObfuscateRelease`。

### 8. 查看输出 APK

默认会在原 APK 同目录生成：

```text
app-release-blackobf.apk
```

如果你改了 `outputSuffix`，输出文件名也会随之变化。

## 配置项说明

| 配置项 | 类型 | 说明 |
|---|---|---|
| `enabled` | `boolean` | 是否启用插件 |
| `autoRun` | `boolean` | 是否自动挂到 `assemble<Variant>` 后执行 |
| `depth` | `int` | 混淆深度，建议从 `1` 开始 |
| `packageName` | `String` | 需要混淆的包名 |
| `rulesFile` | `Object` | 规则文件路径 |
| `variants` | `List<String>` | 指定要处理的 variant，例如 `["release"]` |
| `outputSuffix` | `String` | 输出 APK 的后缀 |

约束：

- `packageName` 和 `rulesFile` 必须二选一
- 必须存在有效的 Android `signingConfig`
- 本机需要可用的 Android SDK 和 `build-tools`
- 插件当前适用于 `application`，不适用于 `library`

## 命令行使用

命令行入口：

- [dex-tools/src/main/java/com/googlecode/dex2jar/tools/BlackObfuscatorCmd.java](dex-tools/src/main/java/com/googlecode/dex2jar/tools/BlackObfuscatorCmd.java)

参数说明：

| 参数 | 说明 |
|---|---|
| `-d` | 混淆深度，建议先从 `1` 开始 |
| `-i` | 输入 dex 路径 |
| `-o` | 输出 dex 路径 |
| `-a` | 规则文件路径 |
| `-p` | 指定要混淆的包名 |

示例：

```java
BlackObfuscatorCmd.main(
    "d2j-black-obfuscator",
    "-d", "2",
    "-i", "/path/classes.dex",
    "-o", "/path/classes_out.dex",
    "-a", "filter.txt"
);
```

## 规则文件示例

```text
# package
com.example.app

# class
com.example.app.MainActivity

# blacklist
!com.example.app.generated
```

规则说明：

- 普通行表示允许混淆
- `!` 开头表示排除
- `#` 开头表示注释

## 构建插件

如果本地已安装兼容版本的 Gradle，可以执行：

```powershell
gradle :blackobfuscator-gradle-plugin:build
```

## 更多文档

- [GRADLE_ANDROID_PLUGIN_USAGE.md](GRADLE_ANDROID_PLUGIN_USAGE.md)
- [GRADLE_ANDROID_PLUGIN_USAGE_ZH.md](GRADLE_ANDROID_PLUGIN_USAGE_ZH.md)
- [APK_OBFUSCATION_USAGE.md](APK_OBFUSCATION_USAGE.md)

## 预览

### 原始代码

![orig](image/orig.png)

### 混淆后代码

![obf1](image/obf1.png)
![obf2](image/obf2.png)

## License

本项目沿用原仓库 License。
