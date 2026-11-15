<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ktv_logo.png" width="132" alt="晓晓呀 Logo">
</p>

<h1 align="center">晓晓呀 · Xiao Xiao Ya</h1>

<p align="center"><b>晓晓呀 KTV</b> · 独立品牌与持续维护。</p>

<p align="center"><b>电视大屏当舞台，手机扫码当点歌台 —— 开机即唱的开源 KTV</b></p>

<p align="center">
  <a href="https://github.com/redhatlxg/xiaoxiao-ktv/releases"><img src="https://img.shields.io/github/v/tag/redhatlxg/xiaoxiao-ktv?label=release&amp;style=flat-square&amp;color=2f81f7&amp;sort=semver" alt="release"></a>
  <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Mobile-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="platform">
  <img src="https://img.shields.io/badge/Android-4.4%2B%20(API%2019)-3DDC84?style=flat-square" alt="minSdk">
  <img src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?style=flat-square&amp;logo=kotlin&amp;logoColor=white" alt="kotlin">
  <img src="https://img.shields.io/badge/Gradle-8.13-02303A?style=flat-square&amp;logo=gradle&amp;logoColor=white" alt="gradle">
  <img src="https://img.shields.io/badge/license-按授权协议使用-blue?style=flat-square" alt="license">
</p>

<p align="center">
  <a href="https://github.com/redhatlxg/xiaoxiao-ktv/stargazers"><img src="https://img.shields.io/github/stars/redhatlxg/xiaoxiao-ktv?style=flat-square&amp;logo=github&amp;label=stars" alt="stars"></a>
  <a href="https://github.com/redhatlxg/xiaoxiao-ktv/forks"><img src="https://img.shields.io/github/forks/redhatlxg/xiaoxiao-ktv?style=flat-square&amp;logo=github&amp;label=forks" alt="forks"></a>
  <a href="https://github.com/redhatlxg/xiaoxiao-ktv/issues"><img src="https://img.shields.io/github/issues/redhatlxg/xiaoxiao-ktv?style=flat-square&amp;color=e05d44" alt="issues"></a>
  <img src="https://img.shields.io/github/last-commit/redhatlxg/xiaoxiao-ktv?style=flat-square&amp;color=6f42c1" alt="last commit">
</p>

<p align="center"><i>遥控器点歌 · 手机扫码 · 原伴唱切换 · 边下边唱 · 开源无广告</i></p>

---

## 🎤 这是什么

**晓晓呀** 是一套跑在 Android TV / 电视盒子 / 横屏一体机上的**本地 KTV 点歌应用**：电视负责出画面与声音，手机扫一下屏幕上的二维码就变成点歌台 —— 不用机顶盒、不用点歌机、不用另买曲库硬盘。

- **电视端**：遥控器方向键 + 确认键走完全程，点歌 → 排队 → 下载 → 播放 → 切歌，全程不碰鼠标。
- **手机端**：同一局域网内打开内置网页，搜歌、点歌、置顶、切原伴唱、调均衡器，比遥控器打字快十倍。
- **曲库**：接官方 Muse 曲库同步，歌名、歌手、语种、分类、热门榜都是现成的，落地到本地 SQLite，断网也能翻。
- **播放**：IJK 内核优先硬解，边下边唱不卡顿，片头、原伴唱、音轨切换按 KTV 习惯做全。

一句话：**把「KTV 包厢」搬回客厅，只花一台电视的钱。**

> **项目维护说明**：本仓库由晓晓呀团队独立维护，功能、界面和版本以本仓库发布内容为准。

> **维护文档**：[v1.6.8 根版本基线](docs/1.6.8-ROOT-BASELINE.md) · [版本记录](docs/版本记录.md) · [构建与发布流程](docs/构建与发布流程.md)。项目当前根版本为 **v1.6.8 正式版**，后续开发均从该根基线创建独立分支；根版本只读保护，不覆盖、不直接开发。

## ✨ 为什么值得一试

