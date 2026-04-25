# BlackObfuscator Gradle Android Plugin 使用说明

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 本地 Maven 坐标：`zym.top.blackobfuscator:blackobfuscator-gradle-plugin:2.1-SNAPSHOT`
- 适用对象：`com.android.application`

## 发布到本地 Maven

在仓库根目录执行：

```powershell
gradle :blackobfuscator-gradle-plugin:publishToMavenLocal
```

发布后会生成两类产物：

- 插件实现产物：用于 `classpath`
- 插件 marker 产物：用于 `plugins {}` DSL

## 推荐接入方式

推荐先发布到 `mavenLocal()`，再通过 `plugins {}` 方式应用。

### 1. 在 app 工程里加入 `mavenLocal()`

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

### 2. 在 app 模块中应用插件

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

## `buildscript classpath` 方式

如果你明确想走 `classpath` 方式，也可以这样配置。

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

然后在 `app/build.gradle` 中：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

## 示例配置

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

执行：

```powershell
.\gradlew blackObfuscateRelease
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

## 要求

- Android SDK 可通过 `local.properties` 或 `ANDROID_SDK_ROOT` 找到
- `build-tools` 中需要存在 `zipalign` 和 `apksigner`
- 目标 variant 必须配置有效的 `signingConfig`
- `packageName` 和 `rulesFile` 必须二选一
