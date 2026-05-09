# 技术设计文档 - 二次元聊天陪伴 + AR 召唤 App MVP

## 文档信息

- 文档版本：`v0.6`
- 文档状态：`In Progress`
- 更新时间：`2026-05-09`
- 关联 PRD：`PRD-二次元空间陪伴App-MVP.md`
- 技术阶段：`MVP`
- 客户端平台：`Android 原生`

## 1. 目标

本文档承接 `v0.4 PRD`，定义首版 MVP 的实现方案，目标是：

- 让工程可以直接按文档启动开发。
- 把 `聊天主闭环` 和 `AR 召唤增强功能` 的技术边界写清。
- 为后续资产接入、模型替换、后端切换和能力扩展预留接口。

## 2. 技术总览

### 2.1 客户端技术栈

- 语言：`Kotlin`
- UI：`Jetpack Compose`
- 架构：`单 Activity + 多 Screen + ViewModel 分层`
- 相机：`CameraX`
- AR：
  - 主路径：`EasyAR Sense Image Tracking / ImageTarget`
  - 增强能力：`EasyAR Sense Plane Detection`
  - 发行策略：`AR Optional`
- 3D 渲染：
  - 运行时模型格式：`glTF / GLB`
  - 渲染方案：`Filament` 或基于 Filament 的轻量封装
- 本地存储：
  - `Room`：后续阶段如需本地聊天摘要或更复杂缓存时再引入
  - `DataStore`：当前已用于会话持久化、最近作品回流引用与权限说明状态
- 网络：
  - 客户端：`Retrofit + OkHttp`
  - 后端：`Node + TypeScript + Express`

### 2.2 后端职责

MVP 后端最少承接以下职责，当前第一阶段最小服务已落地：

- 邮箱验证码登录与账号管理
- 聊天请求转发与会话归属
- 聊天摘要/记忆同步
- 最近作品元信息保存与删除
- 角色配置读取与更新

当前第一阶段后端已采用：

- `Node + TypeScript`
- `Express`
- `SQLite`
- 国内 Linux 主机部署
- `pm2` 常驻托管

### 2.3 非目标

- 本次技术设计不覆盖真实房间扫描与建模。
- 不覆盖多人协作、作品社区、复杂推荐系统。
- 不覆盖自定义角色资产上传。

## 3. 模块划分

### 3.1 聊天模块

职责：

- 提供消息流 UI
- 管理用户输入与角色回复
- 展示最近一次作品回流卡片
- 处理轻度主动陪伴消息

核心对象：

- `ChatMessage`
- `ChatSessionSummary`
- `CompanionMemory`
- `RecentCaptureReference`

### 3.2 召唤模块

职责：

- 检测设备 AR 能力
- 决定进入 `EasyAR 锚点图召唤`、`平面稳定` 或 `fallback`
- 管理召唤页状态机
- 加载与控制 3D 角色
- 生成截图

核心对象：

- `ArSessionState`
- `CompanionPlacement`
- `CaptureAsset`
- `MarkerAsset`
- `PlanePlacementState`

### 3.3 角色资产模块

职责：

- 管理首版 3D 角色资源
- 处理 `FBX -> GLB` 产物接入
- 管理首版待机动作资源

核心对象：

- `CharacterAssetManifest`
- `ModelLoadResult`
- `IdleAnimationState`

### 3.4 账号与数据模块

职责：

- 邮箱登录态维护
- 设置页中的隐私、权限与数据说明
- 最近作品回流元信息删除
- 基础记忆和角色资料同步

## 4. 角色资产链路

### 4.1 输入与运行时格式

- 当前首版资源输入源为 `E:\aic\feiyingfbx\未命名.fbx`
- 当前已有资源格式为 `FBX`
- 客户端运行时统一使用 `GLB`
- 不在 Android 端直接解析 `FBX`

### 4.2 预处理流程

- 角色资产在进入客户端前先完成离线转换：
  - `FBX -> GLB`
  - 材质与贴图检查
  - 只保留首版必需动作：`待机`
- 转换结果输出：
  - `character.glb`
  - 贴图资源
  - 资源描述文件 `CharacterAssetManifest`

### 4.3 资产要求

- 首版只支持 `1 个` 主角色资产包，输入源固定为 `E:\aic\feiyingfbx\未命名.fbx`
- 必须具备：
  - 可正常显示的模型
  - 至少一个稳定待机动作
  - 可接受的移动端面数与纹理大小

### 4.4 风险

