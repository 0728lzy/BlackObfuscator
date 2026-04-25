# BlackObfuscator Gradle Android Plugin

## Plugin ID

`zym.top.blackobfuscator`

## Local Maven coordinates

`zym.top.blackobfuscator:blackobfuscator-gradle-plugin:2.1-SNAPSHOT`

## Publish to local Maven

Run this in the repository root:

```powershell
gradle :blackobfuscator-gradle-plugin:publishToMavenLocal
```

This publishes:

- the implementation artifact to `mavenLocal()`
- the plugin marker artifact for the `plugins {}` DSL

## Recommended integration

Use `mavenLocal()` and apply the plugin via the modern `plugins {}` DSL.

### `settings.gradle`

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

### `app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator' version '2.1-SNAPSHOT'
}
```

## Alternative `classpath` integration

Root `build.gradle`:

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

Then in `app/build.gradle`:

```groovy
apply plugin: 'com.android.application'
apply plugin: 'zym.top.blackobfuscator'
```

## Example configuration

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

Run:

```powershell
.\gradlew blackObfuscateRelease
```

## Requirements

- Android SDK available through `local.properties` or `ANDROID_SDK_ROOT`
- build-tools containing `zipalign` and `apksigner`
- Android variant must have a valid `signingConfig`
- configure exactly one of `packageName` or `rulesFile`
