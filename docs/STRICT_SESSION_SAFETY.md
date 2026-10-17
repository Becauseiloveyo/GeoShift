# G02: Strict Map Session 安全边界与验证说明

版本：GeoShift v0.5.0-alpha（G02）  
日期：2026-10-17  
代码：`feat/v0.5-privacy-reliability`

## 范围
严格模式仅作为在**用户自控测试设备**上的共享系统定位提供者兼容性实验。不能认为它拥有可靠的单应用隔离，也不保证定位信息隐藏或规避第三方平台限制。默认的按包/Profile 模式与严格模式分离。

## 与 v0.4 的差异
1. 普通 `SystemLocationDeliveryHooks.resolveTarget` 与 `SystemProfileSelector.select` 即使存在严格会话，也必须按 Binder UID 与包名归因；严格会话不能绕过对照应用的身份筛选。
2. 严格模式的系统 provider Hook 只在满足 **显式启用、确认全局影响、有效配置、最长 120 秒单调时钟租约** 时应用；关闭、过期、旧系统启动命令和错误参数都不激活。
3. `GeoShiftStrictPackageModule` 是**无副作用**入口：禁用所有未经设备验证的 AMap private callback blackhole、AOSP/Xiaomi Fused broker-global hook。保留源码供 G08/G09 分析，但 G02 不加载。
4. `SystemRuntimeSnapshot.strictProfile(nowElapsedMs)` 每次读取都检查失效日期与 Profile 是否仍启用；不能把陈旧的 AtomicReference 当作有效会话。
5. 开机和 system_server 重启不自动恢复系统全局严格会话；旧偏好不属于新的系统进程运行时授权。

## 手动启用（仅测试设备）
首先通过 GeoShift 创建并启用目标位置 Profile，再在 shell/root 环境**明确承认其可能影响所有正在请求定位的应用**：

```sh
su -c 'am broadcast -n io.geoshift.app/.StrictSessionCommandReceiver --ez enable true --ez acknowledge_global_impact true --el duration_ms 120000 --es package com.example.ownedtest'
```

最大时长 120 秒。可选设置较短时长，例如 `--el duration_ms 30000`。必须主动确认 `acknowledge_global_impact true`；省略、传 false 或 TTL 超出 `1..120000` 将拒绝启动。

立即停止（shell 命令发出后，经 Remote Preferences 通知各相关模块；不是实时硬件级断电保证）：

```sh
su -c 'am broadcast -n io.geoshift.app/.StrictSessionCommandReceiver --ez enable false'
```

**限制：** 当前停用入口仍为开发用 shell 命令，没有独立 UI 紧急停用按钮；会话不会自动强制回收已有异步的定位结果，但所有新的 provider Hook 进入点检查到期。需要在 G09 用真机测试关闭后的恢复与异步行为。对于本机真实导航、紧急定位和金融/账号应用，不应开启此实验模式。

## 自动验证策略
- `StrictSessionPolicyTest`：到期边界、反向时钟、错误时长、显示确认、跨重启/墙钟漂移；
- `SystemRuntimeSnapshotTest`：Profile 启用、到期、清除状态；
- `SystemProfileSelectorTest`：开启 strict 时无 UID/包名不能命中，合法目标才能命中；
- GitHub Actions：JVM tests、Macrobenchmark 编译、Debug APK 构建；
- **真机：NOT RUN**，G08/G09 才执行已授权的实际定位对照。

## 未来允许恢复共享 broker Hook 的门槛
必须先由 G08/G09 独立 Probe 验证 3 个非目标应用不受影响，完成取消订阅、关闭、超时、屏幕锁定、进程重启等场景覆盖。任何共享 provider 无法证明调用对象隔离时，不得为日常 Profile 模式启用。

## 安全结论边界
编译、JVM 纯逻辑测试均不足以证明 Android 17 / HyperOS 上端到端正确性；不得声称完全防止真实位置泄露、100% 隐蔽、无封号风险。
