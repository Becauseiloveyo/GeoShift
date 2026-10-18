# G09 · Android 17 / HyperOS 自控真机验收

日期：2026-10-18 · 开发分支 `feat/v0.5-privacy-reliability`。

## 当前结论

**BLOCKED / NOT RUN**。当前代码工作环境没有可访问的 Android 17/HyperOS 手机，也没有 `adb` 可执行程序；本轮没有通过 ADB 查询手机、更未安装 App、获取用户敏感位置、修改系统设置、执行 Hook 或模拟测试。G01–G08 是**代码级 CI 门槛**，不等于真实位置接口已验证。G09 不得在设备缺席时标记 `DONE`。

## 自动化和隐私保护约束

仓库提供 `tools/g09_device_gate.py`：
- `preflight`：**只读**执行 `adb devices`、`adb shell getprop`、`adb shell pm path`，查看 SDK 版本、设备型号、GeoShift 主 App 与四个 Probe 的安装情况。输出不包含设备序列号、原始位置、Wi-Fi/基站、账号、IMEI；不验证 APK 签名、权限、LSPosed 作用域。
- `manifest`、`verify-manifest`：建立/校验四个 Probe APK 的 SHA-256 和源码提交标识；仅证明与给定清单匹配，**不证明 Android 安装包签名身份或硬件真实性**。
- `review`：融合 8 份对照报告、至少 6 份连续性报告、只读预检与 16 个手动检查项。所有人工填报均标记 `USER_REPORTED`；即使工具返回“待独立复核”，也不声称设备通过验收。

只在**用户拥有并授权的测试手机**上执行。ADB USB 调试由用户亲自在设备确认；所有安装、位置权限、LSPosed 配置调整均为用户操作，脚本不会自动安装或授权。系统级 Strict 模式可能影响其他 App，**不得作为普通单应用隔离的默认方式**；仅在明确可控的独立测试场景验证其超时与恢复，无法安全执行则记录 `NOT_RUN`。

## 取得 APK、核对四个测试包

