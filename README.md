# BlackObfuscator

![](https://img.shields.io/badge/language-java-brightgreen.svg)

BlackObfuscator 是一个面向 Android Dex/APK 的控制流混淆工具，基于 `dex2jar` 修改而来。当前仓库已经包含命令行工具和一个可发布到本地 Maven 的 Gradle Android 插件。

## 项目说明

- 本项目基于 [dex2jar](https://github.com/pxb1988/dex2jar) 修改
- 更适合混淆业务代码，不建议无差别混淆第三方库
- 插件适用于 `com.android.application`

## 版本兼容

当前仓库已验证以下构建组合：

| Gradle | JDK | 状态 |
|---|---|---|
| `6.9.1` | `11` | 通过 |
| `8.7` | `17` | 通过 |

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 本地 Maven 坐标：`zym.top.blackobfuscator:blackobfuscator-gradle-plugin:2.1-SNAPSHOT`
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

### 1. 先把插件发布到本地 Maven

在当前仓库根目录执行：

```powershell
gradle :blackobfuscator-gradle-plugin:publishToMavenLocal
```

发布完成后，可以通过以下坐标引用插件实现：

```text
zym.top.blackobfuscator:blackobfuscator-gradle-plugin:2.1-SNAPSHOT
```

同时 Gradle 也会生成插件 marker，这样你可以直接在 `plugins {}` 里按插件 ID 使用它。

### 2. 在 app 工程中加入 `mavenLocal()`

如果你想用 `plugins {}` 方式应用插件，需要先让项目在插件解析阶段可以访问本地 Maven。

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

### 3. 应用插件

推荐优先使用 `plugins {}` 方式。

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

### 4. 如果你想走 `classpath` 方式

也可以直接通过本地 Maven 坐标引入：

Groovy DSL，根 `build.gradle`：

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

然后在 `app/build.gradle`：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

### 5. 配置 Android 签名

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

### 6. 配置 `blackObfuscator`

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

### 7. 执行任务

如果你配置了：

```text
variants = ["release"]
```

那么会生成任务：

```bash
./gradlew blackObfuscateRelease
```

如果你希望每次 `assembleRelease` 后自动执行，可以配置：

```groovy
blackObfuscator {
    autoRun = true
    variants = ["release"]
}
```

### 8. 输出文件

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

约束：

- `packageName` 和 `rulesFile` 必须二选一
- 必须存在有效的 Android `signingConfig`
- 本机需要可用的 Android SDK 和 `build-tools`
- 插件当前适用于 `application`，不适用于 `library`

## 命令行使用

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

## 更多文档

- [GRADLE_ANDROID_PLUGIN_USAGE.md](GRADLE_ANDROID_PLUGIN_USAGE.md)
- [GRADLE_ANDROID_PLUGIN_USAGE_ZH.md](GRADLE_ANDROID_PLUGIN_USAGE_ZH.md)
- [APK_OBFUSCATION_USAGE.md](APK_OBFUSCATION_USAGE.md)

## License

本项目沿用原仓库 License。
