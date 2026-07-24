# NCM Converter for Android

将网易云音乐 NCM 文件转换为通用音频格式的 Android 应用。
在线自动或手动向音乐文件添加标签/封面/歌词

## 功能

- 解密 NCM 文件为 **flac / mp3 / m4a / ogg / wav**
- 批量解密，并发处理
- 手动或在线自动给音乐文件添加标签/封面/歌词等元素

## 构建

```bash
./gradlew assembleRelease
```

Release APK 位于 `app/build/outputs/apk/release/`，使用 debug keystore 签名，可直接安装。

## 技术栈

- **UI**: Miuix KMP 0.9.3 + JetBrains Compose Multiplatform 1.11.1
- **构建**: AGP 8.13.2, Kotlin 2.4.0, minSdk 33
- **大小**: Release ≈ 3 MB（R8 混淆 + 资源压缩）

## 许可证

MIT License
