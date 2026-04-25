# BlackObfuscator Gradle Android Plugin 使用说明

## 插件信息

- 插件 ID：`zym.top.blackobfuscator`
- 适用对象：`com.android.application`

## 使用说明

下面的说明只面向插件使用者，默认插件产物已经能从你的 Maven 仓库中解析到。

### 1. 在项目中加入插件仓库

如果你的插件产物在本地 Maven，就加入 `mavenLocal()`；如果在私服，就改成对应私服地址。

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

### 2. 应用插件

Groovy DSL，`app/build.gradle`：

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator' version '1.0.0'
}
```

Kotlin DSL，`app/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application")
    id("zym.top.blackobfuscator") version "1.0.0"
}
```

### 3. 如果使用 `classpath` 方式

根 `build.gradle`：

```groovy
buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath "zym.top.blackobfuscator:blackobfuscator-gradle-plugin:1.0.0"
    }
}
```

然后在 `app/build.gradle` 中：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

### 4. 配置签名

插件最终会重新签名 APK，所以目标 variant 必须有完整的 `signingConfig`。

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

### 5. 配置 `blackObfuscator`

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

注意：

- `packageName` 和 `rulesFile` 必须二选一
- `variants = ["release"]` 表示只处理 release APK

### 6. 执行任务

```powershell
.\gradlew blackObfuscateRelease
```

如果希望每次 `assembleRelease` 后自动执行：

```groovy
blackObfuscator {
    autoRun = true
    variants = ["release"]
}
```

### 7. 输出文件

默认会在原 APK 同目录生成：

```text
app-release-blackobf.apk
```

## 要求

- Android SDK 可通过 `local.properties` 或 `ANDROID_SDK_ROOT` 找到
- `build-tools` 中需要存在 `zipalign` 和 `apksigner`
- 目标 variant 必须配置有效的 `signingConfig`