| | |
|---|---|
| 🖥 **双端联动** | 电视出画面，手机点歌；已点 / 已唱 / 正在唱实时同步，多人抢麦不打架 |
| 🎮 **遥控器优先** | 焦点环、方向键导航、长按快捷操作全链路适配，老人小孩都会用 |
| ⏱ **边下边唱** | 已下载直接入队；未下载的后台下载，完成后自动接上队列，不打断当前这首歌 |
| 🎚 **原唱 / 伴唱** | 一键切音轨，切换时保留播放进度与暂停状态，不用从头再来 |
| 📚 **官方曲库** | Muse 增量同步 + 本地 SQLite，断网也能翻歌单、看收藏 |
| 🧩 **一份 APK 通吃** | 同时打包 `armeabi-v7a` 与 `arm64-v8a`，老盒子新电视都能装 |

## 🕹 功能总览

### 点歌与检索
- 首页聚合**热门排行、新歌、歌名、歌星、常唱、收藏、分类、本地歌曲**入口。
- 全网检索 + 本地过滤，支持语种筛选、分页浏览、首字母键盘快搜。
- 歌星页按头像标签分类，进歌手主页直接看 TA 的全部曲目。
- 支持**批量点歌、置顶、收藏 / 取消收藏**，已点列表可调整顺序。

### 播放与演唱
- IJK 播放器负责本地与下载歌曲，小窗与全屏无缝切换，翻页点歌不断流。
- 播放 / 暂停 / 切歌 / 重唱 / 进度拖动，全部遥控器可达。
- **原唱 ↔ 伴唱**实时切换，音轨状态跟随暂停恢复。
- 片头信息、播放提示、暂停常驻提示按大屏观看距离重新排版。
- 内置均衡器，以及画面比例、自动全屏、音画同步等偏好设置。

### 手机点歌（局域网）
- 电视端启动本地 HTTP 服务并生成二维码，手机扫码即用，**无需安装 App**。
- 手机端可搜歌、点歌、切歌、切原伴唱、调均衡器、查看已点队列。
- 与服务端同源，操作结果与电视端界面**实时互相同步**。

### 曲库、更新与运维
- **曲库**：官方 Muse 同步（RSA 登录 + 增量文件校验），不再依赖代码仓库托管数据库文件。
- **应用更新**：以 Release 标签作为版本号，发现更高版本时提示下载安装。
- **接口热更新**：歌曲接口逻辑独立在 `assets/mobile/ktv_api.js`，可不重新打包下发兼容版本。
- **远程日志**：异常可上报，便于定位线上设备问题（设置中可关闭）。

## 🧱 架构一览

```
┌──────────────────────── 电视端 (Android TV) ────────────────────────┐
│  MainActivity ── 焦点 / 导航 / 播放编排                              │
│      ├── SongLibrary · MuseDatabase ── 本地曲库 (SQLite)             │
│      ├── SongOkDownloadManager ── 下载队列 · 断点续传 · 边下边唱      │
│      ├── KtvVideoView (IJK) ── 解码 / 渲染 / 音轨切换                │
│      └── LocalRemoteServer ── 局域网 HTTP 服务 + 二维码              │
└──────────────┬──────────────────────────────────┬──────────────────┘
               │ Muse 曲库同步 (RSA)               │ 局域网扫码
        ┌──────▼────────┐                  ┌──────▼─────────┐
        │ 官方曲库服务   │                  │ 手机浏览器      │
        └───────────────┘                  │ (点歌台 / 遥控) │
               │                           └────────────────┘
               │ 取播放地址（签名 + token）
        ┌──────▼────────┐
        │ 歌曲源 / CDN   │──→ .ts 视频流 ──→ 下载 → 校验 → 播放
        └───────────────┘
```

## 📥 下载与安装

**方式一：装现成 APK**

