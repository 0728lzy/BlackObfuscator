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
    id 'zym.top.blackobfuscator' version '1.0.1'
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
        classpath "zym.top.blackobfuscator:blackobfuscator-gradle-plugin:1.0.1"
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
