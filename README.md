# BlackObfuscator

![](https://img.shields.io/badge/language-java-brightgreen.svg)

BlackObfuscator 是一个面向 Android Dex/APK 的控制流混淆工具，基于 `dex2jar` 修改而来。它提供一个可接入 Android `application` 工程的 Gradle 插件，在 APK 生成后自动执行 dex 混淆、重打包、`zipalign` 和重新签名。

## 适用范围

- Android `application` 项目
- 需要对构建后的 APK 做 dex 混淆
- 已配置可用的签名信息

## 版本兼容

当前仓库已验证以下构建组合：

| Gradle | JDK | 状态 |
|---|---|---|
| `6.9.1` | `11` | 通过 |
| `8.7` | `17` | 通过 |

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 插件模式：APK 后处理，不是 AGP 编译期字节码插桩

插件执行流程：

1. 先执行 `assemble<Variant>`
2. 找到该变体输出的 APK
3. 提取 APK 中的 `classes*.dex`
4. 调用 `BlackObfuscatorCmd` 对命中的 dex 做混淆
5. 重新打包 APK
6. 执行 `zipalign`
7. 使用当前 variant 的 `signingConfig` 重新签名

## 接入步骤

下面的说明面向插件使用者，默认你已经能从自己的仓库体系中拿到插件产物。

### 1. 在项目中加入插件仓库

如果你的插件产物在本地 Maven，就把 `mavenLocal()` 加到仓库列表里。

Groovy DSL，`settings.gradle`：

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

Kotlin DSL，`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

如果你的插件在公司私服或其他 Maven 仓库，把 `mavenLocal()` 换成对应仓库地址即可。

### 2. 应用插件

推荐优先使用 `plugins {}`。

Groovy DSL，`app/build.gradle`：

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator' version '2.1-SNAPSHOT'
}
```

Kotlin DSL，`app/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application")
    id("zym.top.blackobfuscator") version "2.1-SNAPSHOT"
}
```

如果你项目仍然使用传统 `buildscript classpath` 方式，也可以这样写：

根 `build.gradle`：

```groovy
buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath "zym.top.blackobfuscator:blackobfuscator-gradle-plugin:2.1-SNAPSHOT"
    }
}
```

`app/build.gradle`：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

### 3. 配置 Android 签名

插件最终会重新签名 APK，所以目标 variant 必须有完整的 `signingConfig`。

Groovy DSL：

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

Kotlin DSL：

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

### 4. 配置 `blackObfuscator`

你必须二选一配置：

- `packageName`
- `rulesFile`

Groovy DSL：

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

Kotlin DSL：

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

### 5. 执行任务

如果你配置了：

```text
variants = ["release"]
```

那么会生成任务：

```bash
./gradlew blackObfuscateRelease
```

如果希望每次 `assembleRelease` 后自动执行，可以配置：

```groovy
blackObfuscator {
    autoRun = true
    variants = ["release"]
}
```

### 6. 查看输出 APK

默认会在原 APK 同目录生成：

```text
app-release-blackobf.apk
```

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

## 使用要求

- Android SDK 可通过 `local.properties` 或 `ANDROID_SDK_ROOT` 找到
- `build-tools` 中需要存在 `zipalign` 和 `apksigner`
- 目标 variant 必须配置有效的 `signingConfig`
- `packageName` 和 `rulesFile` 必须二选一

## 命令行方式

命令行入口：

- [dex-tools/src/main/java/com/googlecode/dex2jar/tools/BlackObfuscatorCmd.java](dex-tools/src/main/java/com/googlecode/dex2jar/tools/BlackObfuscatorCmd.java)

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

## 相关文档

- [GRADLE_ANDROID_PLUGIN_USAGE.md](GRADLE_ANDROID_PLUGIN_USAGE.md)
- [GRADLE_ANDROID_PLUGIN_USAGE_ZH.md](GRADLE_ANDROID_PLUGIN_USAGE_ZH.md)
- [APK_OBFUSCATION_USAGE.md](APK_OBFUSCATION_USAGE.md)

## License

本项目沿用原仓库 License。