- 若 `FBX -> GLB` 质量不稳定，召唤模块将无法进入开发联调阶段
- 角色待机动作缺失会直接影响 MVP 演示质量

## 5. LLM 接入方案

### 5.1 聊天能力边界

聊天是主闭环，因此 LLM 方案需要优先保证：

- 稳定返回
- 风格一致
- 有基础记忆
- 危机内容可被拦截

### 5.2 MVP 实现建议

- 服务端统一封装单一对话接口，不在客户端直连上游模型
- 客户端只感知：
  - 发送消息
  - 等待回复
  - 收到回复
  - 失败重试

当前第一阶段后端仍为：

- 开发验证码
- 真实 LLM 调用链路已接入
- OpenAI 兼容 `chat/completions` provider
- 当前服务器已完成 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / LLM_TIMEOUT_MS` 配置
- 已通过公网接口与 Android 实机验证真实模型回复可达

### 5.3 Prompt 结构

首版建议至少拆成三层：

- `system prompt`
  - 角色身份
  - 关系边界
  - 安全规则
- `memory context`
  - 最近聊天摘要
  - 最近召唤行为
  - 最近作品主题
- `turn messages`
  - 当前对话轮次

### 5.4 延迟与失败策略

- 产品验收线：`聊天首条回复时间 <= 4 秒`
- 超时策略：
  - 客户端展示等待态
  - 超时后给出重试入口
- 失败不应阻断用户继续浏览既有聊天记录
- 当前实现补充：
  - 模型失败、超时、空回复时，`/chat/send` 直接返回失败，不回退为伪成功文案
  - 模型成功后才写入用户消息与助手消息，避免失败重试产生脏历史
  - 服务器已完成最新源码重部署与重新编译，旧占位回复不再作为当前运行产物

## 6. 云端存储方案

### 6.1 MVP 推荐边界

云端至少存：

- 用户账号信息
- 角色资料
- 聊天记录或聊天摘要
- 最近作品回流元信息
- 最近召唤行为

当前实现修正：

- 当前阶段已落地 `Preferences DataStore`，仅持久化：
  - `sessionToken`
  - `email`
  - `profileCompleted`
  - `companionProfile`
  - `recentCaptureReference`
  - `arPrivacyAccepted`
- 当前阶段不做本地聊天历史缓存，不引入 `Room`
- 当前阶段数据库主存储仍为 `SQLite`，`PostgreSQL` 保持为下一阶段迁移目标

### 6.2 不建议首版云端化的内容

- 完整相机实时画面
- 系统相册中的截图文件本体
- 完整作品库

### 6.3 数据删除接口

至少提供：

- 删除账号
- 删除角色资料
- 删除聊天记录
- 清除最近作品回流元信息

## 7. 官方锚点图方案

### 7.1 交付方式

- 首版固定 `1 张官方锚点图`
- 在 App 内提供：
  - 查看
  - 保存
  - 使用说明

### 7.2 使用方式

- 支持打印
- 支持另一块屏幕显示
- 支持任意尺寸，但给出推荐尺寸作为标准测试口径

### 7.3 技术约束

- 锚点图必须适合 `EasyAR Image Tracking / ImageTarget`
- 设计需具有足够特征点和稳定对比度
- 验收由产品、设计、客户端共同完成

### 7.4 开发影响

- 没有正式锚点图产物，AR 主路径无法稳定联调
- 阶段二开发前必须完成锚点图交付

## 8. EasyAR Optional 与召唤实现

### 8.1 设备检测

- 首次进入召唤页时执行能力检测
- 逻辑分支：
  - `EasyAR 图像跟踪可用` -> 进入锚点图召唤
  - `锚点图召唤成功 + 平面识别可用` -> 启用平面稳定
  - `EasyAR 初始化失败 / 图像跟踪不可用` -> 直接 fallback
  - `相机权限不可用` -> 进入纯屏幕模式

### 8.2 AR 主路径

- 使用 `EasyAR Image Tracking / ImageTarget` 识别官方锚点图
- 识别成功后：
  - 自动在锚点图所在平面显示角色
  - 若平面识别可用，将角色稳定放置到检测到的真实平面
  - 允许用户再做相对位移、旋转、缩放、重置
- 识别成功时不强弹成功提示，尽量无感
- 超过阈值未识别成功则提示重试

### 8.3 平面稳定

- 平面识别只在锚点图召唤成功后作为增强能力启用
- 平面识别不作为独立主入口
- 平面识别仅服务当前会话内放置稳定
- 不保存房间地图
- 不识别家具或空间语义
- 平面识别不可用时，保持锚点图平面召唤，不阻断截图

### 8.4 丢锚处理

- 短时丢失时允许保持当前显示
- 超过 `3 秒` 未恢复时提示重新对准锚点图

### 8.5 Fallback 路径

- 第一层：相机预览叠加角色
- 第二层：纯屏幕摆放模式
- 聊天与截图在 fallback 中仍可用

## 9. 截图与作品回流

### 9.1 截图保存

- 首版只做截图
- 通过系统媒体存储保存到系统相册
- 不读取用户已有图片

### 9.2 最近作品回流

- App 不维护完整作品库
- 只记录：
  - 最近一次截图时间
  - 最近一次截图主题
  - 最近一次截图引用文案

### 9.3 删除语义

- App 内删除表示：
  - 清除最近作品回流卡片
  - 清除相关上下文和元信息
- 系统相册文件由用户在系统相册中自行删除

## 10. 本地数据模型建议

### 10.1 Room 表

- `chat_message`
- `chat_session_summary`
- `recent_capture_reference`
- `companion_profile`

### 10.2 DataStore

- 首次隐私告知是否已展示
- 首次相机权限说明是否已展示
- 最近一次进入召唤页模式

## 11. 状态机实现映射

### 11.1 召唤状态

- `Idle`
- `CheckingCapability`
- `InitializingEasyAR`
- `AwaitingPermission`
- `TrackingMarker`
- `MarkerTracked`
- `DetectingPlane`
- `PlaneStable`
- `FallbackCameraOverlay`
- `FallbackScreenOnly`
- `ModelLoadError`

### 11.2 聊天状态

- `Ready`
- `Sending`
- `AwaitingReply`
- `ReplyReceived`
- `ReplyTimeout`
- `ReplyFailed`

### 11.3 截图状态

- `NotPlaced`
- `Placed`
- `CaptureInProgress`
- `CaptureSaved`
- `CaptureFailed`

## 12. 异常处理

### 12.1 聊天异常

- LLM 超时
- 网络断开
- 服务端失败
- 安全规则拒答

### 12.2 AR 异常

- 不支持 AR
- EasyAR 初始化失败
- 图像跟踪不可用
- 锚点图识别失败
- 平面识别不可用
- 模型加载失败
- 切后台导致会话失效

### 12.3 截图异常

- 低存储
- 媒体写入失败
- 相机被占用

## 13. 测试计划

### 13.1 聊天主闭环

- 登录后首次聊天成功
- 最近作品回流后继续聊天成功
- 危机内容进入安全模式
- 未成年人自述后风格收紧

### 13.2 召唤主闭环

- 支持 EasyAR 图像跟踪的设备上识别锚点图并成功召唤
- 支持平面识别的设备上，锚点召唤成功后启用平面稳定
- EasyAR 初始化失败后 fallback 成功
- 不支持 EasyAR 的设备直接 fallback 成功

### 13.3 截图闭环

- 截图写入系统相册成功
- 最近作品回流生成成功
- 清除最近作品回流成功

## 14. 开发顺序建议

### 阶段一：聊天主闭环

- 登录
- 聊天页
- LLM 接口
- 基础记忆
- 安全模式

### 阶段二：AR 召唤增强

- 能力检测
- EasyAR SDK 初始化
- 锚点图识别
- 平面稳定
- 3D 角色加载
- fallback
- 截图

### 阶段三：治理与稳定性

- 设置页
- 权限说明
- 最近作品回流清除
- 性能指标与埋点

## 15. 当前已实现的后端接口

当前第一阶段后端已定义并实现：

- `/health`
- `/auth/request-code`
- `/auth/verify-code`
- `/me`
- `/me/companion-profile`
- `/chat/send`
- `/chat/history`
- `/me/recent-capture`
- `/me`

## 16. 当前客户端接入策略修正

当前已确定：

- 真机 Debug 联调先走 `http://111.231.14.253`
- Debug 版本允许 HTTP
- 公网入口当前已通过 `Nginx` 反代暴露为 `http://111.231.14.253`
- HTTPS 与域名后置
- Android 客户端下一阶段重点是替换本地 `MockAuthRepository / MockChatRepository`

## 17. 已知待确认项

- LLM 服务商最终选型
- 3D 角色最终渲染库封装方案
- 锚点图推荐尺寸数值
- 角色资源性能预算
- Alpha 目标发布日期
