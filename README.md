# NCM Converter for Android

将网易云音乐 NCM 格式文件转换为通用音频格式，保留封面、标题、艺术家、专辑等元数据。

## 功能

- 解码 NCM 文件，还原原始音频数据
- 支持输出格式：FLAC、MP3、M4A (AAC/ALAC)、OGG、WAV
- 自动保留封面图片和标签信息（ID3v2、Vorbis Comment、iTunes ilst）
- 批量解密，支持并发处理
- 基于 SAF（Storage Access Framework），无需存储权限

## 构建

```bash
./gradlew assembleRelease
```

Release APK 约 3 MB（R8 混淆 + 资源压缩），使用 debug keystore 签名，可直接安装。

## 技术栈

- UI: Miuix KMP 0.9.3 + JetBrains Compose Multiplatform 1.11.1
- 最低 API: 33（Android 13）
- 模块: 单模块，Kotlin 2.4.0

## 许可证

MIT
