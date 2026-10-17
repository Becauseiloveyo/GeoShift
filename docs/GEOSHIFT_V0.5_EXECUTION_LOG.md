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
