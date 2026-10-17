# GeoShift v0.5.0 开发计划书（Privacy & Reliability）

> **唯一执行依据 / Single Source of Truth**
>
> 状态：**IN PROGRESS — G00-G04 COMPLETE; G05 NEXT**  
> 计划版本：1.0.0；建立日期：2026-10-17  
> 仓库：`Becauseiloveyo/GeoShift`；开发分支：`feat/v0.5-privacy-reliability`  
> 基线分支：`feat/v0.1-foundation`；基线提交：`31b14b46923c9f710936b4298e00231781c263f7`（2026-09-07）  
> 进度与验证日志：`docs/GEOSHIFT_V0.5_EXECUTION_LOG.md`  
> 维护方法：**每轮先读本计划与执行日志、再核对真实代码、只做未完成任务、提交证据后更新状态。**

## 0. 目标、边界与成功定义

### 0.1 产品目标
将已有 GeoShift Android 10+ / Android 17 / HyperOS / Modern libxposed 原型推进为**可测试、可恢复、尽量减少意外真实地理信息暴露**的单应用地理环境模拟工具。主要服务隐私、开发、授权兼容性测试。项目仍使用 Kotlin、Compose、libxposed API 102、已有 ProfileStoreV2 和 provider 接口，不重写底座。

### 0.2 明确不做
- 不开发 VPN 加密、代理协议、隧道、DNS 代理或 WebRTC 网络防泄露；网络加密和代理配置由其他软件负责。
- 保留现有 Follow-VPN **出口 IP → 地理 Profile** 的同步能力；仅核对同步有效性与误差，不承担 VPN 网络安全。
- 不做 Play Integrity / 硬件证明 / Root/LSPosed 痕迹对抗 / 服务端风控绕过 / 反作弊 / 支付地区或账号地区限制绕过；**不承诺降低 Claude 或其他平台封号风险**。
- 不实施无授权修改第三方应用数据、破坏其功能或对抗安全控制的行为。
- 不在没有设备证据时声称“零泄漏”“完全隐藏”“100% 有效”或“全平台适配”。

### 0.3 不可违反的工程原则
1. **单应用优先**：默认仅影响明确授权的目标包；无法归因的共享 provider 不得冒充单应用隔离。
2. **严格会话必须显式开启**，有限时、可停止、崩溃/重启后不自动重新启用；系统级影响必须显示告知。
3. **隐私优先不等于伪造成功**：无法安全取得预期行为时显示“不完整/未验证”，优先建议原生位置权限“拒绝/大致位置”，不得把 fail-open 解释为保护成功。
4. 不对 provider 原始共享对象就地修改；热点路径不做网络、同步磁盘、JSON 解析或阻塞工作。
5. 不采集不必要的真实坐标、定位历史、账号信息；日志默认脱敏，密钥不写普通日志和可导出 Profile。
6. 所有状态和成绩来自可复现证据；无法运行的测试标记 **NOT RUN**，不得改写为 PASS。
7. 不自动启用全局 Hook、不破坏导航/紧急定位/非目标应用；重大风险先停用有问题的能力。

## 1. 已确认基线（不代表测试通过）

- GitHub `main` 只有 README，**不是可执行源码基线**；实际代码位于 `feat/v0.1-foundation`（135 个 tree entry）。
- 已有 UI、多 Profile、手动位置、VPN 地理同步、Wi-Fi/蜂窝数据、导入导出、libxposed 普通/系统/Strict Hook、静态健康评分、JVM 测试与 Android CI 配置。
- 仓库当前尚无正式 Release；未找到项目许可证；主 README 自述 `v0.4.2-dev`。
- 目前可见 7 个 JVM 测试源码文件，合计 17 个 `@Test`，**只是静态计数，未执行**。
- 已定位风险：StrictProviderDeliveryHooks 对共享定位源全局生效；失败路径可能保留真实结果；Geocoder 仅部分覆盖；VPN 切换可能保留旧 Wi-Fi/基站；ProfileHealth 不等于真实隐私验收；ProviderSettings API key 用普通 SharedPreferences。
- 暂无可验证的 v0.4.3 或更晚版本完整源码/APK 与对照实测结果；不将历史对话里的测试表述当成当前分支的通关证据。

## 2. 开发任务总表（顺序与依赖）

**状态定义：** `TODO` 未开始；`IN_PROGRESS` 进行中；`BLOCKED` 有客观阻塞；`IMPLEMENTED_UNVERIFIED` 已提交但未验证；`DONE` 有已记录验证证据；`SKIPPED` 有书面理由。

