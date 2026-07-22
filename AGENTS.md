# repo 指南

**重要：未经我明确要求，不得执行提交（commit）、推送（push）、同步等任何版本控制操作。**

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
│           ├── GroupedCardItems.kt   #  卡片拆 lazy item
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
- **禁止升级 AGP**：AGP 8.13.2 已是对 compileSdk 37（预览版）支持最宽松的版本。升级到 8.14+ 可能使上述抑制标志失效，导致构建失败。等 compileSdk 37 成为稳定版或降回 36 后再考虑升级。
- **元数据覆盖**：解密输出所有格式都保留封面、标题、艺术家、专辑信息。MP3 写 ID3v2，FLAC 写 Vorbis Comment + 封面，M4A 写 iTunes ilst 原子。OGG/WAV 保持原始数据不变。

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
- 多行整卡（如 settings 大卡）考虑用 `groupedCardItems` 拆为独立 lazy item 提升滚动性能，靠 `CardSegment` 分角拼回视觉连续卡片
- `CardSegment` 首/末段有圆角用 `squircleSurface`（fill+clip，一个 offscreen layer），中间段无圆角用纯 `background`（无 layer）

### squircle 形状
自定义形状用 `top.yukonga.miuix.kmp.squircle.*`，不用 `clip(RoundedCornerShape(r))` / `background(shape=RoundedCornerShape(r))`。
- **非点击纯色背景**（徽章/占位块）→ `Modifier.squircleBackground(color, radius)`（无 offscreen layer，**不要 clip**）
- **图片/必须裁剪的内容** → `Modifier.squircleClip(radius)`（一个 offscreen layer）
- **可点击元素**（需涟漪裁到圆角内）→ `Modifier.squircleSurface(color, radius)` + `.clickable{}`（一个 offscreen layer）
- **3dp 小徽章**保持 `clip(RoundedCornerShape(3.dp))`，该尺寸下 squircle 无肉眼差异

### 其他
- Flow 收集：用 `collectAsStateWithLifecycle()`，不用 `collectAsState`
- 可复用组件必须暴露 `modifier: Modifier = Modifier` 作为第一可选参
- 颜色走 `MiuixTheme.colorScheme.*`，禁止散落 `Color(0xFF...)`

## 无关文件

- 根目录 `miuix-0.9.3/` 是调试时解压的 Miuix 源码 JAR，非构建所必需。
- 根目录 `ncm/` 才是实际项目根（含 `settings.gradle.kts`）。

## UI 规范参考

- 所有 UI 组件使用 miuix（Card、TopAppBar、NavigationBar、SmallTitle、TextButton 等）；miuix 组件（Card/Button/IconButton/TextField/NavigationBar/Dialog…）内部已用 squircle 渲染圆角，直接用即可，无需处理
- **自定义形状元素用 squircle modifier**：非 miuix 组件的手搓形状不要用 `.clip(RoundedCornerShape(r))` / `background(shape = RoundedCornerShape(r))`，改用 `top.yukonga.miuix.kmp.squircle.*`（随 `miuix-ui` 经 `api` 传递，无需单独加依赖；Android < API 33 / 无 runtime shader 自动回退 `RoundedCornerShape`）。按性能选路径：
  - **非点击纯色背景**（徽章 / 占位块，内容不溢出）→ `Modifier.squircleBackground(color, radius)`（无 offscreen layer，最省，**不要 clip**）
  - **图片 / 必须裁剪的内容**（如组图标 Image）→ `Modifier.squircleClip(radius)`（一个 offscreen layer）
  - **可点击元素**（需把涟漪裁到圆角内）→ `Modifier.squircleSurface(color, radius)` + `.clickable{}`（一个 offscreen layer 同时填充 + 裁剪；条件可点击时按 `isSelectable` 分支退化为 `squircleBackground`）
  - **3dp 小徽章保持 `clip(RoundedCornerShape(3.dp))`**：该尺寸下 squircle 与圆角肉眼无差异，且每元素多一个 GPU layer 不划算