到 [Releases](https://github.com/redhatlxg/xiaoxiao-ktv/releases) 下载最新 `.apk`，U 盘拷到电视安装，或用 adb：

```bash
adb connect 192.168.1.x:5555            # 电视需先开启「网络调试」
adb install -r xiaoxiao-ktv-release.apk
```

**方式二：自己编译源码** —— 见下一节。

> 首次启动需联网完成曲库初始化（视网络数十秒到数分钟）；之后断网也能浏览已同步的歌单。

## 🔧 从源码编译

### 环境要求

| 项目 | 要求 |
|---|---|
| JDK | OpenJDK **17** |
| Android SDK | `compileSdk 35` · `minSdk 19` · `targetSdk 25` |
| Gradle | 使用仓库自带 wrapper（**8.13**），无需单独安装 |
| 系统 | Linux / macOS / Windows 命令行均可 |

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
yes | sdkmanager --licenses > /dev/null
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

### 配置签名

Release 签名信息通过环境变量注入，**不要把密钥文件、密码或真实签名信息提交到 Git 仓库**。在构建环境中设置以下变量：

```bash
export KTV_STORE_FILE="/absolute/path/ktv_keystore.jks"
export KTV_STORE_PASSWORD="你的密钥库密码"
export KTV_KEY_ALIAS="你的别名"
export KTV_KEY_PASSWORD="你的密钥密码"
```

Windows PowerShell 可使用 `$env:KTV_STORE_FILE = "C:\\path\\ktv_keystore.jks"` 等方式设置。未配置这四项时，Release 仍可构建为未签名 APK；正式安装分发前必须使用自己的密钥签名。

生成新密钥时请自行妥善保存密码和备份文件：

```bash
keytool -genkeypair -v -keystore ktv_keystore.jks \
  -alias ktv -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 123456 -keypass 123456 \
  -dname "CN=XiaoxiaoYa KTV, OU=Software, O=XiaoxiaoYa, C=CN"
```

### 编译与安装

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # 按本机实际路径调整
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

产物为已签名 release APK，内含 `armeabi-v7a` 与 `arm64-v8a` 双 ABI。

## 📁 目录结构

```
xiaoxiao-ktv/
├── app/
│   ├── src/main/java/com/local/ktv/
│   │   ├── MainActivity.kt            # 主界面 / 播放 / 下载编排
│   │   ├── MuseDbSync.kt              # 官方曲库同步
│   │   ├── MuseDatabase.kt            # 曲库访问（SQLite）
│   │   ├── SongOkDownloadManager.kt   # 下载队列 · 断点续传
│   │   ├── LocalRemoteServer.kt       # 手机点歌 HTTP 服务
│   │   ├── KtvJsBridge.kt             # JS 桥（HTTP / RSA / 热更新）
│   │   └── player/                    # IJK 播放器封装 · 音轨切换
│   ├── src/main/assets/mobile/        # 手机点歌页（HTML / CSS / JS）
│   ├── src/main/jniLibs/              # IJK 双 ABI so
│   └── src/main/res/                  # 资源 / 证书
├── tools/                             # 辅助脚本
└── build.gradle / settings.gradle / gradlew
```

## ❓ 常见问题

| 现象 | 处理 |
|---|---|
| 曲库初始化失败 | 确认电视能访问 `mconn.cherryonline.cn`；设置页提供「重置数据库」 |
| 点歌提示 403 / 拿不到播放地址 | 多为歌曲源对陌生出口 IP 的试用窗口到期，重启光猫更换公网 IP 后恢复 |
| 热更新脚本不生效 | 远端 `ktv_api.js` 必须保留 `KTV_BRIDGE_API: 8` 标记，否则内置版本优先 |
| 安装报 `NO_MATCHING_ABIS` | 用的是裁剪过 ABI 的包；本项目已同时打包 v7a 与 arm64 |
| 想改签名 / 精简 ABI | 编辑 `app/build.gradle` 的 `signingConfigs` 与 `ndk.abiFilters` 后重编 |

## 📜 软件许可与版权声明

晓晓呀 KTV 的软件使用、复制、分发及商业授权，以软件发布方另行提供的许可协议或书面授权为准。未经许可，不得擅自复制、修改、再分发或用于未获授权的商业场景。

歌曲、MV、伴奏、歌词及曲库数据可能涉及独立的第三方版权。获得软件使用或商业授权，不代表自动获得音乐内容的播放、传播、点播或商业经营权；使用者须自行确认并取得所需授权。

本项目包含的第三方开源组件仍受其各自许可证约束，相关版权声明和许可证要求不会因本项目品牌调整而取消。

## 🙏 致谢

- [IJKPlayer](https://github.com/bilibili/ijkplayer) 播放内核
- 所有提交 Issue、反馈机型兼容问题的使用者

<p align="center"><sub>如果这个项目帮你在客厅唱上了歌，欢迎点个 ⭐ Star</sub></p>
