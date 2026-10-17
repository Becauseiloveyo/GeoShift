# G03 Hook 诊断：证据格式、可信度和隔离

2026-10-17 · GeoShift v0.5.0-alpha · `feat/v0.5-privacy-reliability`

## 技术依据
- Modern libxposed 的 `XposedModule.getRemotePreferences(...)` 在被 Hook 应用中是**只读**的，不能把运行统计直接写到 GeoShift Profile SharedPreferences；参考官方 `JingMatrix/libxposed-api` 的 `XposedInterface.java` 注释。
- 不在定位热路径执行网络、同步磁盘或日志 IO。`HookAuditEmitter` 对同一 package/surface/outcome/identity 首次事件异步入队，通过模块日志输出固定字段，不序列化定位值。
- `GeoShiftAudit` 日志事件格式：
  `GS_AUDIT_V1 pkg=com.example.test surface=framework_last outcome=HIT identity=verified`
- 仅输出包名、接口类别、事件类别、身份归因标记。**不输出**纬经度、IP、Wi-Fi SSID/BSSID、蜂窝身份、时间序列、API key、任意调用参数或原始异常。
- 敏感性仍然存在：包名和日志时间可泄露应用使用信息。用户应仅导出需要的片段，并在分享之前手动检查原始日志。

## 观测状态的准确含义
- `INSTALLED`：当前进程中对应 Hook 注册操作曾报告成功；不意味着目标实际调用过该接口。
- `HIT`：Hook 曾拦截调用，且事件编码为 `identity=verified`；不意味着实际替换过数据。
- `REWRITTEN`：Hook 代码分支曾返回不同引用或模拟值；不等于测试程序确实接收期望结果，也不意味着无其他 API 泄露。
- `FAILED`：Hook 安装或回调包装报告失败；报告聚合时优先显示失败，避免被其他成功事件覆盖。
- `NOT_COVERED`：不存在适配目标或安装失败时报告未覆盖；若未获得任何日志仍显示 `NOT_VERIFIED`。
- `NOT_VERIFIED`：无匹配的有效事件，或者只有 `identity=unknown` 的 HIT/REWRITTEN。不能推测成功。
- 系统安装事件使用 `pkg=io.geoshift.system`；只有通过 Binder UID 和包名归因的 system_server 目标拦截事件，才使用目标包名与 `identity=verified`。身份不明确、共享 UID 同时出现多个 Profile 包名时不做归因。
- App 侧记录 `location_getter`、`framework_last` 与部分 `framework_callback`；系统侧记录 `system_last`、`system_callback`。其余模块当前暂时无审计事件，必须显示为 `NOT_VERIFIED`，不能标记为保护通过。

## 使用方法
1. 在授权测试设备中启用 GeoShift 正常单应用 Profile；不要默认启用全局 Strict 模式。
2. 通过自控 Probe 应用分别调用位置 API；从 LSPosed 模块日志或 `adb logcat` 导出**必要片段**为纯文本文件。日志过滤标签：`GeoShiftAudit`，标记：`GS_AUDIT_V1`。
3. 进入 GeoShift 的 **Diagnostics** 页面，选择 **Import Xposed / logcat text**，导入纯文本（最大 1,000,000 个字符，最多保留 2,000 条去重事件）。
4. 对比 Profile 的分类状态；“已改写”只是日志证据。用 Probe 实际返回值和其他应用对照检查真实效果（G08/G09）。

## 身份与隐私边界
- 日志导入内容不可信、可人工构造或修改；解析只限固定字段和已知接口，显示为**历史证据**，不会认证来源或真实性。
- imported events 会保留在 GeoShift 当前页面的内存中，应用退出后不持久化、不上传。
- 日志缺失可能由 LSPosed 不可用、进程未加载、没有触发接口、日志未保留等多种原因造成，不应直接等价为“Hook 不存在”。
- Android framework hook 不会改变任何公网 IP、代理、VPN 加密或服务器账户状态。
- 当前没有 Hook 全量覆盖、Root/模块隐藏或防平台封号保证，勿用于真实账号风控测试。
- `SystemProfileSelector` 采用 fail-closed 单候选原则：调用字符串中有多个合法候选 Profile 包时不选择任何一个，避免共享 UID 误归因。

## 验收
- JVM：`HookAuditTest`（格式、脱敏、伪造字段、未知身份、聚合优先级、上限），`SystemProfileSelectorTest`（非拥有 UID、共享 UID、多用户、Strict 隔离）。
- CI：`gradle :app:testDebugUnitTest`、Macrobenchmark assemble、Debug APK build/upload。
- Android 17 / HyperOS：实际 Hook 日志、系统进程审计和自控 Probe 端到端测试 **NOT RUN**，留给 G08/G09。

## 未来改进
- G06：本地官方权限入口、配置健康度与运行证据分屏。
- G08：独立 Probe 生成有来源标记的结构化测试结果，验证 App 接收值及不同应用隔离。
- G09：系统升级、冷启动、热加载和 Root 框架兼容性回归。