- 返回按钮使用 MiuixIcons.Back
- 底栏图标：Sidebar / Tune / UploadCloud / Settings
- Badge：`clip(RoundedCornerShape(3.dp))` + 9.sp Bold Monospace
- 操作 IconButton：`minHeight/minWidth = 35.dp, backgroundColor = secondaryContainer`
- **页面骨架**：Scaffold + TopAppBar(scrollBehavior) + LazyColumn
  - LazyColumn 必须加 `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`
  - `contentPadding = PaddingValues(top = innerPadding.calculateTopPadding())`——仅设 top，不设 bottom
  - 首个 item 是 Card / 表单时用 `item { Spacer(Modifier.height(12.dp)) }` 顶部呼吸；SmallTitle 或 RestartRequiredHint 开头**不加**（SmallTitle 自带 InsideMargin 上边距，再加 Spacer 会比其他页多出一截）
  - 末尾 item 统一 `item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }` 吸收导航栏 + 留白
  - **二级页面（独立 NavDisplay entry）签名禁止 `bottomPadding: Dp` 参数**——靠 `Spacer(navigationBarsPadding())` 自适应即可
  - **4 个 Pager Tab 例外**（HomeScreen / ProxyScreen / SubscriptionScreen / SettingsScreen）：因外层 `MainPage` Scaffold 持有 `bottomBar`，必须接 `bottomPadding: Dp` 把 outer Scaffold 的 `innerPadding.calculateBottomPadding()` 透传给 LazyColumn `contentPadding`
- **顶栏 / 底栏毛玻璃**：所有页面 Scaffold 必须用 `BlurredBar` 包裹 `TopAppBar` / `NavigationBar`；MainPage 外层 Scaffold + 每个二级页面各自一份 backdrop（嵌套 layerBackdrop 是 OK 的，layer 抓取相互独立）。模式：
  - 顶层取 `val backdrop = rememberBlurBackdrop()` + `val blurActive = backdrop != null` + `val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface`
  - `topBar = { BlurredBar(backdrop, blurActive) { TopAppBar(... color = barColor ...) } }` / `bottomBar = { BlurredBar(backdrop, blurActive) { NavigationBar(color = barColor) {...} } }`
  - 内容区 LazyColumn modifier 链中追加 `.then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)` —— 让 backdrop 抓取内容 layer 给 TopAppBar/NavigationBar 的 textureBlur 用
  - 含搜索动画的页面（AppProxyScreen / ConnectionScreen）：在 BlurredBar 内套 `searchStatus.TopAppBarAnim(backgroundColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface) { TopAppBar(...) }`，让搜索切换时不挡住毛玻璃
- **宽屏适配**：窗口宽度 ≥ 600dp（`WideScreenMinWidth`，`rememberIsWideScreen()`，[WindowSize.kt](app/src/main/kotlin/top/yukonga/mishka/ui/util/WindowSize.kt)；用缩放前 `LocalPlatformDensity` 量宽，界面缩放不翻转外壳）时：
  - **导航**：[MainPage](app/src/main/kotlin/top/yukonga/mishka/ui/navigation/AppNavigation.kt) 把底部 `NavigationBar` 换成侧边 `NavigationRail`（**可展开收起**——传 `state = rememberNavigationRailState()`，默认收起，顶部有内置 Sidebar 展开钮，展开后 item 变 icon+label 横排 pill）；手机与宽屏共用同一 `pagerContent: (Modifier, Dp) -> Unit` lambda。Home Tab 图标用 `MiuixIcons.Home`（底栏与 rail 同步；不用 `Sidebar`，避免与 rail 展开钮的 Sidebar 图标撞脸）。inset：rail（`defaultWindowInsetsPadding=true`）吸收起始侧 cutout/navBar，内容区 `consumeWindowInsets(...Start)` + `windowInsetsPadding(systemBars∪displayCutout .only(End))` 处理末尾侧，底部经 `bottomPadding = navigationBars.asPaddingValues().calculateBottomPadding()` 透传给各 Tab
  - **顶栏**：所有用 `TopAppBar` 的页面（19 个）改走 [AdaptiveTopAppBar](app/src/main/kotlin/top/yukonga/mishka/ui/component/AdaptiveTopAppBar.kt)——宽屏用固定不折叠的 `SmallTopAppBar`（rail 取代底栏后纵向空间紧张）、手机用大标题 `TopAppBar`；参数面覆盖 title/color/scrollBehavior/navigationIcon/actions/bottomContent。**AboutScreen 例外**：其 hero 视差本就固定用 `SmallTopAppBar`，不套 Adaptive（否则手机端会多出与 hero 重复的大标题）。搜索页（AppProxy/Connection）保留 `searchStatus.TopAppBarAnim { AdaptiveTopAppBar(...) }` 包裹；其搜索框动态 top padding 在宽屏（固定 `SmallTopAppBar` 永不折叠、`collapsedFraction` 恒 0）时**恒为 0**（`if (isWideScreen) 0.dp else 12.dp * (1f - collapsedFraction)`），仅手机可折叠大标题栏才随折叠动态收缩
  - **内容居中**：4 个主 Tab 的 LazyColumn 用 `WideContentBox { sidePadding -> LazyColumn(...) }` 包裹（内部 `BoxWithConstraints` 按内容区实际宽度算出单侧留白 `sidePadding`）；**LazyColumn 保持全宽**（滚动手势覆盖整屏、两侧无死区），仅把 `sidePadding` 加进其 `contentPadding` 的 `start/end` 把内容居中到 `MaxContentWidth=800dp`（内容上限与 600dp 外壳阈值是两个独立常量）。**是否居中复用 `rememberIsWideScreen()` 判定**（外壳是唯一权威）——`WideContentBox` 自己在缩放后 `LocalDensity` 下量宽，若独立比较阈值，densityScale≠1 时会出现「手机外壳+内容内缩」或「rail 外壳+不居中」。二级页宽屏仍全宽；手机路径留白为 0、行为完全不变。**不要**改回压缩 LazyColumn 节点宽度的 layout modifier——那会让 600 外侧区域无法接收滚动手势（死区）
  - **横屏屏幕缺口**：miuix `Scaffold` 不自动 padding 内容、只经 `innerPadding` 提供 inset，二级页 `contentPadding` 又只吃 `top` → 横屏侧边刘海 / 挖孔 / 手势条下内容会压到缺口里。**每个二级页内容根 LazyColumn** 加 `Modifier.horizontalCutoutPadding()`（[WindowSize.kt](app/src/main/kotlin/top/yukonga/mishka/ui/util/WindowSize.kt)，只补水平 `displayCutout ∪ navigationBars` inset，竖屏 / 无侧边缺口为 0），紧跟在 `.fillMaxSize()` 后。顶栏由 `TopAppBar` 自身 `defaultWindowInsetsPadding=true` 处理，两者不重叠。**AboutScreen 相反**：其 `SmallTopAppBar(defaultWindowInsetsPadding=false)`（hero 视差不吃顶部 inset），内容侧已用 `Scaffold.contentWindowInsets.only(Horizontal)` + `calculateStart/EndPadding` 处理缺口，故只需给它的 `SmallTopAppBar` 加 `Modifier.horizontalCutoutPadding()` 补顶栏。4 个主 Tab 内容居中到 600dp、天然在缺口内侧，无需此项
