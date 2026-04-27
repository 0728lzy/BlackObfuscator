# BlackObfuscator Gradle Android Plugin

## Plugin ID

`zym.top.blackobfuscator`

## Purpose

This plugin is for Android app developers who want to obfuscate dex files in the generated APK after `assemble`.

## Recommended usage

The documentation below assumes the plugin artifact is already available from your Maven repository setup.

### Add plugin repositories

`settings.gradle`

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

If your plugin artifact is hosted in a private Maven repository, replace `mavenLocal()` with that repository.

### Apply the plugin

`app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator' version '1.0.6'
}
```

### Alternative `classpath` usage

Root `build.gradle`:

```groovy
buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath "zym.top.blackobfuscator:blackobfuscator-gradle-plugin:1.0.6"
    }
}
```

Then in `app/build.gradle`:

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

### Example configuration

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
    // or rulesFile = file("blackobfuscator-rules.txt")
    variants = ["release"]
    outputSuffix = "-blackobf"
    deleteOriginalApk = false
    dptEnabled = true
    dptJar = file("tools/dpt.jar")
    dptExcludeAbi = "x86,x86_64"
}
```

### Run

```powershell
.\gradlew blackObfuscateRelease
```

## Requirements

- Android SDK available through `local.properties` or `ANDROID_SDK_ROOT`
- build-tools containing `zipalign` and `apksigner`
- Android variant must have a valid `signingConfig`
- configure exactly one of `packageName` or `rulesFile`

## Optional DPT shelling

If you also want a DPT shell step after dex obfuscation, enable it in the same block:

```groovy
blackObfuscator {
    packageName = "com.example.app"
    dptEnabled = true
    dptJar = file("tools/dpt.jar")
    dptExcludeAbi = "x86,x86_64"
    // optional:
    // dptRulesFile = file("tools/dpt-rules.txt")
    // dptProtectConfig = file("tools/dpt-protect.json")
    // javaExecutable = "C:/Program Files/Java/jdk-17/bin/java.exe"
    // dptJavaExecutable = "C:/Program Files/Java/jdk-17/bin/java.exe"
}
```

When `dptEnabled = true`, the task flow becomes:

1. build and locate the variant APK
2. obfuscate `classes*.dex`
3. repackage, `zipalign`, and sign the APK
4. run DPT shelling on the signed APK
5. `zipalign` and re-sign the DPT output
