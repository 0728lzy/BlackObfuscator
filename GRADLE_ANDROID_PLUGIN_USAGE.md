# BlackObfuscator Gradle Android Plugin

## Plugin ID

`zym.top.blackobfuscator`

## Current behavior

- supports `com.android.application`
- runs after `assemble<Variant>` and rewrites the generated APK
- extracts `classes*.dex`, obfuscates matching dex files, repacks the APK, then runs `zipalign` and `apksigner`
- outputs a sibling APK with the suffix `-blackobf.apk` by default

## Recommended integration

The recommended way is to include this repository as a local included build from your Android app project.

### `settings.gradle`

```groovy
pluginManagement {
    includeBuild("../BlackObfuscator")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply the plugin in your app module:

```groovy
plugins {
    id 'com.android.application'
    id 'zym.top.blackobfuscator'
}
```

## Example consumer usage

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

Then run:

```powershell
.\gradlew blackObfuscateRelease
```

If `autoRun = true`, the plugin will attach itself to the selected variant's `assemble<Variant>` task.

## Requirements

- Android SDK available through `local.properties` or `ANDROID_SDK_ROOT`
- build-tools containing `zipalign` and `apksigner`
- Android variant must have a valid `signingConfig`
- configure exactly one of `packageName` or `rulesFile`
