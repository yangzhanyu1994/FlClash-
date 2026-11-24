<div align="center">

<img src="assets/images/icon.png" alt="FlClash" width="88">

# FlClash

A multi-platform proxy client based on ClashMeta. Simple to use, open source and ad-free.

**English** · [简体中文](README_zh_CN.md)

[![Release](https://img.shields.io/github/v/release/chen08209/FlClash?style=flat-square&label=release)](https://github.com/chen08209/FlClash/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/chen08209/FlClash/total?style=flat-square&logo=github)](https://github.com/chen08209/FlClash/releases)
[![License](https://img.shields.io/github/license/chen08209/FlClash?style=flat-square)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/FlClash)

[Website](https://chen08209.github.io/FlClash) · [Download](#download) · [Changelog](CHANGELOG.md) · [Build from source](#build-from-source)

</div>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="snapshots/preview-dark.png">
    <img alt="FlClash dashboard on a MacBook and a phone" src="snapshots/preview.png" width="92%">
  </picture>
</p>

## 必读

### 2026-11-24 · 本 fork 的 Android 签名发布修复

- 日期：2026-11-24（Asia/Singapore，UTC+8）
- 更新者：Grok Build / cloud implementation
- 内容：`v0.9.5` 的第三次打包在 `:app:packageRelease` 失败。密钥库能打开，但 PKCS12 的私钥只能用 store password 解密；CI 把另一份 key password 交给 Gradle，得到 `BadPaddingException`。fork 的签名步骤改为用 store password 解密同一张证书，不更换密钥。新签名不能覆盖上游 `chen08209/FlClash`。
- 测试：attempt 3 的「Require the existing release keystore」只说明四个 secret 非空。同一次 `:app:packageRelease` 读私钥失败，没有 APK，不是签名通过。本地 jarsigner 预检用同一张证书：store password 签名后 `jarsigner -verify` 为 jar verified（exit 0），证书 SHA256 仍是 `0A:CC:20:CC:37:43:82:8A:CD:E1:BF:02:07:8A:69:81:38:B6:45:AA:89:74:C6:67:CB:80:D7:DA:9C:85:13:A9`；key password 读不到私钥。`-strict` 只因自签证书报证书链错误。未真机验证。
- 发布：`v0.9.6` 的 Package Android 已签出 APK。随后的 GitHub Release 任务在 `flutter pub get` 失败，因为 checkout 没有拉取 `prism_ui`。该任务改为递归拉取子模块。未真机验证。
- 来源：attempt 3 日志 job `138256656546`；本机用同一密钥库核对 `getKey`（store password 成功，单独的 key password 失败）；Android Gradle `packageRelease`。

### 2026-11-24 · Android 桌面小组件（只读）

- 日期：2026-11-24（Asia/Singapore，UTC+8）
- 更新者：Grok Build / cloud implementation
- 内容：原生 Android 主屏幕小组件（AppWidget / RemoteViews），不是应用内卡片，也不是快捷设置磁贴。可拉伸。2×2 为运行状态和当前出站节点，4×2 增加模式、速率、IP 类型与等级，4×4 再增加本次会话流量、连接数、当前配置、TUN/系统代理、核心状态、检测时间与来源，4×1 为状态、节点和速率。点击打开应用，没有启动、停止或切换节点。
- 数据：运行状态来自现有核心状态；速率和本次流量来自 mihomo 的当前速率与自本次启动（停止时 `ResetStatistic`）累计，不是今日流量。IP 类型与等级复用 `IpQuality.level`（住宅/移动网络/商业为优，机房为普通，Tor 或滥用为风险；VPN/代理标记本身不是风险）。不显示公网 IP。
- 刷新：进程活着时，状态变化立即推送，已放置小组件且服务在运行时大约每 3 秒读一次本机速率。进程退出后不新开前台服务；约 20 秒未再推送则速率改为「—」，约 3 分钟改为「数据已过期」。系统 `updatePeriodMillis` 为 30 分钟，这是 Android 允许的下限。
- 测试：`flutter analyze --no-fatal-infos` 无问题（exit 0）。`flutter test test/features/home_widget_test.dart` 12 项通过（exit 0），覆盖状态映射、停止后清空速率、出口变化丢弃旧 IP 等级、会话流量文案、尺寸断点、发布去重、以及管理器不发出 START/STOP/TOGGLE。未真机验证。本环境没有 Samsung W25，也没有可用的 Android 模拟器截图。
- 来源：用户已确认的桌面设计（浅色/深色、信息规格、四种状态）；现有 `IpQuality` / `IpType` / `IpQualityLevel` 与 `lookupIpQuality`；Android App Widgets 文档（RemoteViews、`updatePeriodMillis` 最小 30 分钟、`onAppWidgetOptionsChanged`）。

## Features

- **One app for Android, Windows, macOS and Linux**, with x64 and ARM64 builds on the desktop.
- **mihomo (Clash.Meta) core** with rule routing, proxy groups, latency tests, system proxy and TUN mode.
- **Profiles** from a subscription link or a file, with a built-in editor, override scripts, and custom rules,
  proxies and proxy groups.
- **Live views** of connections, requests, DNS queries and logs.
- **Material You design with Apple-style interaction**: dynamic color, light and dark themes, continuous corners,
  sheets that snap between heights, and layouts that adapt from phones to desktops.
- **Backup and restore** through WebDAV or a local file.
- **Platform touches**: a Quick Settings tile, a read-only home-screen widget, per-app proxy and Android TV support on Android; a tray menu and
  global hotkeys on the desktop.
- **Open source and ad-free**, licensed under GPL-3.0.

## Download

Get the latest build from [GitHub Releases](https://github.com/chen08209/FlClash/releases/latest), or open the
[website](https://chen08209.github.io/FlClash#download), which picks the right file for your device.

| Platform | Packages | Notes |
| --- | --- | --- |
| Android | APK for `arm64` (arm64-v8a), `armv7` (armeabi-v7a) and `amd64` (x86_64) | Most phones use `arm64`. Also on the F-Droid repository below. |
| Windows 10 and later | Installer (`.exe`) or portable `.zip`, for x64 and ARM64 | Pick ARM64 on Snapdragon and other ARM laptops. |
| macOS 12 and later | DMG for Apple Silicon and Intel | Also on Homebrew. |
| Linux | `.deb`, `.rpm` and AppImage, for x64 and ARM64 | See the tray note below. |

<p>
  <a href="https://chen08209.github.io/FlClash-fdroid-repo/repo?fingerprint=789D6D32668712EF7672F9E58DEEB15FBD6DCEEC5AE7A4371EA72F2AAE8A12FD"><img alt="Get it on F-Droid" src="snapshots/get-it-on-fdroid.svg" height="56"></a>
  <a href="https://github.com/chen08209/FlClash/releases/latest"><img alt="Get it on GitHub" src="snapshots/get-it-on-github.svg" height="56"></a>
</p>

**Homebrew**

```bash
brew tap chen08209/tap
brew install --cask flclash
```

**Linux tray icon**

The tray icon is a StatusNotifierItem. KDE Plasma, XFCE and most panels show it as is; GNOME needs the
AppIndicator and KStatusNotifierItem Support extension.

**Unattended desktop**

FlClash starts the core only after the disclaimer is accepted. Where nobody can click it, such as a container, set
`FLCLASH_ACCEPT_DISCLAIMER=1` in the app's environment to accept it on first launch.

## Usage

**Import a profile from a link.** Opening a link in this form imports the subscription into FlClash. The `clashmeta://`
and `flclash://` schemes work the same way.

```text
clash://install-config?url=<URL-encoded subscription link>
```

**Automate on Android.** Tasker, MacroDroid and similar apps can start, stop or toggle the proxy by starting an
activity with one of these actions:

```text
com.follow.clash.action.START
com.follow.clash.action.STOP
com.follow.clash.action.TOGGLE
```

From a computer, the same works over adb:

```bash
adb shell am start -a com.follow.clash.action.TOGGLE
```

## Build from source

You need [Flutter](https://docs.flutter.dev/get-started/install) 3.50 (release builds use 3.50.0),
[Go](https://go.dev/dl/) 1.27 and [Rust](https://rustup.rs/) installed through rustup. Each platform builds on its own
host, except Android, which builds anywhere.

```bash
git clone --recursive https://github.com/chen08209/FlClash.git
cd FlClash
flutter pub get
dart run tool/package.dart android   # or windows, macos, linux
```

Packages land in `dist/`. The Go core and the Rust libraries are compiled as part of the Flutter build.

| Platform | Also needed |
| --- | --- |
| Android | Android SDK with the NDK. Add `--arch arm64` to build a single ABI. |
| Windows | GCC (MinGW-w64) for the core and [Inno Setup](https://jrsoftware.org/isinfo.php) 6 for the installer. |
| macOS | Xcode and Node.js. The script installs `appdmg` through npm. |
| Linux | Debian or Ubuntu. The script installs the build packages with apt and downloads `appimagetool`. |

Run `dart run tool/package.dart --help` for the remaining options, such as `--targets` to build only some package formats or `--dry-run` to list the files a run would write.

## Support

Starring the repository is the easiest way to support the project. Questions and announcements go to the
[Telegram channel](https://t.me/FlClash); bugs and feature requests go to
[GitHub Issues](https://github.com/chen08209/FlClash/issues).

<a href="https://star-history.com/#chen08209/FlClash&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=chen08209/FlClash&type=Date&theme=dark">
    <img alt="Star history" src="https://api.star-history.com/svg?repos=chen08209/FlClash&type=Date" width="640">
  </picture>
</a>

## License

FlClash is released under the [GPL-3.0 license](LICENSE).
