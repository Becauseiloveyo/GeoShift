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
