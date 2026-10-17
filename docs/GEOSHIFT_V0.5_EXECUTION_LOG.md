# GeoShift v0.5.0 执行日志

> 先读 `docs/GEOSHIFT_V0.5_PLAN.md`；证据优先，任何未跑的测试均记为 NOT RUN。

## 2026-10-17 · Round 000 — 计划落地与代码基线
- **已做**：核实 `feat/v0.1-foundation` 的最新提交 `31b14b46923c9f710936b4298e00231781c263f7`；确认 `main` 仅 README；阅读 README、Manifest、相关 Hook、Profile、同步与测试文件；建立开发分支 `feat/v0.5-privacy-reliability`。
- **发现**：共享 strict provider 修改风险、Geocoder 适配缺口、真实位置潜在 fail-open、Wi-Fi/基站旧配置跨地区残留、缺真实 Hook 覆盖诊断、API 密钥以普通偏好存储、缺正式许可证。
- **单测证据**：7 个测试源码文件、17 个 `@Test`（仅静态检查；**NOT RUN**）。
- **实际构建/真机**：**NOT RUN**（本轮连接了 GitHub 仓库，但尚未获得可运行 Android SDK/真实设备环境证据）。
- **变更**：新增本计划、此日志、根目录 `AGENTS.md`；不修改运行时代码，不扩大 Hook 作用域。
- **任务状态**：G00 = DONE；G01-G10 = TODO。
- **下一任务**：G01 构建与测试基线；确认 CI 是否适用于新分支，保存测试日志和构建来源；通过前不启动风险较高的 Hook 行为修改。

## 2026-10-17 · Round 001 — G01 Android CI 基线验证
- **任务**：G01 = DONE（仅构建/CI 级；非真机运行验收）。
- **变更**：`.github/workflows/android.yml` push filter 增加 `feat/v0.5-privacy-reliability`；提交 `3fea5ed16ef98fdc56e8d34e441bc2aff6dccfa0`。
- **CI 证据**：GitHub Actions [#39010985422](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39010985422)，`success`；对应 job `117333278655`。
- **通过步骤**：`gradle :app:testDebugUnitTest --stacktrace`、`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace`、`gradle :app:assembleDebug --stacktrace`、`Upload debug APK`，全部完成成功。
- **构建日志**：Unit BUILD SUCCESSFUL in 1m38s；Benchmark BUILD SUCCESSFUL in 1m56s；Debug BUILD SUCCESSFUL in 32s。日志未单独解析为确切测试用例总数；之前 17 只是 `@Test` 静态计数。
- **APK artifact**：`GeoShift-debug-3fea5ed16ef98fdc56e8d34e441bc2aff6dccfa0`，GitHub artifact ID `12093067222`，zip archive 10,589,792 bytes（不是 APK 内部文件大小或功能验证）。
- **本地限制**：当前 ChatGPT 容器具备 Java 21，但无 Gradle、Android SDK，且无法解析 github.com；未在本机重复运行构建、未实机安装、未完成 Hook 端到端测试：**NOT RUN**。
- **源码分支说明**：与上一开发基线相比，此轮只增加文档和 CI 分支触发；运行时代码没有变化。
- **下一任务**：G02 Strict Map Session 超时、隔离和停止语义；先加纯逻辑测试，再修改系统 Hook，必须有 CI 证据后才能标记 DONE。

## 2026-10-17 · Round 002 — G02 Strict Map Session 代码修复（提交待验证）
- **当前状态**：G02 = IMPLEMENTED_UNVERIFIED；等待新的 GitHub Actions 完整构建，不能提前写 DONE。
- **已提交源码**：`StrictSessionPolicy.kt`（有上界的单调时钟 TTL / 显式确认）；`StrictSessionControl.kt` 和 `StrictSessionCommandReceiver.kt`（root/shell-only 命令新增 `acknowledge_global_impact` 与 `duration_ms`，默认不启用）；`SystemRuntimeSnapshot.kt`（每次读会话检查 TTL）；`GeoShiftSystemModule.kt`、`StrictProviderDeliveryHooks.kt`（系统 provider 显式门控）；`SystemLocationDeliveryHooks.kt` 与 `SystemProfileSelector.kt`（移除 Strict 对 UID/包名选择的绕过）；`GeoShiftStrictPackageModule.kt`（暂时完全禁止共享 Broker/高德 callback-blackhole Hook）；`StrictProcessSession.kt`（休眠代码防御式 TTL 门控）。
- **测试代码**：新增 `StrictSessionPolicyTest.kt`（显式确认、限时、边界、重启/时钟偏移）；修改 `SystemRuntimeSnapshotTest.kt` 和 `SystemProfileSelectorTest.kt`（过期、失效、无身份调用不能命中）。
- **文档**：`docs/STRICT_SESSION_SAFETY.md` + README 显式的受控设备使用方式和风险提示。
- **安全差异**：未经设备验证的共享 Broker 位置 Hook 不再安装。系统共享 Provider Hook 仍存在，但**只有开发者显式确认系统级影响且 TTL 有效**才会修改数据；正常模式保持原有包身份限定。
- **阶段性验证**：静态复核修改过的源码与测试逻辑；当前提交后的 GitHub CI 结果尚未确认，真机复测 **NOT RUN**。
- **现有限制**：CLI stop 经 Remote Preferences 通知而非同步全链路屏障；缺应用内紧急停止 UI；实时地图和非目标应用对照由 G08/G09 实际验证。不得描述为“完全无泄漏”。
- **下一步**：等效于原 CI 的新分支流水线必须通过 JVM tests、Macrobenchmark 编译、Debug APK build/upload，记录 run id/提交 SHA。若失败先修复，成功后 G02 可标 DONE（代码级），实机证据仍归 G09。

## 2026-10-17 · Round 002 验收补录 — G02 CI PASS
- **提交**：`467e2e6e5dbd207f816859d1339c9b55b13b7c2c`（G02 全部实际代码与测试、文档基线）。
- **构建证据**：[GitHub Actions #39037167354](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39037167354)，`completed / success`；job `117409282217`。
- **通过步骤**：`gradle :app:testDebugUnitTest --stacktrace`、`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace`、`gradle :app:assembleDebug --stacktrace`、`Upload debug APK`，均成功。Gradle 日志显示三次 BUILD SUCCESSFUL：1m37s / 1m54s / 33s；未由 CI 提供精确执行用例总数，因此不推断测试总数。
- **Artifact**：`GeoShift-debug-467e2e6e5dbd207f816859d1339c9b55b13b7c2c`，ID `12102812265`，GitHub artifact archive 10,590,742 bytes；不等同于真机安装、运行或隐私保护验证。
- **任务结论**：**G02 = DONE（代码级、CI 验收）**。纯逻辑单测证实时间/开关/归因不变式，核心实验行为仍须 G08/G09 的 Android 17 / HyperOS 设备测试。
- **下一任务**：G03 Hook 安装/命中/失败/未验证运行态诊断与身份隔离机制；先研究无敏感数据的事件计数与用户可见状态，再实现并验收。