- **Card 间距**：水平 12.dp，每项统一 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`；不使用 `Arrangement.spacedBy`
- **多组件卡片拆为独立 lazy item（滚动性能）**：`LazyColumn` 里禁止 `item { Card { rowA(); rowB(); rowC() } }` 这种"单 item 塞整卡多行"的反模式——它让整卡内容一次性组合，卡片高/行多时（settings 大卡、代理组数百节点展开）滚动/展开卡顿。改用 [GroupedCardItems](app/src/main/kotlin/top/yukonga/mishka/ui/component/GroupedCardItems.kt)：`groupedCardItems(keyPrefix, items = listOf(CardItem("k") { row() }, ...))` 把每行拆成独立 item，靠 `CardSegment` 分角拼回一张视觉连续的 miuix 风格卡片，LazyColumn 只组合可见段。**分角背景选路**：有圆角的首/末段用 `squircleSurface`（fill+clip，一个 offscreen layer）——必须 clip，否则段内 clickable 内容（preference 涟漪 / 组头点击）的方角涟漪会溢出圆角，与 miuix `Card` 用 squircleSurface 同因；中间段无圆角纯 `background`（无 offscreen layer，最省）。语义对齐 miuix `Card`（surfaceContainer 底 + onSurfaceContainer 内容色 + 16.dp 圆角 + insideMargin 默认 0，preference 自带内边距故段 `insidePadding=0`）。`outerBottomPadding` 按所替换 Card 的 `.padding(bottom=…)` 传（6/12/0）；条件行用 `buildList { if (…) add(CardItem…) }`。`groupedCardItems` **不加 item 动画**（保持原静态 Card 无动画行为、拆分对用户不可见的纯性能优化）；需要展开/收起动画的自行在 item 内用 `Modifier.animateItem(...)`。**settings 各屏幕、DnsQueryScreen 结果、ProxyScreen 节点网格均已按此重构**；ProxyScreen 额外把展开状态从 item 内 `rememberSaveable` 上提到屏幕级 `SnapshotStateList`（节点行是顶层 lazy item、随展开动态增删，存于 item 内会随 item 销毁丢失），组头段 + 每行 ≤2 节点段拼一张卡，排序/分行在 LazyColumn 内容 lambda 完成，组头段与节点行段都用 `Modifier.animateItem()`（默认 fade + placement spring）——展开时节点行淡入、下方各组平滑下滑，替代原 `AnimatedVisibility(expandVertically)`（节点多时一次性组合整组才是卡顿源，拆 lazy 后组合快、动画交 animateItem）。**placement spec 不能设 null**，否则下方各组硬跳、展开无动画感。组头段底角随展开在 `16.dp↔0.dp` 间 `animateDpAsState(tween(300))`（经 `CardSegment.bottomCornerRadius` 覆写 isFirst/isLast 推导值），与 chevron 旋转 / 节点行淡入同步——否则 `isLast` 随 `rows` 翻转会让组头底角瞬间圆↔方突变。**不适用**：纯静态文本卡（ExternalControl 提示卡、RootSettings 警告卡）与带视差 + `textureBlur` 的 AboutScreen——保持单 `item { Card }`（其内容 Column 用 `heightIn(min = lazyListState.layoutInfo.viewportSize.height.toDp())` 而非固定 `fillParentMaxHeight()`——后者把 Column 钉死为恰好一个视口高，横屏矮视口下超出一屏的卡片会被裁掉且无法滚动露出；`heightIn(min=…)` 保证「至少一屏」的同时允许内容更高时增长）
- **TextField 表单**：不包 Card，直接 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`
- **Edit Dialog 按钮顺序**：`not_modified | cancel | confirm`（三按钮 weight(1f) + `spacedBy(8.dp)`），confirm 用 `ButtonDefaults.textButtonColorsPrimary()`
- **长内容 Dialog 滚动 + 按钮固定底部**：miuix `WindowDialog` 在手机上对 content `Column` **不设 max-height**（`heightIn(max = Unspecified)`），内容过长会铺满屏幕把底部按钮顶出可视区。需把内容包进 `Column(Modifier.heightIn(max = 500.dp))` 限高，内部可滚动区用 `Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())`（`fill = false` 让短内容自然收缩、长内容才撑满到上限后滚动），按钮作为非加权子项放在滚动区之后固定在底部（同 KernelSU `ChooseKmiDialog` 范式）。范例：[MetaSettingsScreen](app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/MetaSettingsScreen.kt) 的 Age 密钥对结果 Dialog
- **用户反馈**：`platform.showToast(message, long = false)`——轻量操作结果提示
- **i18n**：所有用户字符串走 `stringResource(R.string.xxx)`（Composable 内）或 `context.getString(R.string.xxx)`（非 Composable，需 Context），禁止硬编码。用 Android 原生资源，`import top.yukonga.mishka.R`
  - 新增字符串同时加到 `app/src/main/res/values/strings.xml` + `res/values-zh-rCN/strings.xml`
  - key 命名：`{页面}_{描述}`，通用按钮 `common_` 前缀
  - 日志消息英文，代码注释中文
