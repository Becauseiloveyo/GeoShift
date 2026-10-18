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

## 2026-10-17 · Round 003 — G03 跨进程 Hook 诊断实施（待 CI）
- **实施状态**：G03 = IMPLEMENTED_UNVERIFIED；Android CI 尚未确认。本轮不修改 VPN 隧道/代理/加密，不启用共享 Strict。
- **接口核对**：libxposed 官方 `XposedInterface.getRemotePreferences` 明确指出在被 Hook 的进程中只读；不通过它向远端偏好写诊断结果。
- **实现**：`HookAudit.kt` 定义有限字段和有限类别的可解析诊断格式；`HookAuditEmitter.kt` 在 Hook 处仅去重排队、异步写入 Xposed 日志；`GeoShiftModule.kt` 记录 App Location getter、framework last/current 的部分安装/命中/改写/失败事件；`SystemLocationDeliveryHooks.kt` 记录 system_server 上已按 Binder UID/包名归因的事件与安装失败/覆盖不足。
- **界面**：新增 Diagnostics 导航、`GeoShiftDiagnosticsScreen.kt`；`MainActivity.kt` 通过系统文件选择器导入用户手动导出的 LSPosed/logcat 文本。解析长度上限 100 万字符、最多 2,000 条唯一事件；结果只在内存，退出后不上传、不持久化。
- **身份隔离**：`SystemProfileSelector.select` 拒绝一个调用携带两个不同、同 UID 且启用的位置 Profile 包名，避免“只选第一个”的歧义；保留跨 Android user 和非拥有 UID 失败闭合逻辑。
- **测试**：新增 `HookAuditTest.kt` 的格式/攻击字段/未知身份/优先级/边界测试；增加共享 UID、重复包名、跨用户隔离回归。
- **隐私承诺**：事件没有坐标、网络地址、Wi-Fi/基站或密钥；**包名与日志时间本身仍可能敏感**。导入的文本可伪造，仅用于历史观测，不能当作 Hook 正确性、真实位置防泄漏或第三方平台账户合规证明。
- **文档**：`docs/G03_HOOK_DIAGNOSTICS.md` 说明状态语义、日志导入限制、测试矩阵和可信度边界。
- **未完成**：本轮代码的 GitHub Actions 完整结果仍需核实；设备上真实 API 的实际交付结果 **NOT RUN**，由 G08/G09 验证。
- **下一步**：触发并审阅 Android CI；有编译/单测失败则修复并重跑；成功后更新 G03=DONE（代码级），再推进 G04。

### Round 003 增补 / 最终源码 CI 范围
- 在首轮 CI 排队后补充：`GeoShiftModule` 对时区、Locale、Geocoder、Wi-Fi、Telephony、CellIdentity 的安装/失败与部分命中/改写事件；Diagnostics 页面区分 `io.geoshift.system` 的系统全局安装证据与归因到 Profile 的事件；日志解析兼容 CRLF。
- `HookAuditTest` 新增 CRLF 回归。所有观测状态仍表示历史、可伪造的日志证据，非实际隐私安全通过率；没有独立 Probe 的真实设备验证。
- 旧的首轮 CI `39038527478` 仅覆盖较早快照，不可替代本次最终代码验证。记录下一次无 `[skip ci]` 提交对应的完整流水线作为**G03 最终验收**。

