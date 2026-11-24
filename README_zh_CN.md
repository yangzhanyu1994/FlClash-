<div align="center">

<img src="assets/images/icon.png" alt="FlClash" width="88">

# FlClash

基于 ClashMeta 的多平台代理客户端，简单易用，开源无广告。

[English](README.md) · **简体中文**

[![Release](https://img.shields.io/github/v/release/chen08209/FlClash?style=flat-square&label=release)](https://github.com/chen08209/FlClash/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/chen08209/FlClash/total?style=flat-square&logo=github)](https://github.com/chen08209/FlClash/releases)
[![License](https://img.shields.io/github/license/chen08209/FlClash?style=flat-square)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/FlClash)

[官网](https://chen08209.github.io/FlClash/zh) · [下载](#下载) · [更新日志](CHANGELOG.md) · [从源码构建](#从源码构建)

</div>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="snapshots/preview-dark.png">
    <img alt="FlClash 在 MacBook 与手机上的仪表盘" src="snapshots/preview.png" width="92%">
  </picture>
</p>

## 必读

### 2026-11-24 · 本 fork 的 Android 签名发布修复

- 日期：2026-11-24（Asia/Singapore，UTC+8）
- 更新者：Grok Build / cloud implementation
- 内容：`v0.9.5` 的第三次打包在 `:app:packageRelease` 失败。密钥库能打开，但 PKCS12 的私钥只能用 store password 解密；CI 把另一份 key password 交给 Gradle，得到 `BadPaddingException`。fork 的签名步骤改为用 store password 解密同一张证书，不更换密钥。新签名不能覆盖上游 `chen08209/FlClash`。
- 测试：attempt 3 的「Require the existing release keystore」只说明四个 secret 非空。同一次 `:app:packageRelease` 读私钥失败，没有 APK，不是签名通过。本地 jarsigner 预检用同一张证书：store password 签名后 `jarsigner -verify` 为 jar verified（exit 0），证书 SHA256 仍是 `0A:CC:20:CC:37:43:82:8A:CD:E1:BF:02:07:8A:69:81:38:B6:45:AA:89:74:C6:67:CB:80:D7:DA:9C:85:13:A9`；key password 读不到私钥。`-strict` 只因自签证书报证书链错误。未真机验证。
- 发布：run `45341140984` 的 Package Android 已签出 APK。job `138273284408` 在 `flutter pub get` 退出 66，因为 checkout 没有拉取 `prism_ui`，没有重新编译。已用该次 `fork-android-dist` 发布稳定版 [v0.9.6](https://github.com/yangzhanyu1994/FlClash-/releases/tag/v0.9.6)。三份 APK 的包名都是 `com.follow.clash`，`versionName` 为 `0.9.6`。`apksigner` 证书 SHA-256 为 `0acc20cc3743828acde1bf02078a698138b645aa8974c667cb80d7da9c8513a9`。下载校验：arm64 `73f31df7837652fc1bd0b382305510cc1ee92a269ed451c79f4bca52107ae078`，armv7 `9d102dfdce2cda1a876f462e97e767ee262c4950d1e8ceeef222135ee8a23e66`，amd64 `2b751851a50d4a2ee0a714b7795994d8813e9ac49f61ec3348d92e05f1426b86`。未真机验证。
- 来源：attempt 3 日志 job `138256656546`；发布失败日志 job `138273284408`；本机用同一密钥库核对 `getKey`；对 CI 产物和发布页下载文件运行 `apksigner verify --print-certs` 与 `sha256sum`。

### 2026-11-24 · Android 桌面小组件（只读）

- 日期：2026-11-24（Asia/Singapore，UTC+8）
- 更新者：Grok Build / cloud implementation
- 内容：原生 Android 主屏幕小组件（AppWidget / RemoteViews），不是应用内卡片，也不是快捷设置磁贴。可拉伸。2×2 为运行状态和当前出站节点，4×2 增加模式、速率、IP 类型与等级，4×4 再增加本次会话流量、连接数、当前配置、TUN/系统代理、核心状态、检测时间与来源，4×1 为状态、节点和速率。点击打开应用，没有启动、停止或切换节点。
- 数据：运行状态来自现有核心状态；速率和本次流量来自 mihomo 的当前速率与自本次启动（停止时 `ResetStatistic`）累计，不是今日流量。IP 类型与等级复用 `IpQuality.level`（住宅/移动网络/商业为优，机房为普通，Tor 或滥用为风险；VPN/代理标记本身不是风险）。不显示公网 IP。
- 刷新：进程活着时，状态变化立即推送，已放置小组件且服务在运行时大约每 3 秒读一次本机速率。进程退出后不新开前台服务；约 20 秒未再推送则速率改为「—」，约 3 分钟改为「数据已过期」。系统 `updatePeriodMillis` 为 30 分钟，这是 Android 允许的下限。
- 测试：`flutter analyze --no-fatal-infos` 无问题（exit 0）。`flutter test test/features/home_widget_test.dart` 12 项通过（exit 0），覆盖状态映射、停止后清空速率、出口变化丢弃旧 IP 等级、会话流量文案、尺寸断点、发布去重、以及管理器不发出 START/STOP/TOGGLE。未真机验证。本环境没有 Samsung W25，也没有可用的 Android 模拟器截图。
- 来源：用户已确认的桌面设计（浅色/深色、信息规格、四种状态）；现有 `IpQuality` / `IpType` / `IpQualityLevel` 与 `lookupIpQuality`；Android App Widgets 文档（RemoteViews、`updatePeriodMillis` 最小 30 分钟、`onAppWidgetOptionsChanged`）。

## 功能

- **一个应用覆盖 Android、Windows、macOS 和 Linux**，桌面端同时提供 x64 与 ARM64 版本。
- **mihomo（Clash.Meta）内核**：规则分流、代理组、延迟测试、系统代理与 TUN 模式。
- **配置管理**：通过订阅链接或文件导入配置，内置编辑器，支持覆写脚本以及自定义规则、代理和代理组。
- **实时查看**连接、请求、DNS 查询和日志。
- **Material You 设计，融合 Apple 式交互**：支持动态取色、浅色与深色主题，连续曲率圆角，弹出面板可在几档高度间吸附，布局随屏幕从手机到桌面自适应。
- **备份与恢复**：通过 WebDAV 或本地文件。
- **平台细节**：Android 上有快捷设置磁贴、只读主屏幕小组件、分应用代理和 Android TV 支持；桌面端有托盘菜单和全局快捷键。
- **开源无广告**，以 GPL-3.0 协议发布。

## 下载

从 [GitHub Releases](https://github.com/chen08209/FlClash/releases/latest) 获取最新版本，或打开
[官网](https://chen08209.github.io/FlClash/zh#download)，它会自动为你的设备选好安装包。

| 平台 | 安装包 | 说明 |
| --- | --- | --- |
| Android | `arm64`（arm64-v8a）、`armv7`（armeabi-v7a）、`amd64`（x86_64）三种 APK | 绝大多数手机选 `arm64`。也可以通过下方的 F-Droid 仓库安装。 |
| Windows 10 及以上 | 安装版（`.exe`）或便携版（`.zip`），分 x64 与 ARM64 | 骁龙等 ARM 架构笔记本选 ARM64。 |
| macOS 12 及以上 | Apple Silicon 与 Intel 两种 DMG | 也可以通过 Homebrew 安装。 |
| Linux | `.deb`、`.rpm` 和 AppImage，分 x64 与 ARM64 | 托盘依赖见下方说明。 |

<p>
  <a href="https://chen08209.github.io/FlClash-fdroid-repo/repo?fingerprint=789D6D32668712EF7672F9E58DEEB15FBD6DCEEC5AE7A4371EA72F2AAE8A12FD"><img alt="Get it on F-Droid" src="snapshots/get-it-on-fdroid.svg" height="56"></a>
  <a href="https://github.com/chen08209/FlClash/releases/latest"><img alt="Get it on GitHub" src="snapshots/get-it-on-github.svg" height="56"></a>
</p>

**Homebrew**

```bash
brew tap chen08209/tap
brew install --cask flclash
```

**Linux 托盘图标**

托盘图标是 StatusNotifierItem。KDE Plasma、XFCE 和多数面板直接显示；GNOME 需要安装 AppIndicator and KStatusNotifierItem Support 扩展。

**无人值守的桌面环境**

FlClash 要在免责声明被接受后才会启动核心。在容器等无人点击的环境里，可在应用的环境变量中设置
`FLCLASH_ACCEPT_DISCLAIMER=1`，首次启动时即视为接受。

## 使用

**通过链接导入配置。** 打开下面格式的链接，即可把订阅导入 FlClash。`clashmeta://` 和 `flclash://` 开头的链接同样可用。

```text
clash://install-config?url=<经过 URL 编码的订阅链接>
```

**Android 自动化。** Tasker、MacroDroid 等应用可以用下面的 Action 启动 Activity，从而启动、停止或切换代理：

```text
com.follow.clash.action.START
com.follow.clash.action.STOP
com.follow.clash.action.TOGGLE
```

在电脑上也可以通过 adb 触发：

```bash
adb shell am start -a com.follow.clash.action.TOGGLE
```

## 从源码构建

需要 [Flutter](https://docs.flutter.dev/get-started/install) 3.50（正式版构建使用 3.50.0）、[Go](https://go.dev/dl/) 1.27，
以及通过 rustup 安装的 [Rust](https://rustup.rs/)。桌面端需要在对应系统上构建，Android 在任意系统上都能构建。

```bash
git clone --recursive https://github.com/chen08209/FlClash.git
cd FlClash
flutter pub get
dart run tool/package.dart android   # 或 windows、macos、linux
```

安装包输出到 `dist/`。Go 内核和 Rust 库会在 Flutter 构建过程中一并编译。

| 平台 | 额外依赖 |
| --- | --- |
| Android | 带 NDK 的 Android SDK。加上 `--arch arm64` 可以只构建单个 ABI。 |
| Windows | 编译内核用的 GCC（MinGW-w64），以及打包安装程序用的 [Inno Setup](https://jrsoftware.org/isinfo.php) 6。 |
| macOS | Xcode 与 Node.js。脚本会通过 npm 安装 `appdmg`。 |
| Linux | Debian 或 Ubuntu。脚本会用 apt 安装构建所需的软件包，并下载 `appimagetool`。 |

其余选项可以运行 `dart run tool/package.dart --help` 查看，例如用 `--targets` 只构建部分安装包格式，用 `--dry-run` 列出这次会生成的文件。

## 支持

给仓库点一个 Star 是支持项目最简单的方式。问题讨论和更新通知请关注 [Telegram 频道](https://t.me/FlClash)，
Bug 与功能建议请提交到 [GitHub Issues](https://github.com/chen08209/FlClash/issues)。

<a href="https://star-history.com/#chen08209/FlClash&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=chen08209/FlClash&type=Date&theme=dark">
    <img alt="Star history" src="https://api.star-history.com/svg?repos=chen08209/FlClash&type=Date" width="640">
  </picture>
</a>

## 许可证

FlClash 以 [GPL-3.0 协议](LICENSE)发布。