- **语义色 token**：状态色（运行中 / 等待 / 失败）、延迟色（优 / 可 / 差 / 未测）、按钮色（restart / stop / reload）、错误文案色，统一走 `top.yukonga.mishka.ui.theme.StatusColors`（`runState` / `delay` / `actionButton` / `danger` / `healthy` / `warning` / `neutral` / `selectedNodeContainer`）。**禁止在屏幕里散落 `Color(0xFF...)`**；仅 `MiuixTheme.colorScheme.*` 已有的 token 与 `StatusColors` 是合法颜色源
- **Flow 收集**：所有屏幕用 `androidx.lifecycle.compose.collectAsStateWithLifecycle()`（`lifecycle-runtime-compose`），不用 `androidx.compose.runtime.collectAsState`；后台时上游不再驱动 UI 重组
- **强跳过友好的状态形状**：UiState `data class` 必须 `@Immutable`；含跨节点变化的大集合字段（节点列表、连接列表、组列表）一律用 `kotlinx.collections.immutable.ImmutableList` / `ImmutableMap`（构造时 `.toPersistentList()` / `.toPersistentMap()`），避免 SSM 下重组每次都做结构性 `equals` 走 List/Map 全表
- **可复用组件 API**：`ui/component/*` 的可复用 composable 必须暴露 `modifier: Modifier = Modifier` 作为第一可选参，并应用到 root-most 节点；带表单 / 控件的 wrapper 透传到底层 miuix 组件（`ArrowPreference` / `OverlayDropdownPreference` / `Card` 等都接受 modifier）