| ID | 等级 | 内容与边界 | 交付及验收门槛 | 状态 |
|---|---|---|---|---|
| G00 | P0 | 固化计划、Git 开发分支、基线、执行规则 | 计划和执行日志可从分支读到，未篡改原开发分支 | DONE |
| G01 | P0 | 可重复构建和测试基线 | GitHub Actions #39010985422：JVM tests/benchmark 编译/debug APK/upload 全部成功；设备运行未验收 | DONE |
| G02 | P0 | Strict Map Session 安全止损 | 显式确认 + 120s TTL + Shell stop + UID/包名隔离 + 禁用 Broker 全局适配；完整 CI #39037167354 成功；实机测试归 G09 | DONE |
| G03 | P0 | Hook 可观测性与身份隔离 | 异步脱敏审计、诊断日志导入及状态 UI、共享 UID/多用户歧义拒绝单测；完整 CI #39038730416 通过，真机留 G08/G09 | DONE |
| G04 | P0 | 定位 API 语义和回调稳定性 | null/空列表保真、取消与监听器注册/注销、API 33+ 异步 Geocoder；首次 CI 编译失败已修复，最终 CI #39050614646 全部成功；真机归 G08/G09 | DONE |
| G05 | P1 | 原子地理 Profile、无线数据有效期 | VPN 地区变化时旧无线数据不得作为新地区“有效”；快照版本、来源、过期状态、同步失败回退有单测 | TODO |
| G06 | P1 | 原生隐私权限引导和诊断 UI | 精确/大致/拒绝的官方设置入口；静态配置完整度与实测保护效果分开；未测试标“未验证” | TODO |
| G07 | P1 | 本地密钥、日志和导出卫生 | 原生 Android Keystore 保护 provider API key；迁移与异常测试；日志和 Profile JSON 不输出凭据/真实坐标 | TODO |
| G08 | P1 | 独立自控 Probe 与对照测试 | 编写受控测试 app/automated tests，覆盖 Framework、Fused、Geocoder、Wi-Fi、Telephony；非目标应用对照；输出机器可读结果 | TODO |
| G09 | P1 | Android 17 / HyperOS 实机闭环 | 冷启动、前后台、位置变化、VPN GeoIP 同步、重启/停止、拒绝权限等实机矩阵；无 P0 遗留 | TODO |
| G10 | P2 | 许可合规、性能回归与正式发布 | 许可证选择与第三方 NOTICE；CI、源码/APK/测试记录和 SHA256 对应；正式 Release 发布前须 G01-G09 门槛达标 | TODO |

**执行依赖**：G01 → G02/G03 → G04 → G05/G06/G07 → G08 → G09 → G10。可在不改变安全边界前提下合并提交，但不得跳过验收门槛。

## 3. 工作包详情

### G01 可重复构建（先决条件）
1. 对原始基线执行 `gradle :app:testDebugUnitTest :app:assembleDebug :benchmark:assembleBenchmark --stacktrace` 或项目 CI 明确认可的等价命令。
2. 记录 Java 21、Gradle 9.5.1、Android SDK 37、libxposed API 102 及依赖可用性；不得猜测输出。
3. 检查 CI 在当前工作分支或 PR 上是否真实运行、是否上传 debug APK。
4. 将原始失败分类：环境/依赖/源码/运行时；不因计划要求而隐藏失败。

### G02 Strict 模式的安全边界
- 为 `StrictMapSession` 定义时间戳与 TTL；所有使用入口一致判定过期，不能只在 UI 中过期。
- 对共享系统 provider、Xiaomi/AOSP Fused 回调做“影响范围”分类；不得把全局修改称作单应用隔离。
- 将回调黑洞（丢弃）路径列为实验功能；验证停用后正常定位恢复。
- 在没有授权测试设备时仅提交可执行的纯逻辑测试，实机状态保持 NOT RUN。

### G03-G04 正确性与可观察性
- 区分 Hook `installed`、`hit`、`rewritten`、`error`、`notCovered`。只保留必要的聚合计数/类别，避免原始坐标泄露。
- 同时测试原始 `Location`、`List<Location>`、`LocationResult`、null、取消、超时、listener 注册/移除；不得破坏第三方正常 API 语义。
- 以 Android 官方 API 为准，在运行时检查类/方法是否存在，不硬编码特定设备必定支持。

### G05 Profile 数据模型
- 为地理同步和 Wi-Fi/蜂窝记录加入 `revision`、`source`、`stale` 或等价不变式。
- 新 GeoIP 位置变化后，旧无线信息只能保留为**历史/待重新确认**，不能静默标记为当前有效数据；遵循“最小修改、用户手填数据可恢复”。
- 原子替换 immutable snapshot；失败维持最后一个可用的**已核验**配置并显示“过期/未同步”。

