# 剧译 App · 开发流程文档

沿用「记账本（Ledger）」项目的开发流程：本地编译验证 → 真机安装测试 → git 提交 → 推送 GitHub → 云端 Actions 自动出包。

---

## 1. 项目概览

| 项 | 内容 |
| --- | --- |
| 应用名 | 剧译（TV Translate） |
| 包名 / applicationId | `com.tvtranslate.app` |
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose（1.6.8）+ Material 3 |
| 播放器 | 系统 `VideoView` / MediaPlayer |
| 处理链路 | MediaCodec 抽音频 → 云端 ASR → LLM 翻译 |
| 最低系统 | Android 8.0（API 26），targetSdk 34 |
| 远程仓库 | https://github.com/0628shi/tv-translate |

> 目录约定：源码与工具链分开。`tv-translate/` 是仓库根目录；`environment/` 是本机 JDK / Gradle / SDK，**不入库**。

---

## 2. 本地开发环境

### 2.1 工具链位置（本机）

| 工具 | 路径 | 版本 |
| --- | --- | --- |
| JDK | `E:\安卓\environment\.tools\jdk\jdk-17.0.20.1+1` | Temurin 17 |
| Gradle | `E:\安卓\environment\.tools\gradle\gradle-8.7` | 8.7 |
| Android SDK | `E:\安卓\environment\.tools\android-sdk` | platform 34 / build-tools 34.0.0 |

### 2.2 构建脚本

```powershell
.\build-apk.ps1           # debug
.\build-apk.ps1 -Release  # release（未签名）
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

### 2.3 本机两个特殊处理（不要删）

1. **`environment` 目录对本会话只读**，Gradle 无法写缓存，所以 `build-apk.ps1` 把 `GRADLE_USER_HOME` 指到项目内 `.gradle-home`（约 1.4GB，已 gitignore；删掉后重新从 `..\environment\.gradle-home` 复制即可）。
2. **项目路径含中文**，Kotlin daemon 会把路径编码坏（`E:\u5B89u5353\...`），所以 `gradle.properties` 设置了：
   ```properties
   kotlin.compiler.execution.strategy=in-process
   kotlin.incremental=false
   ```
   云端是 ASCII 路径，不受影响。

---

## 3. 日常开发流程

```
改代码 → .\build-apk.ps1 编译验证 → 真机安装测试 → git 提交 → push GitHub → Actions 自动构建
```

### 3.1 提交

```powershell
git add -A
git commit -m "feat: 说明改动"
git push origin main
```

提交信息沿用约定式前缀：`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`。

### 3.2 云端构建

仓库含 `.github/workflows/build.yml`，push 到 `main` 或手动触发后：

1. 打开 https://github.com/0628shi/tv-translate → **Actions** → **Build APK**
2. 等构建完成（首次约 2–4 分钟）
3. 进入对应 run → 底部 **Artifacts** → 下载 `tv-translate-debug-apk`
4. 解压得到 `app-debug.apk`

---

## 4. 代码结构

```
app/src/main/java/com/tvtranslate/app/
├── MainActivity.kt          # 入口 + 页面路由
├── AppViewModel.kt          # 状态机：Home / Settings / Processing / Player
├── Settings.kt              # BYOK 配置（SharedPreferences）
├── Utils.kt
├── model/Cue.kt             # 字幕条目
├── pipeline/
│   ├── AudioExtractor.kt    # MediaExtractor + MediaCodec → 16k 单声道 WAV 分块
│   ├── AsrClient.kt         # OpenAI 兼容 /audio/transcriptions
│   ├── Translator.kt        # 批量翻译 + 上下文 + 术语表
│   ├── SubtitleParser.kt    # SRT / ASS 解析
│   ├── Pipeline.kt          # 编排：字幕文件或 ASR → 翻译
│   └── CueStore.kt          # 缓存与 SRT 导出
└── ui/                      # Home / Settings / Processing / Player / Theme
```

**关键约定**：

- 音频统一重采样为 **16kHz 单声道 16bit PCM**，每 10 分钟一个 WAV（约 19MB，低于 Whisper 25MB 限制）。
- ASR 必须返回 `segments`（带 start/end）；缺失时按句估算时间轴，并在日志里告警。
- 翻译每批 20 条，带前 4 句上下文和术语表；某批失败保留原文，不中断整集。
- API Key 只存本机 `SharedPreferences`，**绝不入库**。

---

## 5. 常见问题

| 问题 | 处理 |
| --- | --- |
| `Could not initialize native services` | `environment` 不可写导致，`build-apk.ps1` 已改用项目内 `.gradle-home` |
| `kotlin...compiler...non-existent location: E:\u5B89u5353\...` | 中文路径 + Kotlin daemon，已改 `in-process` |
| `Your project path contains non-ASCII characters` | `gradle.properties` 里的 `android.overridePathCheck=true` 不要删 |
| 视频放不了 | 系统解码器不支持 AC3/DTS/部分 HEVC；换 MP4/H.264/AAC |
| 字幕不同步 | ASR 没返回时间戳；换 Whisper 类接口或自建 faster-whisper |
| push 被拒 `workflow scope` | Token 需要 `repo` + `workflow` 权限 |

---

## 6. 安全与维护

- **不要提交**：`local.properties`、`.gradle-home/`、`.tmp/`、`.android/`、`build/`、`*.apk`、`*.log`（均已在 `.gitignore`）。
- **Token 安全**：不要写进代码或提交历史；用完后在 GitHub Settings → Developer settings 里吊销。
- **发布正式版**：需在 `app/build.gradle.kts` 配 `signingConfigs`，再 `assembleRelease`。

---

## 7. 后续计划

- [ ] 真机回归 + logcat 修一轮
- [ ] 检测内嵌字幕轨（MKV 自带字幕就不跑 ASR）
- [ ] 断点续传与结果缓存
- [ ] 端侧 ASR（sherpa-onnx）离线模式
- [ ] 字幕列表编辑 / 单句重译