1. 从 [GitHub Actions](https://github.com/Becauseiloveyo/GeoShift/actions) 选择**同一次成功运行**的 `GeoShift-debug-<SHA>` 与 `GeoShift-Probe-four-variants-<SHA>`；不能混合不同提交的 APK。G08 已成功构建的历史 CI 是 `#39099797863`，G09 改动后应优先用最新成功的 CI。
2. 解压两个 Artifact。让 Probe 根目录中保留 `probe-build-manifest.json` 和 `target/debug/probe-target-debug.apk`、`control1/debug/probe-control1-debug.apk`、`control2/debug/probe-control2-debug.apk`、`control3/debug/probe-control3-debug.apk`。
3. 在仓库根目录核对：
   ```bash
   python3 tools/g09_device_gate.py verify-manifest \
     --apk-root probe-apks --manifest probe-apks/probe-build-manifest.json
   ```
   `CHECKSUM_MATCH` 只表示四文件内容与 CI 清单匹配；如果文件名/结构不同，应根据实际 Artifact 检查，不跳过 SHA 校验。
4. 在自控设备上**由用户明确决定后**，手动安装主包及 Probe：
   ```bash
   adb install -r app-debug.apk
   adb install -r probe-apks/target/debug/probe-target-debug.apk
   adb install -r probe-apks/control1/debug/probe-control1-debug.apk
   adb install -r probe-apks/control2/debug/probe-control2-debug.apk
   adb install -r probe-apks/control3/debug/probe-control3-debug.apk
   ```
   如已有数据，`-r` 是更新安装而非抹除所有数据；但签名不一致时会失败，**不要在未明确备份的情况下卸载原 App 来绕过签名冲突**。
5. 验证只读环境：
   ```bash
   python3 tools/g09_device_gate.py preflight --output g09-preflight.json
   ```
   `READY_FOR_MANUAL_VALIDATION` 仅说明 SDK 37 与五包安装状态已核对，不表示 HyperOS 版本、LSPosed、位置权限、Keystore 或隔离已验证。多台设备连接时先自行明确 ADB serial，再用 `--serial` 指定；序列号不会写到报告中。

## 基线、正常按包 Hook 与对照

1. 确认普通 GeoShift Profile 暂未开启；所有 G02 Strict Session 均关闭；三个 Control 包不在模块作用域、没有 GeoShift Profile。
2. 在四个 Probe 应用中使用相同的临时高熵会话 Secret（16–128 字符，仅用于本次测试，不导出）、Session ID；原生权限保持一致，避免“精确 vs 大致”扰动对照。Target 输入拟测试的模拟目的地经纬度。不得用真实账号或非自控应用做测试。
3. 每个 Probe 选择 `baseline`，明确点击测量并各导出一份 JSON。
4. 只将 `io.geoshift.probe.target` 加到普通 LSPosed Scope，配置 GeoShift Profile 并启用；保持 Strict 模式关闭。启动应用时复核是否确实加载模块，报告中的 `NOT_RUN`、`DENIED`、`TIMEOUT` 都不算位置改写成功。
5. 四个 Probe 选择 `active` 测量并各导出一份 JSON。共 8 份文件，用 G08 的 `python3 tools/verify_probe_reports.py` 对照；有硬件、地理位置自然变化、Android 权限或 GMS 版本差异时需要解释，不能用“虚构通过”代替。

## 30 分钟连续性与其他实机门槛

目标 Probe 在 active 模式额外执行**至少 6 次**测量，每次单独导出 JSON，同一 Session ID、同一个 APK 版本、同一权限和同一真实设备；首次与末次有效报告时间相隔**至少 30 分钟**，每次目标位置 API 必须报告匹配，否则标为 FAIL 或 NOT_RUN。建议每 5–6 分钟一次，但这些只检测离散采样点，**不等于无间断地监测了整段 30 分钟**；若要证明真正连续不中断，需另有持续监听器轨迹或监测证据（且默认不得导出真实坐标）。

此外逐一填写 `docs/G09_DEVICE_MATRIX_TEMPLATE.json` 中 16 项：原生位置权限、唯一目标作用域、普通模式 Strict 关闭、Framework/Fused、三对照、null/取消/超时、Geocoder async、Wi-Fi/基站旧数据、Follow-VPN 地理同步、前后台、连续性、显式 Strict 停止与超时、强停与恢复、重启恢复、Keystore 升级迁移与重置、安全 Profile 导出。未测请保留 `NOT_RUN`；失败必须保留 `FAIL`。执行高影响 Strict 测试应在可停止且不影响正常位置服务的隔离条件进行；否则不要执行。

填写 `continuous30.startUtc`、`endUtc`、`observedSampleCount` 和 `unexpectedReturnCount`，以真实记录为准，不可用人为时间覆盖真实样本。运行：

```bash
python3 tools/g09_device_gate.py review \
  --preflight g09-preflight.json \
  --matrix docs/G09_DEVICE_MATRIX_TEMPLATE.json \
  --reports target-baseline.json target-active.json \
    control1-baseline.json control1-active.json \
    control2-baseline.json control2-active.json \
    control3-baseline.json control3-active.json \
  --continuity-reports target-minute-00.json target-minute-06.json \
    target-minute-12.json target-minute-18.json \
    target-minute-24.json target-minute-30.json \
  --output g09-device-review.json
```

`deviceGate=USER_REPORTED_COMPLETE_REQUIRES_INDEPENDENT_REVIEW` 表示所有必要的自报资料看似齐全，**不等于 PASS**，仍需核对目标机屏幕、LSPosed 实际作用域、原始构建 SHA/签名、真实回调数据与使用权限；结果本身没有硬件签名，易于伪造。

## 受控清理与恢复

- 完成实验后自行关闭 Target Profile；确认普通位置服务和非目标 App 恢复。不要在真实日常 App 中启用系统级 Strict。
- 如要卸载 Probe，只移除四个 `io.geoshift.probe.*` 测试 APK，不要在没有备份的情况下删除 GeoShift 主 App 用户配置。
- 报告虽经脱敏，仍可能包含设备型号、软件版本、包名和时间，分享前应检查；**不要上传 HMAC Secret 或原始 ADB / logcat 全量日志**。
- 如果设备无法访问，预检输出 `BLOCKED`，所有 G09 手机测试保持 `NOT_RUN`，G10 正式稳定版发布门槛不应被放行。