### G06-G08 用户可理解的隐私保护
- 独立展示“配置健康度”和“真实 API 采样结果”；100 分不能表示 100% 防泄露。
- 官方权限优先于 Hook：目标应用无必要的位置权限时建议拒绝或大致位置；不偷偷赋予额外权限。
- 证据报告至少列 API、系统版本、应用包、测试方法、期望、实际（默认脱敏）、PASS/FAIL/NOT_RUN、时间和版本提交。
- 可独立卸载的 Probe 仅对测试应用与自己控制的设备运行；不得用真实 Claude 等账号进行风控/封号实验。

### G09-G10 收尾
- 以 Android 17 / HyperOS 为主要设备矩阵，AOSP 作为对照（若未获得设备则标 BLOCKED，不得宣称实机完成）。
- 验收：目标应用定位链路 30 分钟连续无非预期回跳；**正常按应用模式**下 3 个非目标对照应用定位输出不被改变；实验 Strict 模式的共享 provider 影响范围必须明确报告且不得用于日常；超时/停用/重启后没有残留 Strict 全局效果；导出不含密钥；自动化测试无失败。**这些是目标门槛，不是已测结果。**
- 项目尚无开源许可证，未经用户选定不得擅自添加 License 或 GPL 实现；发布必须有第三方许可清单与 SHA256。

## 4. 开源复用清单和许可闸门

| 来源 | 可能复用 | 许可处理 |
|---|---|---|
| https://github.com/noobexon1/XposedFakeLocation | Modern libxposed、作用域及配置生命周期思路 | MIT；拷贝代码前核对具体文件版权和保留声明 |
| https://github.com/0xfnzero/gps-locator | 自控 Probe、ADB 设备测试、报告设计 | MIT；仅在证实稳定合适后集成 |
| https://github.com/jqssun/android-gps-setter | Android 定位兼容策略参考 | GPL-3.0；**仅参考公开的原理/行为，不复制代码**，除非项目许可方案经过专门审查 |
| https://github.com/JingMatrix/libxposed-api | API 102 签名、生命周期与规范 | Apache-2.0；优先官方版本与 Javadoc |

**复用流程**：查项目当前代码/维护情况 → 核许可证/NOTICE → 记录 commit/tag/文件 → 做最小试验 → 独立测试 → 再集成。不以“开源”作为直接复制的许可依据。

## 5. 每次执行的强制工作流（包括新的 ChatGPT/Codex 对话）

1. **先读** `AGENTS.md`、本计划、`docs/GEOSHIFT_V0.5_EXECUTION_LOG.md` 和目标分支最近提交；定位最新状态和阻塞。
2. **再审** 当前源码、测试和变更；计划是工作意图，不能代替真实源代码。
3. 选依赖已满足、优先级最高的 `TODO` 或 `IMPLEMENTED_UNVERIFIED`；写出本轮目标与成功条件。
4. 先跑相关既有测试；做最小修改；增补回归测试；不得悄悄删除旧测试。
5. 实际运行可以运行的检查；失败保留日志；无构建/设备能力就写 `NOT RUN` 与原因。
6. 同步修改**本计划任务状态**和**执行日志**，填提交 SHA、文件、命令、结果、限制和下一任务。先提交改动，再以可验证的证据状态记录进度。
7. 安全检查：没有凭据、坐标、私人数据进入 Git 和测试报告；不自动扩大 LSPosed 作用域或开启全局严格模式。
8. 每轮对用户交付：完成的任务 ID、真实测试结论、剩余阻塞、下一项；**禁止只报计划或把“实现”当成“验证通过”。**

不需要每一小步都询问用户；只在会不可逆地修改设备、删除数据、改变项目许可证或需要第三方账号授权时请求明确许可。**未来的开发只在用户发起新的执行请求时继续，不存在自动后台持续开发。**

## 6. 版本门禁与进度记账

- **Alpha / GATE A**：G01-G04（2026-10-17 均有独立代码级 CI PASS），关键错误路径有自动化回归；真实设备仍需 G08/G09，关键错误路径有自动化回归。
- **Beta / GATE B**：G05-G08 完成，独立 Probe 能实际复验；重要日志默认脱敏。
- **Release Candidate / GATE C**：G09 在目标设备通过；没有已知 P0；尚未验证平台明确列为 NOT RUN。
- **Stable / GATE D**：G10 完成，发布包哈希、许可和来源完整，使用范围限制已记录。

计划改动必须保留原任务的状态、历史和理由；新增任务使用 G11、G12……；任务拆分用 Gxx.a/Gxx.b。不可通过删除失败项制造 100% 通过率。
