# 架构决策记录 006：采用 EasyAR Sense 作为 AR 主线

## 状态

`Accepted`

## 背景

THETWO 的召唤体验需要覆盖国内 Android 设备。Google ARCore 在认证设备上能力成熟，但会受到 Google Play Services for AR、设备认证和国内分发环境影响。当前产品更需要稳定完成“识别官方锚点图 -> 角色出现 -> 截图回流聊天”的闭环，并在此基础上逐步增强平面稳定。

EasyAR Sense 提供图像跟踪和平面识别能力，更适合作为当前国内 Android MVP 的 AR 主线。

## 决策

THETWO 下一阶段 AR 主线采用 EasyAR Sense。

能力分层固定为：

- `L0 纯屏召唤`：无相机权限也能摆放和截图。
- `L1 CameraX 相机叠加`：相机背景 + 手动摆放角色。
- `L2 EasyAR 锚点图召唤`：识别官方锚点图，角色出现在锚点图所在平面。
- `L3 EasyAR 平面稳定`：在 L2 召唤成功后，若设备支持平面识别，则把角色稳定放到检测到的真实平面上。

Google ARCore 不再作为下一阶段主路径。后续如果要重新引入 ARCore，需要新增 ADR。

## 后果

收益：

- 更适合国内 Android 分发环境。
- 官方锚点图召唤稳定性更高，便于 MVP 和 Alpha 验证。
- 平面识别可以作为锚点召唤后的增强，不阻塞主闭环。

代价：

- 需要评估 EasyAR 授权、包体、设备支持和 SDK 集成成本。
- 后续如果面向海外 Google 生态，可能需要重新评估 ARCore 增强模式。

## 当前适用范围

- 适用于 MVP 到 Alpha 阶段的 AR 召唤能力。
- 不改变 ADR-002：AR 仍必须 Optional。
- 不改变 ADR-005：MVP 仍不做房间理解、空间记忆、家具识别或房间扫描。
