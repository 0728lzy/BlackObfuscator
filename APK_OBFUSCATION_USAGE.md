# APK 混淆脚本使用说明

仓库中提供了一个 PowerShell 脚本：

- `obfuscate-apk.ps1`

它用于对已经构建完成的 APK 做离线混淆处理，适合 Windows 环境下直接对成品 APK 重新打包和重新签名。

## 脚本能力

脚本会自动完成以下步骤：

1. 从 APK 中提取 `classes*.dex`
2. 调用 `BlackObfuscatorCmd` 对命中的 dex 进行混淆
3. 将混淆后的 dex 回填到 APK
4. 执行 `zipalign`
5. 使用指定 keystore 重新签名
6. 校验 APK 对齐与签名结果

## 适用场景

适合以下场景：

- 已经有现成 APK，需要离线处理
- 暂时不想接入 Gradle 插件
- 希望用脚本快速验证不同的混淆规则和签名参数

## 环境要求

- Windows
- PowerShell 5+ 或 PowerShell 7+
- Java
- Android SDK
- Android SDK `build-tools`

脚本会优先查找这些工具：

- `aapt`
- `zipalign`
- `apksigner`
- `keytool`

如果 `dex-tools/build/install/dex-tools/lib` 不存在，脚本会尝试调用 Gradle 先构建 `dex-tools`。

## 常用示例

按包名混淆：

```powershell
powershell -ExecutionPolicy Bypass -File ".\obfuscate-apk.ps1" `
  -ApkPath "C:\path\app-release.apk" `
  -PackageName "com.example.app" `
  -KeystorePath "D:\key\release.keystore" `
  -KeyAlias "release" `
  -StorePassword "123456" `
  -KeyPassword "123456" `
  -Depth 1
```

按规则文件混淆：

```powershell
powershell -ExecutionPolicy Bypass -File ".\obfuscate-apk.ps1" `
  -ApkPath "C:\path\app-release.apk" `
  -RulesFile "C:\path\filter.txt" `
  -KeystorePath "D:\key\release.keystore" `
  -KeyAlias "release" `
  -StorePassword "123456" `
  -KeyPassword "123456" `
  -Depth 1
```

## 参数说明

| 参数 | 必填 | 说明 |
|---|---|---|
| `-ApkPath` | 是 | 待处理 APK 路径 |
| `-PackageName` | 否 | 要混淆的包名 |
| `-RulesFile` | 否 | 规则文件路径 |
| `-KeystorePath` | 是 | 签名 keystore 路径 |
| `-KeyAlias` | 是 | 签名别名 |
| `-StorePassword` | 是 | keystore 密码 |
| `-KeyPassword` | 否 | key 密码，默认跟 `StorePassword` 相同 |
| `-Depth` | 否 | 混淆深度，默认 `1` |
| `-OutputApkPath` | 否 | 输出 APK 路径 |
| `-WorkDirectory` | 否 | 临时工作目录 |
| `-AndroidSdkRoot` | 否 | 指定 Android SDK 路径 |
| `-BuildToolsVersion` | 否 | 指定 build-tools 版本 |
| `-GradleBat` | 否 | 指定 Gradle 可执行文件 |
| `-KeepWorkDirectory` | 否 | 是否保留中间文件 |

注意：

- `-PackageName` 和 `-RulesFile` 不能同时使用
- 如果两者都不传，脚本会尝试从 APK manifest 自动读取包名

## 输出结果

脚本成功执行后会输出：

- 最终 APK 路径
- APK 大小
- 签名 SHA-256
- 每个 dex 是否真的被混淆

典型输出会包含一张 `Dex summary` 表，例如：

```text
Dex          Obfuscated InputSize OutputSize   Delta
---          ---------- --------- ----------   -----
classes.dex       False   8451640    8451640       0
classes2.dex      False   8264152    8264152       0
classes3.dex       True   8069808   11961536 3891728
```

## 常见问题

### 1. 提示 `No classes found`

说明当前 dex 里没有匹配到你配置的包名或规则，不一定是脚本错误。

### 2. 混淆后 APK 体积明显增大

控制流混淆后 dex 变大是正常现象，但如果增长过大，通常说明：

- 混淆范围太大
- 命中了超大类
- 已经对第三方库或自动生成代码做了混淆

建议先缩小范围，并从 `Depth 1` 开始。

### 3. 安装失败

常见原因：

- 新 APK 的签名与手机中已安装版本不一致
- 混淆后命中了不适合处理的代码

如果签名不一致，通常需要先卸载旧版本。

## 建议用法

推荐按下面顺序验证：

1. 从 `Depth 1` 开始
2. 只混淆主业务包
3. 安装验证功能是否正常
4. 再逐步扩大混淆范围
