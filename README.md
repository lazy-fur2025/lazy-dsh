<div align="center">

<img src="docs/screenshots/banner.webp" width="100%" alt="Lazy DSH：把 DeepSeek Harness 装进手机">

<br />

**Lazy DSH** · 手机上的 DeepSeek Harness Agent

不要 root，不用电脑。装一个 APK，手机上就有一个能读屏、点按、改设置、整理文件的 Agent。

在安卓手机上运行 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)（dsh），
让它操作 App、整理文件、修改系统设置，或者按条件自动执行任务。

[![Android](https://img.shields.io/badge/Android-10%2B-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-555?style=flat-square)
![Engine](https://img.shields.io/badge/engine-dsh%200.2.1--rc.5%20%C2%B7%20Node%2024-4D6BFE?style=flat-square)
![Version](https://img.shields.io/badge/version-0.3.8-4D6BFE?style=flat-square)
![Root](https://img.shields.io/badge/root-不需要-34A853?style=flat-square)
![License](https://img.shields.io/badge/license-Apache--2.0-blue?style=flat-square)

</div>

---

## 和 DSH-Android 的关系

项目从 [DSH-Android](https://github.com/jackwener/DSH-Android) 起步。后来包名、品牌、首页、设置、引导、启动流程和能力都做了重做，现在是一个独立项目，不再是可直接合并的分支。DSH-Android 的作者不对本项目的改动负责；原始项目的代码与文档仍是本项目的重要基础，见「致谢」。

## 重做了哪些地方

| 方向 | 内容 |
|---|---|
| **品牌** | 电荷猫（电荷模式）与叶绿兽（叶绿模式）两位角色、两套配色；新启动图标、通知图标、悬浮球三态（待机 / 干活 / 需要你）。素材全部从一张贴纸图切出，不重画 |
| **能耗模式** | 输入框上一键切换 电荷 / 叶绿：推理强度、技能目录、任务后自省、系统提示词、配色一起变；**模型始终不变** |
| **冷启动片头** | 2.4 s 逐帧渲染的两版片头（浅色 / 深色），系统 Splash 与首帧同色；只冷启动播一次，可点按跳过；开启「降低动态效果」时退化为静态 `QuietIntro` |
| **内置技能** | 20 个技能随 APK 安装到 `files/dsh-home/skills`，首次启动后出现在 设置 → 扩展 → 技能 |
| **Office 导出** | 把回复导出成真正的 `.docx` / `.pptx` / `.xlsx`；抽屉里的「文件」页负责打开、分享、删除 |
| **生图** | 设置 → 模型服务 → 生图，接任意 OpenAI 兼容 `/images/generations`；原图进相册 `Pictures/DSH` |
| **账号** | 移除 DeepSeek 账号登录，只保留 API Key（Keystore 加密存本机） |
| **更新** | 移除 App 内更新检查；换版本时自行下载覆盖安装 |
| **包名** | `io.github.lazy.dsh`，可与 DSH-Android 原版同时安装 |

<div align="center">

<img src="docs/screenshots/shot-home-charge.webp" width="31%" alt="电荷猫首页">
<img src="docs/screenshots/shot-home-leaf.webp" width="31%" alt="叶绿兽首页">
<img src="docs/screenshots/shot-modes.webp" width="31%" alt="能耗模式设置">

<sub>左：电荷猫 · 中：叶绿兽 · 右：两种模式的说明与用量取向</sub>

</div>

## 界面一览

<div align="center">

<img src="docs/screenshots/shot-drawer.webp" width="31%" alt="会话抽屉">
<img src="docs/screenshots/shot-settings.webp" width="31%" alt="设置">
<img src="docs/screenshots/shot-onboarding.webp" width="31%" alt="首次启动">

<sub>会话抽屉 · 设置 · 首次启动（只需要一个 API Key）</sub>

</div>

## 快速开始

1. 安装 APK：自己构建的产物在 `app/build/outputs/apk/debug/app-debug.apk`；如果发布页有包，也可以直接下载。
2. 首次启动填入 [DeepSeek API Key](https://platform.deepseek.com/api_keys)。Key 用 Android Keystore 加密后存本机；也可以在 设置 → 模型服务 里改用其他 OpenAI 兼容服务商。
3. 直接说要做什么，例如「列出下载目录里最大的 5 个文件」「把这条消息回一下」「明早 7:30 叫我」。
4. 用到某项权限时 App 会再引导开启；不需要一次给全。
5. 可选：在 设置 → 手机权限 里一键安装 Shizuku，解锁后台屏、系统开关等更多能力。

> 包名和签名都与 DSH-Android 原版不同：从原版迁移时不能直接覆盖安装，需要先卸载（会清数据）；同一签名的 Lazy DSH 版本之间可以覆盖升级。

## 能做什么

- **文件**：查找、整理、移动、重命名、压缩解压；能读 PDF / Word / Excel / PPT 里的文字。
- **屏幕和 App**：读屏、点击、滑动、输入（基于无障碍服务）；打开 App 或直接跳到指定页面。配好 Shizuku 后可在独立的后台屏操作（Android 11+），你继续用主屏。
- **系统设置**：亮度、音量、勿扰、闹钟、Wi‑Fi、蓝牙、定位；读取和回复通知，控制媒体播放。
- **生图**：填一个 OpenAI 兼容的生图接口，助手就能按描述画图；原图进相册 `Pictures/DSH`，对话里直接显示。
- **Office 导出**：回复下方「导出」→ Word / PPT / Excel，抽屉 → 文件 里统一管理。
- **自动化**：定时执行，或在连上 Wi‑Fi / 蓝牙、开始充电、到达某地、收到通知等条件下触发。
- **扩展**：技能、MCP 服务、第三方插件都能装；内置 20 个技能开箱可用。
- **多模型**：DeepSeek 之外，可添加 OpenAI、Claude、Gemini、Grok、OpenRouter、Kimi、智谱 GLM、通义千问、火山方舟、MiniMax、硅基流动、阶跃星辰、Ollama、LM Studio，或任意 OpenAI 兼容接口。
- **随时呼起**：悬浮球、快捷设置磁贴、分享菜单、系统助手都能把当前这一屏交给它。
- **完整的 dsh**：dsh 原样运行（没有 fork），技能、MCP、子 agent、网页搜索都能用。

## 能耗模式：电荷 / 叶绿

同一个模型，两种花法。开关在输入框控制行最前面，也可以在 设置 → 通用 → 能耗模式 里选。

| | 电荷 Electric | 叶绿 Leaf |
|---|---|---|
| 模型 | **不变** | **不变** |
| DeepSeek 推理强度 | High | Low |
| 提示词里的技能目录 | 保留 | 不发送 |
| 设备任务后的经验总结 | 开 | 关，不额外调用模型 |
| 回答取向 | 一次决定、批量调用工具、把事情办完 | 最短正确回答、复用上下文、跳过可选步骤 |
| 视觉 | 电荷猫：电蓝紫 + 闪电金，重字重无衬线字标 | 叶绿兽：叶绿，轻衬线字标 |

模型不参与切换：换个模式只改「花多少力气」，不偷偷改你选的模型和费用。实现细节见 [能耗模式](docs/ENERGY-MODES.md)。

## 冷启动片头

<div align="center">
<img src="docs/screenshots/shot-launch.webp" width="34%" alt="降低动态效果时的静态片头">
</div>

- 正片是两段 1080×2372 / 60 fps / 2.4 s 的 H.264 视频：`res/raw/splash.mp4`（浅色）与 `splash_dark.mp4`（深色）。
- 系统 Splash 的背景色与片头第一帧一致（浅色 `#D1E6C6`，深色 `#0B0C10`），冷启动不跳色。
- 片头是盖在已经组合好的首页之上的 overlay：引擎启动、首页布局都不等它，也不会被任何页面盖住。
- 只在冷启动播放；旋转、切主题、从最近任务回来都不重播；点一下可以立刻跳过。
- 开启系统「移除动画 / 降低动态效果」时播放静态 `QuietIntro`（上图），只留光环慢转和 900 ms 淡出。

时间轴、调色和重新渲染方法见 [冷启动片头](docs/LAUNCH-INTRO.md)；片头工程在仓库外的 `../GIF/dsh-splash/`。

## 架构

```mermaid
flowchart LR
  subgraph main[":main 进程（Kotlin · Compose）"]
    UI["对话 · 轨迹 · 文件<br/>唤起层 · 设置"]
    CAP["CapabilityHub<br/>设备工具"]
    TRG["TriggerHub<br/>自动化"]
    SVC["无障碍 · 通知监听<br/>系统助手 · 输入法"]
  end
  subgraph engine[":engine 进程（前台服务）"]
    NODE["libnode.so<br/>Node 24 · bionic"]
    DSH["dsh --profile android<br/>+ device / guard / mode 插件"]
  end
  SHZ["Shizuku（可选）"]
  API["模型 API"]

  UI <-- "dsh SDK JSON-RPC（stdio）" --> DSH
  DSH <-- "能力桥 JSON-RPC（socketpair）" --> CAP
  TRG --> DSH
  CAP --- SVC
  CAP -. shell 权限 .-> SHZ
  NODE --- DSH
  DSH -- HTTPS --> API
```

- **引擎嵌在 App 里**：Node 24 编译成安卓原生共享库，通过 JNI 在 `:engine` 前台服务里启动。不用 proot、不用 Termux。
- **dsh 不做 fork**：App 只通过 dsh 的 SDK 协议和它对话；安卓适配放在插件和少量构建期补丁里（见 [js/README.md](js/README.md)）。
- **手机能力在 App 侧**：设备工具全部由 Kotlin 实现，通过一条私有 socket 暴露给 dsh（见 [引擎契约](docs/ENGINE-CONTRACT.md)）。
- **数据在本机**：会话、附件、App 经验、导出的 Office 文件都在 App 私有目录；`allowBackup=false`，卸载即清除。

## 系统要求

- Android 10 及以上（minSdk 29），arm64-v8a。
- 调试包约 123 MB；建议预留 500 MB 可用空间。
- 需要能访问所选模型服务的 API；联网搜索由 DeepSeek 提供，用第三方模型时需要另配 DeepSeek API Key。

## 安装失败怎么办

- **「与已安装应用签名不一致」**：手机上装的是 DSH-Android 原版、旧版本或自己编译的版本。先卸载再装（会清除 App 数据）。
- **「解析失败 / 解析包出现问题」**：多半是没下完整，或经微信、QQ 转发后被改名（例如变成 `.apk.1`）。重新下载并核对 SHA-256。
- **「不兼容 / 与设备不兼容」**：只支持 Android 10 及以上的 arm64-v8a 手机；x86 模拟器装不上。
- **提示「已屏蔽有害应用」**：本 App 不在 Google Play 分发，又用到无障碍、通知读取等能力，可能被保护机制误判。核对安装包来源后，在弹窗里点「更多详情 → 仍要安装」；用 adb 安装时可以先把 `verifier_verify_adb_installs` 设为 0，装完再改回。

## 当前限制

- **锁屏后不能操作其他 App**：有安全锁屏时，系统不允许操作其他 App 的界面。这类任务会暂停并提示解锁；文件、设置、通知等系统 API 任务照常执行。
- **Shizuku 重启后要重新启动**：建议打开 Shizuku 的「开机启动（无线调试）」（Shizuku 13.6+、Android 11+）。
- **国产 ROM**：部分机型需要手动允许「后台弹出界面」和「自启动」，悬浮球和后台自动化才能稳定工作；App 的体检页会提示。
- **第三方模型**：只支持 OpenAI 兼容的 Chat Completions 接口（HTTPS）；上下文统一按 128K 计；推理强度只对引擎认得的模型家族列出支持的档位。工具调用效果取决于所选模型。
- **引擎冷启动**：目前约 5 秒，仍在优化；片头只负责把这段等待盖住。
- **片头只有冷启动**：MP4 没有 alpha 通道，背景必须是实色，所以是整屏 overlay，不能叠在任意界面上。
- **内置技能授权**：20 个技能里，7 个为项目作者原创，5 个在 frontmatter 声明 MIT，另外 8 个通用技能未声明许可证。公开分发前需要逐个确认上游许可证，或从 `assets/skills/` 移除；详见 [BUNDLED-SKILLS.md](docs/BUNDLED-SKILLS.md)。

## 开发者

<details>
<summary><b>从源码构建</b></summary>

需要 JDK 21、Android SDK（compileSdk 37.2）、NDK r29；引擎和原生库还需要 Node/npm、`patch`、`python3`。在 arm64 Linux 主机上构建时要额外传入 `-Pandroid.aapt2FromMavenOverride=<arm64 版 aapt2 的路径>`。

```bash
# 1. 原生库（libnode 等）：下载预编译产物并校验哈希，不用自己编 Node
native/prebuilt.sh fetch            # → toolchains/out/jniLibs/arm64-v8a/

# 2. 引擎包：dsh + 插件 + 补丁 → engine.zip
OUT_DIR=toolchains/out/engine js/build-engine.sh

# 3. APK
./gradlew :app:assembleDebug -Pdsh.engineOut="$PWD/toolchains/out/engine"
```

Windows 上引擎构建走 WSL，本工作区封装在 `F:\DSH\toolchains\build-engine.sh`：

```powershell
wsl bash /mnt/f/DSH/toolchains/build-engine.sh
.\gradlew.bat :app:assembleDebug -Pdsh.fetchShizuku=false
```

- 预编译产物的标签由 Node 版本加全部原生补丁的哈希生成（例如 `native-node24.22.1-caacecde44`）。只有升级 Node 或改动原生补丁时才需要重编：先运行 `native/build.sh`，再用 `native/prebuilt.sh publish` 发布。
- `toolchains/`、`local.properties`、构建日志都在 `.gitignore` 里，不参与提交。
- 主机端到端测试（真实调用 DeepSeek API）：`node js/test/host-e2e.mjs`，API Key 放在 `DSH_E2E_KEY_FILE` 指向的文件里。

</details>

<details>
<summary><b>测试与截图（不需要真机）</b></summary>

```powershell
# 单元测试 + Roborazzi 截图；截图输出到 app/build/screenshots/
.\gradlew.bat :app:testDebugUnitTest -I F:\DSH\toolchains\dev.init.gradle
```

当前基线：**431 个单元测试全部通过**；截图覆盖首页（电荷 / 叶绿 / 深色 / 浅色 / 窄屏）、抽屉、设置、引导、能耗模式页、QuietIntro、Markdown 表格。

`dev.init.gradle` 只是本机辅助：加阿里云 Maven 镜像，并把 Roborazzi 设成录制模式。在普通环境里去掉 `-I` 参数即可。

</details>

<details>
<summary><b>目录结构</b></summary>

```text
app/        Android App（Kotlin · Compose · Hilt · Room）
  core/designsystem/  设计令牌、品牌、字体、动效、玻璃效果
  core/office/        Word / PPT / Excel 写入（ZIP + OOXML，纯 Kotlin）
  core/skills/        内置技能播种
  core/engine/        引擎进程、JNI 启动、崩溃恢复
  core/capability/    设备工具（文件、屏幕、系统、通知、媒体……）
  core/trigger/       自动化规则和事件源
  feature/            对话、轨迹、文件、设置、片头、语音、扩展
  res/raw/            冷启动片头（splash.mp4 / splash_dark.mp4）
  assets/skills/      随 APK 安装的 20 个技能
js/         dsh 的 android profile、插件、补丁，build-engine.sh → engine.zip
native/     Node 24 安卓构建、JNI 包装、原生插件、预编译产物脚本
tools/      字符串、图标、README 首图等生成脚本
docs/       架构、引擎契约、设计 token、品牌、片头、Office 导出
```

</details>

<details>
<summary><b>翻译 / 添加界面语言</b></summary>

`app/src/main/res/values/` 是中文（默认），`values-en/` 是英文，`values-ru/` 是俄语。添加一种语言只需两步：

1. 新建 `app/src/main/res/values-<语言代码>/`，复制 `values-en/` 下的全部 `strings*.xml` 并翻译（lint 的 MissingTranslation 会报漏翻的条目）；
2. 在 `app/src/main/res/xml/locales_config.xml` 里加一行 `<locale android:name="<语言代码>" />`。

设置 → 通用 → 语言 的选项和 Android 13+ 系统设置里的「应用语言」都读这份列表，不用改代码；单元测试 `LocalesConfigTest` 会检查两者一致。引擎拿到的语言（`DSH_ANDROID_LOCALE`）跟随 App 语言。

</details>

<details>
<summary><b>设计文档</b></summary>

- [架构](docs/ARCHITECTURE.md)
- [引擎契约（App ↔ dsh）](docs/ENGINE-CONTRACT.md)
- [插件契约](docs/PLUGINS-CONTRACT.md)
- [设计 token](docs/design-tokens.md)
- [品牌素材](docs/BRAND-ASSETS.md)
- [能耗模式](docs/ENERGY-MODES.md)
- [冷启动片头](docs/LAUNCH-INTRO.md)
- [内置技能](docs/BUNDLED-SKILLS.md)
- [Office 导出](docs/OFFICE-EXPORT.md)
- [后台屏操作设计稿](docs/AGENT-SCREEN-V2.md)
- [工具结果卡片设计稿](docs/RESULT-CARDS.md)
- [发布流程](docs/RELEASE.md)

</details>

## 致谢

- [DSH-Android](https://github.com/jackwener/DSH-Android)：本项目的起点
- [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)：agent 引擎
- [Node.js](https://nodejs.org)；安卓构建参考了 [Termux](https://github.com/termux/termux-packages) 和 [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) 的做法
- [Shizuku](https://github.com/RikkaApps/Shizuku)（Apache-2.0）：App 内置了官方安装包
- [ripgrep](https://github.com/BurntSushi/ripgrep)
- [dsh-llm-service-lite](https://github.com/snnh/dsh-llm-service-lite)（MIT，snnh）：第三方模型适配器，原样内置于 `js/packages/llm-service-lite/`
- [magpie](https://github.com/yetone/magpie)（MIT）：服务商预设地址和接入 dsh 的经验

## 声明

本项目是社区项目，与 DeepSeek 官方无关联，也未获其背书。项目从 `jackwener/DSH-Android` 起步，之后做了大量重做，现独立维护；DSH-Android 的作者不对本项目的改动负责。「DeepSeek」及相关商标归其各自所有者所有。

## 许可证

[Apache License 2.0](LICENSE)。内置的第三方组件（Node.js、dsh、dsh-llm-service-lite、Shizuku、ripgrep 等）遵循各自的许可证。
