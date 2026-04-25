# BlackObfuscator 中文说明

BlackObfuscator 是一个面向 Android Dex/APK 的控制流混淆工具，基于 `dex2jar` 修改而来。它的核心能力是对命中的 `classes*.dex` 进行控制流混淆，从而提高反编译和静态分析的难度。

当前仓库已经包含两种使用方式：

- 命令行方式：直接调用 `BlackObfuscatorCmd`
- Gradle Android Plugin：对 Android `application` 工程生成的 APK 做自动混淆、重新打包和重新签名

## 仓库结构

- `dex-obfuscator`
  - 混淆核心逻辑
- `dex-tools`
  - 命令行入口，包含 `BlackObfuscatorCmd`
- `blackobfuscator-gradle-plugin`
  - 新增的 Gradle Android Plugin 模块
- `obfuscate-apk.ps1`
  - 面向 Windows/PowerShell 的 APK 混淆脚本

## 命令行用法

命令行入口位于：

- [BlackObfuscatorCmd.java](dex-tools/src/main/java/com/googlecode/dex2jar/tools/BlackObfuscatorCmd.java)

参数说明：

| 参数 | 说明 |
|---|---|
| `-d` | 混淆深度，建议先从 `1` 开始 |
| `-i` | 输入 dex 路径 |
| `-o` | 输出 dex 路径 |
| `-a` | 规则文件路径 |
| `-p` | 指定要混淆的包名 |

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

## 规则文件示例

```text
# package
com.example.app

# class
com.example.app.MainActivity

# blacklist
!com.example.app.generated
```

规则说明：

- 普通行表示允许混淆
- `!` 开头表示排除
- `#` 开头表示注释

## Gradle Android Plugin

当前仓库已新增 Android Gradle 插件模块，插件 ID 为：

```text
top.niunaijun.blackobfuscator
```

这个插件目前面向 `com.android.application`，工作流程如下：

1. 先执行 `assemble<Variant>`
2. 找到该变体输出的 APK
3. 提取 APK 中的 `classes*.dex`
4. 调用 `BlackObfuscatorCmd` 对命中的 dex 做混淆
5. 重新打包 APK
6. 自动执行 `zipalign`
7. 使用当前 variant 的 `signingConfig` 重新签名

详细配置说明见：

- [GRADLE_ANDROID_PLUGIN_USAGE_ZH.md](GRADLE_ANDROID_PLUGIN_USAGE_ZH.md)

## 构建插件

由于仓库当前缺少 `gradle-wrapper.jar`，不能直接依赖现有 `gradlew` 启动。建议在本机安装兼容版本的 Gradle，或补齐 wrapper 文件后执行：

```powershell
gradle :blackobfuscator-gradle-plugin:build
```

注意：

- 这套工程当前更适合在 JDK 11 下构建
- 在 JDK 17 + Gradle 6.9.1 的组合下，旧版 Groovy/AGP 可能出现兼容问题

## 相关文档

- [英文说明](README_EN.md)
- [Gradle Android Plugin 中文用法](GRADLE_ANDROID_PLUGIN_USAGE_ZH.md)
- [APK PowerShell 脚本说明](APK_OBFUSCATION_USAGE.md)

## 说明

- 本项目更适合混淆业务代码，不建议无差别混淆所有第三方库
- 建议优先从小范围、低深度开始验证
- 如果签名证书与已安装应用不一致，安装时可能需要先卸载旧版本

## License

本项目沿用原仓库 License。
