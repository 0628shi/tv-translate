# 剧译 · TV Translate（v0.1）

给**没有字幕**的本地剧集生成可看的中文字幕：抽音频 → 云端语音识别 → LLM 翻译 → 播放器里双语显示。

> 这是第一版（MVP）。核心链路已打通并编译出 APK，但本机没有安卓设备/模拟器，**未做真机运行验证**，首次使用请留意行为是否符合预期。

---

## 功能

- 选本地视频（SAF，不需要存储权限）
- **有字幕**：选 SRT/ASS，直接翻译，跳过语音识别（省钱、质量更好）
- **没字幕**：自动抽取音频 → 分块上传 ASR → 按上下文批量翻译 → 生成字幕
- 播放器：播放/暂停、拖动进度、双语/仅译文、字号调节、时间轴偏移、导出 SRT
- BYOK：ASR 和翻译各自配置 Base URL / 模型 / Key，只存在本机

## 安装

```powershell
adb install -r "E:\安卓\tv-translate\app\build\outputs\apk\debug\app-debug.apk"
```

- 最低 Android 8.0（API 26），目标 API 34
- 需要联网
- 首次启动后先点右上角「设置」填 Key

## 首次配置

App 里有两套独立的 OpenAI 兼容接口。

### 语音识别（ASR）

| 服务 | Base URL | 模型 |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `whisper-1` |
| 硅基流动 | `https://api.siliconflow.cn/v1` | `FunAudioLLM/SenseVoiceSmall` |
| 自建 faster-whisper 网关 | `http://192.168.x.x:8000/v1` | `large-v3` 等 |

要求：接口支持 `POST /audio/transcriptions`，返回 `verbose_json` 且带 `segments`（含 start/end）。没有时间戳的服务会退化成"按句均分"，同步会很差。

### 翻译（LLM）

| 服务 | Base URL | 模型 |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| 通义/Kimi/OpenAI 兼容网关 | 对应的 `/v1` | 对应模型 |

要求：支持 `POST /chat/completions`。翻译会带上下文（前 4 句）和术语表，批量 20 条。

> 只填翻译不填 ASR 也能用——前提是你能提供字幕文件。

## 使用

1. 「选择视频」挑一个本地文件
2. 有字幕就「选择字幕」，没有就留空
3. 选目标语言和原声语言（不确定就选「自动」）
4. 「开始生成字幕」，等进度跑完
5. 自动进入播放器，字幕会跟着画面出现

45 分钟一集、云端 ASR 的情况，大约 1–5 分钟处理完。

## 本地测试（不用装手机）

```powershell
.\run-tests.ps1
```

跑 11 项自检，全部在 JVM 上完成，不碰设备：SRT/ASS 解析、SRT 往返、时间格式化、重叠/重复修复、48k→16k 重采样、WAV 头、翻译 JSON 解析、提示词构造。

也可以不经过手机，直接用 SRT 验证真实翻译效果：

```powershell
.\run-tests.ps1 -TestArgs "translate --in ep01.srt --out ep01.zh.srt --base-url https://api.deepseek.com/v1 --api-key sk-xxx --model deepseek-chat --target 简体中文"
```

模块划分：

- `core/`：纯 Kotlin/JVM 逻辑（字幕解析、重采样、WAV、提示词、JSON 解析），可脱离 Android 运行
- `devtools/`：JVM 测试与命令行工具，`gradlew :devtools:run --args="selftest"`
- `app/`：Android UI、MediaCodec 抽音频、ASR 与播放

> 本机没有 JUnit 缓存，也没有模拟器镜像，所以测试用 Kotlin `main()` + 断言实现，退出码 0/1，不依赖测试框架。

## 处理流程

```
视频文件
  ├─ 有字幕文件 → 解析 SRT/ASS ─────────────┐
  └─ 没有字幕   → MediaCodec 解 16k 单声道 PCM │
                  → 每 10 分钟一个 WAV 分块    │
                  → POST /audio/transcriptions │
                  → 合并时间戳、去重、修重叠   │
                                               ▼
                              按 20 条一批调用 LLM（带上下文 + 术语表）
                                               ▼
                              字幕列表 → VideoView 叠加渲染 / 导出 SRT
```

关键代码：

- `pipeline/AudioExtractor.kt`：MediaExtractor + MediaCodec 解码、重采样、切块
- `pipeline/AsrClient.kt`：多段式 multipart 上传（边读边传，不占大内存）
- `pipeline/Translator.kt`：批量翻译、上下文、JSON 对齐、失败降级
- `pipeline/SubtitleParser.kt`：SRT / ASS 解析
- `ui/PlayerScreen.kt`：播放器 + 双语字幕覆盖层

## 已知限制

1. **未真机验证**：只验证了编译、打包和 APK 结构。真机首次运行如果闪退，用 `adb logcat` 抓堆栈。
2. **播放器用系统 MediaPlayer**：AC3/DTS 音轨、部分 HEVC 10bit、部分 MKV 可能放不了。建议 MP4 / H.264 / AAC；放不了会弹提示。
3. **ASR 必须返回 segments**：否则时间轴只能均分，字幕会漂。
4. **没有缓存/断点续传**：中断后要重跑整集。
5. **翻译失败不阻塞**：某批失败会保留原文，不会中断整集。
6. 只做了「译成中文 / English」两个目标；源语言支持自动/日/韩/英/中。
7. 处理期间需要保持 App 在前台（已加 keep-screen-on，但没有前台服务）。

## 构建

本机工具链在 `..\environment`（JDK 17 / Gradle 8.7 / SDK 34）。

```powershell
.\build-apk.ps1          # debug
.\build-apk.ps1 -Release # release（未签名）
```

两个环境相关的坑，已在脚本里处理：

- `environment` 目录对本会话只读，所以 Gradle user home 复制到了项目内 `.gradle-home`（约 1.4GB，可删，删了要重新复制）。
- 项目路径含中文，Kotlin daemon 会把手写路径编码坏，`gradle.properties` 里改成了 `kotlin.compiler.execution.strategy=in-process`。

## 目录

```
tv-translate/
├─ app/src/main/java/com/tvtranslate/app/
│  ├─ MainActivity.kt / AppViewModel.kt / Settings.kt
│  ├─ pipeline/  AudioExtractor, AsrClient, Translator, Pipeline, CueStore
│  └─ ui/        Home, Settings, Processing, Player, Theme
├─ core/         纯 JVM 逻辑（Cue、SubtitleParser、重采样、WAV、提示词、JSON）
├─ devtools/     本地测试与命令行工具
├─ docs/可行性分析.md
├─ run-tests.ps1
└─ build-apk.ps1
```

## 下一步

- 真机回归 + logcat 修一轮
- 检测内嵌字幕轨（MKV 自带字幕就不跑 ASR）
- 断点续传与结果缓存
- 端侧 ASR（sherpa-onnx）离线模式
- ASS 样式保留、字幕列表编辑/单句重译

## 隐私

API Key 存在本机 SharedPreferences，不随 APK 分发。音频分块和字幕文本会发送到你配置的服务商；视频画面不会上传。
