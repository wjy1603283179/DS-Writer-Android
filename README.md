# DS Writer

Android 12+ 的 OpenAI 兼容对话客户端。不绑定任何厂商,接口地址和模型名自己填。

[![Release](https://img.shields.io/github/v/release/wjy1603283179/DS-Writer-Android?label=release&color=2e7d5b)](https://github.com/wjy1603283179/DS-Writer-Android/releases/latest)
[![APK](https://img.shields.io/badge/APK-3.4%20MB-2e7d5b)](https://github.com/wjy1603283179/DS-Writer-Android/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

![对话](docs/screenshots/01-chat.png)

- **安装包 3.4 MB**,单模块 Kotlin,约一万行。
- **不添加任何额外提示词。** 永远不引入 `system` / `developer` 角色,不改写你打的字,没有你的操作不发请求。
- **唯一的上下文处理是压缩成前情提要,默认关闭。** 手动触发,结果落在一个可编辑的框里,你看过点了确认才会变成下一条真实消息。
- 公网走 HTTPS,内网允许明文 HTTP,可以连局域网里的本地模型。服务端只要 `--host 0.0.0.0` 并放行端口,不需要插件。

## 安装

从 [Releases](https://github.com/wjy1603283179/DS-Writer-Android/releases/latest) 下载 APK,Android 12 及以上。

用调试证书签名,所以会提示"来源未知"。

## 构建

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

需要 JDK 17、Android SDK 35、Build Tools 35.0.0。未签名 release:

```powershell
.\gradlew.bat assembleRelease -PdsWriterSigning=off
```

无应用内更新,升级请手动装。MIT,见 [LICENSE](LICENSE)。
