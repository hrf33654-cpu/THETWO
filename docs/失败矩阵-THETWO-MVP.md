# 失败矩阵：THETWO MVP

## 文档信息

- 文档版本：`v0.1`
- 文档状态：`Draft`
- 更新时间：`2026-05-08`
- 适用范围：MVP 到 Alpha 前的失败恢复与降级策略

## 1. 目标

AI companion + AR 产品的失败路径很多。本文档用统一矩阵固化主链路的失败处理，避免后续真实 LLM、ARCore、截图和数据删除接入后出现不可预期行为。

表格字段说明：

- 功能：用户正在使用的功能。
- 失败场景：可能失败的具体情况。
- 检测方式：客户端或服务端如何识别失败。
- Fallback：系统应该怎么降级或恢复。
- 用户反馈：用户应该看到什么。
- 是否阻断 Alpha：是否必须在 Alpha 前解决。

## 2. 主链路失败矩阵

| 功能 | 失败场景 | 检测方式 | Fallback | 用户反馈 | 是否阻断 Alpha |
| --- | --- | --- | --- | --- | --- |
| 登录 | 邮箱格式错误 | 后端返回 `INVALID_EMAIL` | 留在登录页 | 提示输入有效邮箱 | 是 |
| 登录 | 验证码为空或错误 | 后端返回 `INVALID_CODE` | 留在登录页，可重试 | 提示验证码错误 | 是 |
| 登录 | 请求验证码网络失败 | Retrofit/网络异常 | 保留输入，可重新请求 | 提示稍后重试 | 是 |
| 会话 | token 缺失 | 客户端无 `authSession` | 回到登录页 | 提示登录态失效 | 是 |
| 会话 | token 无效 | 后端返回 `UNAUTHORIZED` 或 HTTP `401` | 清理认证态并重登 | 提示重新登录 | 是 |
| 创角 | 昵称或语气缺失 | 客户端校验或后端 `PROFILE_REQUIRED` | 留在创角页 | 提示补全角色资料 | 是 |
| 创角 | 保存失败 | 网络异常或后端错误 | 保留表单，可重试 | 提示保存失败 | 是 |
| 聊天 | 发送空消息 | 客户端禁用或后端 `CHAT_SEND_FAILED` | 不发送 | 无输入时按钮不可用或提示 | 是 |
| 聊天 | 发送网络失败 | Retrofit/网络异常 | 本地消息标记 `FAILED` | 显示发送失败和重试入口 | 是 |
| 聊天 | 历史加载失败 | `GET /chat/history` 失败 | 展示欢迎消息或已有本地状态 | 提示历史加载失败 | 否 |
| LLM | 上游 timeout | 后端超时或请求失败 | 返回失败给客户端，客户端可重试 | 提示回复失败，请重试 | 是 |
| LLM | 上游空回复 | 后端检测空内容 | 视为失败，不写助手消息 | 提示回复失败，请重试 | 是 |
| LLM | 上游安全拒答 | 后端转换为 `RESTRICTED` 或固定安全回复 | 保持聊天可继续 | 展示更保守回复 | 是 |
| 安全模式 | 未成年人自述 | 后端规则或安全服务返回 `RESTRICTED` | 收紧回复，不主动召唤邀约 | 显示受限模式说明 | 是 |
| 安全模式 | 危机内容 | 后端规则或安全服务返回 `RESTRICTED` | 固定安全引导，短时保守 | 提示联系现实可信对象或专业支持 | 是 |
| 召唤入口 | 未接受相机/AR 隐私说明 | 客户端状态 `arPrivacyAccepted=false` | 弹出说明，不进入保存流程 | 展示相机和截图说明 | 是 |
| 相机 fallback | 未授权相机 | Android 权限结果 denied | 进入纯屏 fallback | 提示已回退到纯屏模式 | 是 |
| 相机 fallback | 相机被占用或预览失败 | CameraX 初始化失败 | 进入纯屏 fallback | 提示相机不可用，可继续纯屏召唤 | 是 |
| 纯屏 fallback | 无相机权限且用户仍要召唤 | 权限 denied | 使用渐变背景生成截图 | 提示纯屏模式可用 | 是 |
| ARCore | 设备不支持 AR | AR 能力检测失败 | 进入 fallback | 提示设备不支持 AR，已进入降级模式 | 否 |
| ARCore | Google Play Services for AR 缺失 | 包检测或 ARCore 安装状态 | 提供安装入口，取消则 fallback | 提示安装或继续 fallback | 否 |
| ARCore | 用户取消安装 AR 服务 | 安装流程返回取消 | 进入 fallback | 提示已回退到普通召唤 | 否 |
| ARCore | 图像锚点识别失败 | 超时未识别 | 保持扫描或允许 fallback | 提示重新对准锚点图 | 否 |
| ARCore | 丢锚超过阈值 | AR tracking lost | 保持短时显示，超时提示重扫 | 提示重新对准锚点图 | 否 |
| 3D 角色 | `character.glb` 加载失败 | 加载器返回错误 | 显示失败页，允许 fallback | 提示角色加载失败，可重试 | 否 |
| 截图 | 低存储或写入失败 | `BitmapSaver` 返回失败 | 保留在召唤页，可重试 | 提示截图保存失败 | 是 |
| 截图 | 相机 bitmap 为空 | `PreviewView.bitmap=null` | 使用纯屏背景生成截图 | 正常保存或提示纯屏模式 | 否 |
| 作品回流 | 截图已保存但同步失败 | `PUT /me/recent-capture` 失败 | 保留 pending reference，可重试同步 | 提示截图已保存但回流失败 | 是 |
| 作品回流 | 最近作品不存在 | `GET /me/recent-capture` 返回 `CAPTURE_UPDATE_FAILED` | 不展示最近作品卡片 | 无需强提示 | 否 |
| 设置 | 清除最近作品失败 | `DELETE /me/recent-capture` 失败 | 保留当前引用，可重试 | 提示清除失败 | 是 |
| 设置 | 清空聊天失败 | `DELETE /chat/history` 失败 | 保留当前聊天，可重试 | 提示清空失败 | 是 |
| 设置 | 删除账号失败 | `DELETE /me` 失败 | 保留登录态，可重试 | 提示删除失败 | 是 |
| 后端 | 内部异常 | HTTP `500` + `INTERNAL_SERVER_ERROR` | 客户端按失败处理 | 提示稍后重试 | 是 |
| 网络 | 公网接口不可达 | 网络异常或超时 | 保留本地状态，不伪造成功 | 提示网络异常 | 是 |

## 3. Alpha 前必须稳定的失败路径

Alpha 前必须完成并手测：

- auth expired -> relogin。
- LLM timeout -> retry。
- chat send fail -> failed message + retry。
- AR unsupported -> fallback camera or screen-only。
- camera permission denied -> screen-only fallback。
- capture storage fail -> retry without returning to chat。
- recent capture sync fail -> pending retry。
- delete account fail -> stay signed in and show error。

## 4. 不允许的失败处理

- 不允许聊天发送失败后显示为成功。
- 不允许截图保存失败后生成作品回流。
- 不允许最近作品清除失败后本地先假装已清除。
- 不允许 AR 初始化失败阻断 fallback 召唤。
- 不允许客户端自行决定最终安全模式并绕过后端。
- 不允许 Release 包继续展示开发验证码。

## 5. 后续维护规则

- 每新增一个主功能，必须补一行失败矩阵。
- 每新增一个外部依赖，必须补 timeout、失败、降级和用户反馈。
- 每新增一个删除入口，必须写清删除范围和失败时 UI 状态。
- 每个 Alpha 阻断项必须能映射到一个测试用例或手测步骤。

