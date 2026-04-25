# BlackObfuscator Gradle Android Plugin

## Plugin ID

`top.niunaijun.blackobfuscator`

## Current behavior

- supports `com.android.application`
- runs after `assemble<Variant>` and rewrites the generated APK
- extracts `classes*.dex`, obfuscates matching dex files, repacks the APK, then runs `zipalign` and `apksigner`
- outputs a sibling APK with the suffix `-blackobf.apk` by default

## Add to this repository build

The plugin implementation lives in:

- `blackobfuscator-gradle-plugin`

Build it with:

```powershell
.\gradlew :blackobfuscator-gradle-plugin:build
```

## Example consumer usage

```groovy
plugins {
    id 'com.android.application'
    id 'top.niunaijun.blackobfuscator'
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
