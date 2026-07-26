# repo 指南

## 项目概述

单模块 Android 应用（`com.mcn.fix`），将网易云音乐 NCM 文件转换为 flac/mp3/m4a/ogg/wav。

- **UI**: Miuix KMP 0.9.3 + JetBrains Compose Multiplatform 1.11.1。**严禁自建 UI**，所有界面一律使用 Miuix 提供的组件（`top.yukonga.miuix.kmp.*`）。
- **构建**: AGP 8.13.2, Kotlin 2.4.0, compileSdk=37, minSdk=33
- **版本**: 1.1.2 (versionCode=5)
- **结构**: `app/` 为唯一模块；入口 `MainActivity.kt` → `McnConverterTheme` → `MainPage`（三 Tab: Home + Tags + Settings）

## 项目结构

```
ncm/
├── app/src/main/java/com/mcn/fix/
│   ├── MainActivity.kt          # Activity 入口
│   ├── McnApp.kt                 # Application 类
│   ├── crypto/                   # NCM 解密核心
│   │   ├── NcmCrypto.kt          #  解密逻辑（密钥推导、AES、RC4）
│   │   └── NcmStreamCipher.kt   #  RC4 流加密实现
│   ├── data/
│   │   ├── DecryptManager.kt     #  批量解密状态管理（Flow）
│   │   ├── model/
│   │   │   ├── NcmFileInfo.kt    #  文件信息数据类
│   │   │   └── NcmMetadata.kt    #  元数据（封面、歌词等）
│   │   └── tag/                  #  标签编辑器数据层
│   │       ├── AudioTagInfo.kt   #  标签数据类 + AudioFileEntry
│   │       ├── TagReaderWriter.kt#  读写音频标签（jaudiotagger + MediaMetadataRetriever）
│   │       └── TagSearchApi.kt   #  在线搜索（网易云 QQ 双源）+ 文件名解析
│   ├── util/
│   │   ├── FileUtils.kt          #  文件系统工具（SAF 遍历 NCM 等）
│   │   └── FairMemoryManager.kt  #  小米公平运行内存机制适配（监听 itgsa.intent.action.TRIM 广播、Binder 回调）
│   └── ui/
│       ├── theme/
│       │   ├── Theme.kt          #  Miuix 主题配置
│       │   └── StatusColors.kt   #  语义色 token
│       ├── navigation/
│       │   └── AppNavigation.kt  #  导航 + MainPage + HomeState（三 Tab）
│       ├── screen/
│       │   ├── HomeScreen.kt     #  主页面（文件列表、解密按钮）
│       │   ├── SettingsScreen.kt #  设置页（含标签设置区域）
│       │   └── AboutScreen.kt    #  关于页
│       ├── tag/                  #  标签编辑器 UI
│       │   └── TagScreen.kt      #  文件列表 + 编辑界面 + 搜索 + 自动/批量填充
│       ├── component/
│       │   ├── AdaptiveTopAppBar.kt  #  宽屏自适应顶栏
│       │   ├── BlurredBar.kt         #  毛玻璃包裹
│       │   ├── WideContentBox.kt     #  宽屏内容居中
│       │   ├── GroupedCardItems.kt   #  CardSegment 卡片分组容器
│       │   └── FloatingBottomBar.kt  #  悬浮药丸底部导航栏
│       ├── liquid/
│       │   ├── InnerShadow.kt        #  内阴影 Modifier
│       │   ├── LiquidGlass.kt        #  玻璃拟态特效（vibrancy/lens）
│       │   └── CombinedBackdrop.kt   #  复合 Backdrop 效果
│       └── interaction/
│           ├── DragGestureInspector.kt  #  自定义拖拽手势检测
│           └── DampedDragAnimation.kt   #  阻尼拖拽动画
├── res/                          # Android 资源（strings.xml 等）
└── AndroidManifest.xml
```

## 构建命令

- debug: `./gradlew assembleDebug`（≈30 MB，无优化）
- release: `./gradlew assembleRelease`（≈3 MB，R8 混淆 + 资源压缩）
- Release 使用 debug keystore 签名，可直接安装测试

## 已知陷阱

