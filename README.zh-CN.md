# ZymProGuard Obfuscator

[English](README.md)

ZymProGuard Obfuscator 是一个面向 Android `application` 工程的 Gradle 插件。它会在 `assemble` 之后对 `classes*.dex` 做混淆，重新打包 APK，执行 `zipalign`，重新签名，并且可以按需继续执行 DPT 加壳。

## 插件 ID

`io.github.0728lzy.zymproguardobfuscator`

## 使用前提

- Android `application` 模块
- 可用的 release `signingConfig`
- Android SDK 中存在 `zipalign` 和 `apksigner`
- 下列三种方式至少配置一种：
  `packageName`
  `rulesFile`
  `autoFilter = true`

## 接入方式

如果你是从 GitHub Packages 或其他 Maven 仓库拉取插件，先把仓库加到插件解析配置里。

`settings.gradle`

```groovy
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/0728lzy/BlackObfuscator")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

然后在 `app/build.gradle` 中应用插件：

```groovy
plugins {
    id 'com.android.application'
    id 'io.github.0728lzy.zymproguardobfuscator' version '1.0.7'
}
```

如果你使用 `buildscript` 方式：

```groovy
buildscript {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/0728lzy/BlackObfuscator")
            credentials {
                username = findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
        google()
        mavenCentral()
    }
    dependencies {
        classpath "io.github.0728lzy.zymproguardobfuscator:blackobfuscator-gradle-plugin:1.0.7"
    }
}

apply plugin: 'com.android.application'
apply plugin: 'io.github.0728lzy.zymproguardobfuscator'
```

## 配置方式

基础配置示例：

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
    autoRun = true
    depth = 1
    variants = ["release"]
    outputSuffix = "_bobf"
    deleteOriginalApk = true

    autoFilter = true
    // 或者 packageName = "com.example.app"
    // 或者 rulesFile = file("blackobfuscator-rules.txt")
}
```

如果还要开启 DPT 加壳：

```groovy
blackObfuscator {
    autoFilter = true
    dptEnabled = true
    dptJar = file("tools/dpt.jar")
    dptExcludeAbi = "x86,x86_64"

    // 可选
    // dptRulesFile = file("tools/dpt-rules.txt")
    // dptProtectConfig = file("tools/dpt-protect.json")
    // javaExecutable = "C:/Program Files/Java/jdk-17/bin/java.exe"
    // dptJavaExecutable = "C:/Program Files/Java/jdk-17/bin/java.exe"
}
```

DPT 额外注意：

- `dpt.jar` 很可能要求同级目录下还存在 `shell-files`
- DPT 开始之前，release APK 本身必须已经可以正常签名

## 运行方式

手动执行：

```powershell
.\gradlew blackObfuscateRelease
```

如果配置了 `autoRun = true`，那么在 `assembleRelease` 之后会自动执行。

## 输出结果

默认会在原始 APK 输出目录旁边生成一个带有 `outputSuffix` 后缀的新 APK。
