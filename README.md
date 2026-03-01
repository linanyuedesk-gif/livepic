# VLive (com.cl.vlive)

Android 项目：将视频片段导出为 Live Photo 资产对（`JPG + MOV`）。

## 已实现功能

- 选择本地视频文件（系统文件选择器）
- 预览视频并通过双滑块选择片段
- 自动限制片段时长在 `1.5s ~ 3.0s`
- 导出静态图（JPG）+ 动态视频（MOV）
- 为静态图写入 `ContentIdentifier` 相关 EXIF/XMP 标记
- 导出后写入系统相册并支持一键分享

## 包名

- `applicationId`: `com.cl.vlive`

## 目录

- 图片导出到：`Pictures/VLive`
- 视频导出到：`Movies/VLive`

文件名统一形如：

- `IMG_20260302_120301.JPG`
- `IMG_20260302_120301.MOV`

## 构建

```bash
./gradlew assembleDebug
```

## iPhone / 照片 / 微信朋友圈使用建议

1. 将同名的 `JPG + MOV` 同时传到 iPhone（AirDrop、微信文件传输助手、iCloud Drive 均可）。
2. 确保两个文件基础名一致（如 `IMG_xxx`），再导入 iPhone「照片」。
3. 在「照片」中若识别为 Live Photo，可直接在微信朋友圈按“实况”发布。

## 说明

- Android 侧已完成片段裁剪与成对资产导出。
- 不同 iOS 版本/导入路径对 Live Photo 识别策略存在差异，建议优先使用 AirDrop 或 iCloud Drive 导入同名文件对进行验证。