- **`gradle.properties` 非常规设置**：`android.suppressUnsupportedCompileSdk=37`、`android.disableAarMetadataCheck=true`、`android.overridePathCheck=true`。编译 SDK 37 是预览版，需这些标志抑制错误。
- **全局变量命名用驼峰**：项目代码中 UI 状态用驼峰命名（如 `deleteAfterDecrypt`），但字符串 key 用下划线（如 `"delete_after_decrypt"`），这是 SharedPreferences 的惯例，不要混在代码变量名里。
- **元数据覆盖**：解密输出所有格式都保留封面、标题、艺术家、专辑信息。MP3 写 ID3v2，FLAC 写 Vorbis Comment + 封面，M4A 写 iTunes ilst 原子。OGG/WAV 保持原始数据不变。
- **扫描卡死**：`HomeScreen.kt` 中 `sourceDirLauncher` 回调先设 `isLoadingFiles = true` 再更新 `homeState.sourceDirUri`。选择相同目录时 `sourceDirUri` 值不变，`LaunchedEffect` 不重启，`isLoadingFiles` 永远卡在 `true`。已在 `HomeState` 中添加 `scanVersion` 计数器解决，每次选择目录递增，`LaunchedEffect` 双键依赖确保重扫。
- **删除源文件不生效**：`HomeScreen.kt:476` 删除逻辑中 `homeState.fileList.find { it.path == result.fileName }`，`info.path` 是完整 URI，`result.fileName` 只是裸文件名，永远不匹配。需用 `result.path`（完整 URI）比较。已在最新代码中修复。
- **TagScreen 文件列表状态**：`tagAudioFiles` 和 `tagScanVersion` 提升到 `MainPage`（`AppNavigation.kt`）以跨越 Tab 切换保持。`remember` 而非 `rememberSaveable`，因为 `AudioFileEntry` 不可序列化。
- **TagScreen 封面缓存**：`coverCache = remember { LinkedHashMap<String, ByteArray?>(128, 0.75f, true) }` 在 `TagScreen` 级别创建，传递给 `FileCoverThumbnail` 避免列表滚动时每项重复创建 `MediaMetadataRetriever`。
- **元数据标识**：`TagPresenceInfo` 缓存通过 `mutableStateMapOf` 存放到 `TagScreen` 级别；`LaunchedEffect(audioFiles.toList(), scanVersion)` 在后台并发读取标签，使用 `TagReaderWriter.readTags()`（全标签读取，含歌词）。
- **MIUIX Checkbox**：`top.yukonga.miuix.kmp.basic.Checkbox` 使用 `state: ToggleableState`（而非 `checked: Boolean`）和 `onClick: (() -> Unit)?`（而非 `onCheckedChange`）。
- **`buildAnnotatedString` 不可用于 ArrowPreference.summary**：该参数类型为 `String?`，需用普通字符串拼接。
- **双源搜索**：`TagSearchApi.search()` 始终查询网易云和 QQ 音乐两个来源，结果去重。`Source` 枚举控制是否过滤来源。
- **`parseFileName` 分隔符**：支持 ` - `、` — `、` – `、`·`、`・`。
- **版本号不同步**：`res/values/strings.xml` 和 `res/values-zh-rCN/strings.xml` 中的 `version_value` 字符串需在版本更新时手工同步，否则关于页显示的版本号会与 `build.gradle.kts` 中的 `versionName` 不一致。

## UI 惯例

### LazyColumn 骨架
```
Column(Modifier.fillMaxSize()) {
    SmallTopAppBar(title = ...)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = 12.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item { CardSegment(isFirst=true, isLast=true) { SomePreference(...) } }
        item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
    }
}
```
- 首个 item 是 Card 时用 `Spacer(Modifier.height(12.dp))` 顶部呼吸；`SmallTitle` 开头的**不加**（自带上边距）
- 末尾 `Spacer(Modifier.height(24.dp).navigationBarsPadding())` 吸收导航栏
- `contentPadding` 仅设 `top`，不设 `bottom`（由末尾 Spacer 处理）

### Card 与 Preference
- Card 间距：水平 12.dp，每项 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`，不用 `Arrangement.spacedBy`
- TextField 表单不包 Card，直接 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`

### 其他
- Flow 收集：用 `collectAsStateWithLifecycle()`，不用 `collectAsState`
- 颜色走 `MiuixTheme.colorScheme.*`，禁止散落 `Color(0xFF...)`
- i18n：所有用户字符串走 `stringResource(R.string.xxx)`（Composable 内）或 `context.getString(R.string.xxx)`（非 Composable）。新增字符串同时加到 `res/values/strings.xml` + `res/values-zh-rCN/strings.xml`

## 无关文件

- 根目录 `miuix-0.9.3/` 是调试时解压的 Miuix 源码 JAR，非构建所必需。
- 根目录 `ncm/` 才是实际项目根（含 `settings.gradle.kts`）。
