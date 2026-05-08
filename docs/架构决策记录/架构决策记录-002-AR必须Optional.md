# 架构决策记录 002：AR 必须 Optional

## 状态

`Accepted`

## 背景

Android 设备的 ARCore 支持情况不稳定。用户可能遇到设备不支持、Google Play Services for AR 缺失、安装取消、相机权限拒绝、AR 初始化失败、锚点识别失败等情况。

如果 AR 是唯一入口，大量用户会在召唤页被阻断，MVP 的“召唤 -> 截图 -> 回流聊天”闭环无法稳定验证。

## 决策

AR 必须是 Optional。支持 AR 的设备进入 ARCore 图像锚定路径；不支持 AR 或 AR 初始化失败时，必须进入 fallback 路径。

fallback 路径至少包括：

- CameraX 相机预览叠加。
- 无相机权限时的纯屏模式。
- 角色拖动、缩放、旋转。
- 截图保存。
- 最近作品回流到聊天。

## 后果

收益：

- 非 AR 设备仍可体验核心召唤闭环。
- ARCore 失败不会阻断 Alpha 测试。
- 设备矩阵可以分为 AR 组和 fallback 组分别验证。

代价：

- 需要维护 AR 与 fallback 两条路径。
- 埋点和失败矩阵必须区分 AR 漏斗与 fallback 漏斗。

## 当前适用范围

- 适用于所有 Android 召唤入口。
- 后续 AR 方案调整时，仍默认保留非 AR fallback，除非新 ADR 明确废弃。

