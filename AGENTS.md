# repo 指南

## 项目概述

单模块 Android 应用（`com.mcn.fix`），将网易云音乐 NCM 文件转换为 flac/mp3/m4a/ogg/wav。

- **UI**: Miuix KMP 0.9.3 + JetBrains Compose Multiplatform 1.11.1。**严禁自建 UI**，所有界面一律使用 Miuix 提供的组件（`top.yukonga.miuix.kmp.*`）。
- **构建**: AGP 8.13.2, Kotlin 2.4.0, compileSdk=37, minSdk=33
- **结构**: `app/` 为唯一模块；入口 `MainActivity.kt` → `McnConverterTheme` → `MainPage`（两 Tab: Home + Settings）

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
│   │   └── model/
│   │       ├── NcmFileInfo.kt    #  文件信息数据类
│   │       └── NcmMetadata.kt    #  元数据（封面、歌词等）
│   ├── util/
│   │   └── FileUtils.kt          #  文件系统工具（SAF 遍历 NCM 等）
│   └── ui/
│       ├── theme/
│       │   ├── Theme.kt          #  Miuix 主题配置
│       │   └── StatusColors.kt   #  语义色 token
│       ├── navigation/
│       │   └── AppNavigation.kt  #  导航 + MainPage + HomeState
│       ├── screen/
│       │   ├── HomeScreen.kt     #  主页面（文件列表、解密按钮）
│       │   ├── SettingsScreen.kt #  设置页
│       │   └── AboutScreen.kt    #  关于页
│       └── component/
│           ├── AdaptiveTopAppBar.kt  #  宽屏自适应顶栏
│           ├── BlurredBar.kt         #  毛玻璃包裹
│           └── WideContentBox.kt     #  宽屏内容居中
├── res/                          # Android 资源（strings.xml 等）
└── AndroidManifest.xml
```

## 构建命令

- debug: `./gradlew assembleDebug`（≈30 MB，无优化）
- release: `./gradlew assembleRelease`（≈3 MB，R8 混淆 + 资源压缩）
- Release 使用 debug keystore 签名，可直接安装测试

## 已知陷阱

- **`gradle.properties` 非常规设置**：`android.suppressUnsupportedCompileSdk=37`、`android.disableAarMetadataCheck=true`、`android.overridePathCheck=true`。编译 SDK 37 是预览版，需这些标志抑制错误。
- **元数据覆盖**：解密输出所有格式都保留封面、标题、艺术家、专辑信息。MP3 写 ID3v2，FLAC 写 Vorbis Comment + 封面，M4A 写 iTunes ilst 原子。OGG/WAV 保持原始数据不变。
- **禁止擅自提交/推送**：没有用户明确要求，不得执行任何 `git commit`、`git push`、提交代码或同步远程仓库的操作。
- **扫描卡死**：`HomeScreen.kt` 中 `sourceDirLauncher` 回调先设 `isLoadingFiles = true` 再更新 `homeState.sourceDirUri`。选择相同目录时 `sourceDirUri` 值不变，`LaunchedEffect` 不重启，`isLoadingFiles` 永远卡在 `true`。已在 `HomeState` 中添加 `scanVersion` 计数器解决，每次选择目录递增，`LaunchedEffect` 双键依赖确保重扫。
- **删除源文件不生效**：`HomeScreen.kt:476` 删除逻辑中 `homeState.fileList.find { it.path == result.fileName }`，`info.path` 是完整 URI，`result.fileName` 只是裸文件名，永远不匹配。需用 `result.path`（完整 URI）比较。已在最新代码中修复。
- **全局变量命名用驼峰**：项目代码中 UI 状态用驼峰命名（如 `deleteAfterDecrypt`），但字符串 key 用下划线（如 `"delete_after_decrypt"`），这是 SharedPreferences 的惯例，不要混在代码变量名里。

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