## 2026-10-17 · Round 003 验收补录 — G03 CI PASS
- **最终源码与测试提交**：`a68e68a58579f665919097508ca4ac5fddeb7d14`（含 G03 全部功能、修复、文档及单测）。
- **CI 证据**：[GitHub Actions #39038730416](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39038730416)，`completed / success`；job `117413766659`。
- **实际通过步骤**：`gradle :app:testDebugUnitTest --stacktrace`、`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace`、`gradle :app:assembleDebug --stacktrace`、Debug APK 上传；构建日志依次显示 `BUILD SUCCESSFUL`（1m36s / 1m49s / 28s）。
- **Artifact**：`GeoShift-debug-a68e68a58579f665919097508ca4ac5fddeb7d14`；GitHub Actions artifact ID `12103008910`；归档大小 `10,612,007 bytes`（不是 APK 内文件大小、签名状态或实机覆盖证据）。
- **之前的工作流**：`#39038527478` 只验证了较早代码快照的 JVM 测试，随后因并发策略取消；不能作为最终提交的完整验收。本轮使用 `#39038730416`。
- **任务结论**：**G03 = DONE（代码级 CI 门槛通过）**。诊断界面反映的是从用户手动导入、且可能伪造的历史日志观察；实际 Android 17 / HyperOS 真实 Hook 命中、真实值泄露和非目标应用隔离测试仍 **NOT RUN**，归 G08/G09。
- **下一任务**：G04 定位 API 调用语义与回调正确性；重点禁止无权限或 null 返回路径被错误合成“成功定位”，验证取消、回调、缓存、注册与移除监听器，并按 SDK 适配异步 Geocoder。

## 2026-10-17 · Round 004 — G04 定位 API 语义与监听器生命周期（CI 待验证）
- **状态**：G04 = IMPLEMENTED_UNVERIFIED，必须以本轮新提交为准取得实际 GitHub Actions 测试日志。此前 G03 CI 不构成本轮证明。
- **源码变更**：`GeoShiftModule.kt` 的 `getLastKnownLocation`、`getCurrentLocation`、Google `LocationResult` 不再凭空把 null/empty 结果转换成模拟坐标；只有真实返回的非空 `Location` 被复制修改；`CancellationSignal` 与 Executor 原样传入。位置监听器使用 `LocationListenerRegistry` 按引用身份复用，注销成功再释放，失败可重试，一次性成功回调后移除。系统回调 `SystemLocationRewriter` 不再复制空/无 Location 列表。
- **异步 Geocoder**：API >= 33 时适配 `getFromLocation(double,double,int,GeocodeListener)`；原异步请求照常执行，透传 `onError` 和空地址列表，仅为与现配置相符的有效非空成功结果执行回调结果替换；配置变更时回退原响应。原同步 Geocoder 行为保持不变。
- **新增纯 JVM 测试**：`LocationCallbackPolicyTest.kt`（null/empty/nonempty/Geocoder error/no backend 保真）和 `LocationListenerRegistryTest.kt`（引用身份、重用、移除失败后的保留语义、清理旧包装）。
- **文档与门槛**：`docs/G04_LOCATION_CALLBACK_SEMANTICS.md`，含 G08/G09 受控 Android 17 / HyperOS 实机矩阵。CI = JVM tests + benchmark compile + debug APK build/upload。真机验证 **NOT RUN**。
- **边界**：系统原生权限结果、真实取消行为、混合相同监听器并发单次/连续注册、SDK 不同 ROM 的回调实际交付未运行；不承诺“绝不泄露”，不修改 VPN/代理加密、账号风控或严格模式默认状态。
- **下一步**：触发并完成 G04 最终 CI，若失败修复；成功后 G04 标记为 DONE（代码级）且记录 run ID/提交与产物。之后进入 G05 Profile 地域一致性、无线数据过期管理。

### Round 004 CI 失败与明确修复（不得删除失败证据）
- **失败流水线**：[GitHub Actions #39050444176](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39050444176)，提交 `101fc8ce9c1bf089f80dc12f811985bfbb1c6902`，job `117447759935`，`failure`。
- **唯一编译错误**：`app/src/test/java/io/geoshift/app/core/LocationCallbackPolicyTest.kt:13`，Kotlin 泛型 `LocationCallbackPolicy.transform(null)` 推断为 `Nothing`，导致 `expected Nothing, actual String`；不是运行时定位 API 失败。
- **修复**：对 null 回调测试显式使用 `LocationCallbackPolicy.transform<String>(null)`；修复提交 `a27236599ac09c8fc95d1800b4d6a314e44136e5`。不改其他断言、不删除测试。
- **后续检查**：重新触发完整 CI；未成功之前任务状态继续 `IMPLEMENTED_UNVERIFIED`。

### Round 004 G04 最终验收 / CI PASS
- **最终受测提交**：`e236cc1d733bc83967653dbc46d510bc8d34d504`（包含上一轮 CI 失败证据、泛型测试修复以及全部 G04 代码）。
- **GitHub Actions**：[运行 #39050614646](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39050614646)，job `117448260390`，`completed / success`。
- **真实执行通过**：`gradle :app:testDebugUnitTest --stacktrace` → BUILD SUCCESSFUL 1m27s；`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace` → BUILD SUCCESSFUL 1m11s（**仅编译，不是实测性能**）；`gradle :app:assembleDebug --stacktrace` → BUILD SUCCESSFUL 19s；Upload debug APK → success。
- **Artifact**：`GeoShift-debug-e236cc1d733bc83967653dbc46d510bc8d34d504`，ID `12107328331`；GitHub artifact archive `10,616,040 bytes`。不等于 APK 内部文件大小、签名核验或实机功能验证。
- **验证结论**：G04 = **DONE（代码级及 CI 门槛）**。新建纯 JVM 单测已纳入 Gradle 实际执行，未从 Gradle 日志单独确认具体通过用例总数；不能臆造计数。
- **限制和遗留设备验证**：Android 17/HyperOS 上的动态 API Hook、Geocoder callback 顺序/线程、取消/超时、系统异常回退、混合模式相同 Listener 的重复注册、非目标应用隔离仍 **NOT RUN**，归 G08/G09。同步 Geocoder 的既有模拟地址行为未改变。不承诺 100% 无泄漏或对任何平台账号限制有效。
- **下一任务**：G05 原子 Profile 地域快照、Follow-VPN 地理数据更新及 Wi-Fi/基站的过期状态与重载不变式；必须先读本计划/日志/当前源码，补回归测试和 CI。

## 2026-10-17 · Round 005 — G05 原子 Profile / 无线数据过期保护（待验证）
- **状态**：G05 = IMPLEMENTED_UNVERIFIED；不能以 G04 的 CI 当作本次验收。
- **根因**：既有 `GeoProfileSynchronizer` 修改 GeoIP 国家/时区/经纬度而不使旧无线身份失效；`ProfileStoreV2` 将 Profile 散存在多个 Remote Preferences 键中，缺少每份 Profile 的原子快照和过期后台写入识别。
- **实现**：`GeoProfile` 增加 `revision`、`geoRevision`、`radioGeoRevision`、`radioStale`、`geoSource`；`GeoContextPolicy` 通过国家/地区/城市变化或 ~1 km 位置移动标记旧 Wi-Fi/基站过期；`ProfileRevisionPolicy` 比对旧版后递增提交版本；`ProfileCodec` 新增 v5 元数据。
- **持久化**：`ProfileStoreV2` 优先读取完整 `atomic_snapshot_v5`，写入一次性 `SharedPreferences.Editor.commit()`；继续携带兼容旧键，损坏的权威快照不回退混合键；旧 Follow-VPN 无线数据的来源无法核实时标记 stale，但保留可恢复字段。当前未做真实 LSPosed Remote Preferences commit 测试。
- **运行态**：`GeoShiftModule` Wi-Fi/Telephony Hook 和 `effectiveWifiAccessPoints` 检查无线地理绑定状态；过期时不再声称模拟数据有效。**旧无线数据被停用时，系统原始无线信息可能透传**，不能称为防止系统值泄露。
- **产品操作**：ProfileEditorScreen 显示过期告警与显式确认入口；无线提供者仅将实际成功获取的类别标记可用，缺失类别的旧数据不会混入新城市 Profile；MainActivity 保存后重新读取带提交版本的新 Profile，后台旧数据写入失败。
- **测试**：新增 `GeoContextPolicyTest`、`ProfileRevisionPolicyTest`，扩充 `GeoProfileSynchronizerTest`，覆盖跨国/跨区/移动、同城小抖动、无线绑定手动恢复、GeoIP 失败不落库、旧 Revision 冲突拒绝。
- **使用限制**：VPN/代理加密、Account eligibility、Root/Integrity/Anti-cheat 规避均不实现。所有真实 Android 17 / HyperOS Hook 与 Remote Preferences 原子通知验证仍为 **NOT RUN**，留 G08/G09。
- **详细说明**：`docs/G05_ATOMIC_PROFILE_AND_RADIO_STALENESS.md`。
- **待执行**：记录本提交 CI Run 与失败/通过证据；成功方可把 G05 标记为 DONE（代码级），下一任务 G06。

### Round 005 G05 最终验收 · CI PASS
- **最终受测提交**：`4f832a63bea6ca5ae5148a0c5508194a9778970a`（包含 G05 运行时代码、单测、说明文档以及实施阶段日志）。
- **实际 CI**：[GitHub Actions #39092267241](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39092267241)；job `117571325037`，`completed / success`。
- **真实通过步骤**：`gradle :app:testDebugUnitTest --stacktrace` → BUILD SUCCESSFUL in 1m57s；`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace` → BUILD SUCCESSFUL in 1m48s（编译而非实测性能）；`gradle :app:assembleDebug --stacktrace` → BUILD SUCCESSFUL in 28s；Upload debug APK = success。日志未提供明确单测用例总数，不做推断。
- **构建产物**：`GeoShift-debug-4f832a63bea6ca5ae5148a0c5508194a9778970a`，GitHub Actions artifact ID `12122944270`，归档大小 `10,624,371 bytes`；不能误称 APK 内部大小或真机安装证据。
- **最终状态**：G05 = **DONE（代码级 CI 门槛）**；Profile CAS / 过期策略的纯 JVM 测试实际成功。LSPosed Remote Preferences `commit/readback`、断电恢复、v1–v5 真实迁移、无线 Hook 的实际交付、非目标应用隔离与 Android 17/HyperOS 稳定性均 **NOT RUN**，列入 G08/G09。
- **重要限制**：标记旧无线数据 stale 并停用模拟只使旧数据不再被当作当前城市数据，**系统原始无线信息仍可能透传**，不构成真实信息零泄漏保证。用户如需阻止相关 API 访问，应优先使用 Android 原生权限。
- **下一任务**：G06 原生位置权限引导与诊断 UI 区分；先读本计划/本日志/最新代码，随后实现测试再通过新一轮 CI。

## 2026-10-17 · Round 006 — G06 原生权限与独立证据诊断（CI 待验证）
- **状态**：G06 = IMPLEMENTED_UNVERIFIED；实际 Android CI 未结束前不能标为 DONE。
- **官方依据**：Android 12+（API 31+）的精确/大致授予由 Android 设置控制；权限降级可能导致目标应用进程重启，不能通过 GeoShift 自动修改第三方授权。
- **新增模型**：`NativeLocationPermission.kt` 纯逻辑分类 UNKNOWN/NOT_REQUESTED/DENIED/APPROXIMATE/PRECISE；`AndroidLocationPermissionReader.kt` 读取 PackageManager 目标应用位置授权，查询错误或不可见保留 UNKNOWN。
- **独立证据**：`PrivacyEvidenceSummary.kt` 将 Profile 配置完整度、历史 Hook 安装/命中/改写/失败计数及 Probe 测试状态严格分开；G08 尚未执行，因此实际 Probe 一律 NOT_RUN，即使配置得分为 100 或已导入 REWRITTEN 日志也不会显示 PASS。
- **UI 实现**：`MainActivity` 在 `onResume` 和 Profile 刷新后重新查询目标包授权；Diagnostics 页每个 Profile 展示只读权限快照、单独配置分数、历史事件和实际 Probe 未测试状态；`Open Android app permissions` 只调用官方 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`，不尝试授予、撤销第三方权限。Android 12 前不宣传精确度切换。
- **测试**：新增 `NativeLocationPermissionTest.kt` 与 `PrivacyEvidenceSummaryTest.kt`；保留全部原有测试，不把旧 G05 CI 记作本轮通过。
- **隐私边界**：PackageManager 数据不证明 AppOps、后台授权、系统定位开关、实际 API 值或网络侧地理信息；手动导入的 LSPosed/logcat 日志可以伪造。真实 Android 17/HyperOS 上的系统跳转与权限切换 **NOT RUN**（留 G08/G09）。
- **文档**：`docs/G06_NATIVE_PERMISSIONS_AND_EVIDENCE.md`。
- **下一步**：检查新的 G06 CI（unit tests、benchmark compile、debug build、upload）。失败要记录原因并修复；通过后把 G06 标记为 DONE（代码级），继续 G07 Keystore 密钥处理。

### Round 006 最终验收 · G06 CI PASS
- **受测完整提交**：`f56f6fd330e45d00e2248b55b66eac91c86e7380`，包括 G06 的所有 UI/权限读取、证据模型、JVM 测试及文档修改。
- **CI 来源**：[GitHub Actions #39093873195](https://github.com/Becauseiloveyo/GeoShift/actions/runs/39093873195)，job `117576119210`，`completed / success`。
- **真实通过项目**：`gradle :app:testDebugUnitTest --stacktrace` → BUILD SUCCESSFUL in 1m51s；`gradle :app:assembleBenchmark :benchmark:assembleBenchmark --stacktrace` → BUILD SUCCESSFUL in 1m59s（只是编译，不是实测性能）；`gradle :app:assembleDebug --stacktrace` → BUILD SUCCESSFUL in 32s；上传 Debug APK 成功。
- **构建产物**：`GeoShift-debug-f56f6fd330e45d00e2248b55b66eac91c86e7380`，GitHub Actions artifact ID `12123517510`，压缩产物大小 `10,631,456 bytes`。这不代表独立 APK 内部大小、签名认证或设备安装结果。
- **完成状态**：**G06 = DONE（代码级 CI 门槛）**。自动化测试覆盖权限分类、未知结果保留、配置 100 分不等于 Probe PASS、历史 Hook 证据不得升级为实际结果；精确用例总数未独立核对，不虚构。
- **设备验证仍 NOT RUN**：目标包可见性及 PackageManager 返回值、Android 17/HyperOS 应用设置跳转与权限切换、AppOps/后台授权、权限降级后的应用进程重启、LSPosed 与 API 返回值。均归 G08/G09。
- **下一任务**：G07 Android Keystore 本地 provider API key 保护、迁移/损坏回退、默认日志与导出脱敏；继续遵守不扩展 VPN/代理与风控绕过的约束。
