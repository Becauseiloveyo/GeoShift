# G08 — 独立受控 Probe 与三应用对照协议

日期：2026-10-18 · GeoShift v0.5 · 分支 `feat/v0.5-privacy-reliability`

## 交付

独立 Gradle `:probe` Android 应用模块，`minSdk 29 / targetSdk 37`；无 libxposed Hook 代码，不使用 GeoShift App 私有存储；可分别安装、卸载，四个变体共享源码、独立包名：

| Variant | 包名 | 用途 |
|---|---|---|
| `targetDebug` | `io.geoshift.probe.target` | **唯一**允许添加至正常 GeoShift LSPosed Scope / 配置为目标 Profile 的受控测试应用 |
| `control1Debug` | `io.geoshift.probe.control1` | 非目标对照 1，不配置模拟、不放入 Scope |
| `control2Debug` | `io.geoshift.probe.control2` | 非目标对照 2，不配置模拟、不放入 Scope |
| `control3Debug` | `io.geoshift.probe.control3` | 非目标对照 3，不配置模拟、不放入 Scope |

注意：四个 APK 应使用*同一源码提交、同一测试设备和同一临时 HMAC Secret*，并保持它们的原生位置权限不变。**不得启用 G02 系统级 Strict Session 进行单应用隔离验收**：共享 Provider 会影响其他应用，它是另一项明确有系统级影响的实验。

## 测量 API

每个 APK 仅在用户按 **Run API measurements** 后发起一次受限测试序列，无后台自动采样、没有外部报告上传、没有应用遥测。包含：
- Framework: `LocationManager.getLastKnownLocation`、`getCurrentLocation`（取消/超时）与 `requestLocationUpdates`（获取一次后调用 `removeUpdates`）；
- Google Play Services: `FusedLocationProviderClient.getLastLocation`、`getCurrentLocation`；缺失 GMS 或无缓存可能返回 ERROR/ABSENT/TIMEOUT；
- Geocoder: API >=33 使用异步 `GeocodeListener`，低版本使用后台同步 API。只对**用户输入的测试目的地经纬度**进行地理编码，不自动获取本机实际坐标作为参数；
- Wi-Fi: `WifiManager.connectionInfo`、已缓存的 `scanResults`（不主动触发扫描）；
- Telephony: `TelephonyManager.networkOperator`、`allCellInfo`。

可见结果为 `MATCH_TARGET/DIFFERENT/PRESENT/ABSENT/DENIED/TIMEOUT/ERROR/UNSUPPORTED/NOT_RUN`，不是直接输出定位值或无线标识。运行时权限必须由使用者**明确点击**请求并在 Android 原生弹窗中决定；没获得权限不会代为授权，也不会声称受保护。

## 严格的基线 / 对照步骤

1. 用相同来源的构建包安装 4 个 APK。GeoShift 不启用任何目标 Profile；Strict Session 始终关闭。
2. 为每个测试包设置符合实验目的的原生位置权限（必要时包含附近设备/电话状态）。将三对照包保持在 LSPosed Scope 外；即使被要求也不要加进 Scope。
3. 在四个 Probe 中填写同一个 6–32 位 **Session ID** 和同一段仅供本次实验的 **16–128 字符随机 Secret**。该 Secret 不写入 JSON，务必保密，丢失则不能可靠比较新结果；不同实验请更换 Secret。为目标包填写 GeoShift 中将要设置的目的地经纬度；控制包可留空。
4. 选择 `baseline`，按 **Run API measurements**，每个 App 导出 1 份 JSON。目标基线结果不要求匹配尚未启用的模拟目的地。
5. **只**将 `io.geoshift.probe.target` 配成 GeoShift 目标应用，启用其普通按包 Profile，目标位置为前述目的地；检查全局 Strict Session 仍关闭。
6. 不移动测试设备、不更改四个 App 的原生权限，不更换 Secret，依次在四个 APK 选择 `active` 再测并导出 1 份 JSON。
7. 获得 **8 份** JSON，运行：
   ```bash
   python3 tools/verify_probe_reports.py -o probe-comparison.json \
     target-baseline.json target-active.json \
     control1-baseline.json control1-active.json \
     control2-baseline.json control2-active.json \
     control3-baseline.json control3-active.json
   ```
8. 把 `probe-comparison.json` 纳入设备验收报告。**PASS 只代表这 8 份非可信报告记录的局部可比 API 满足条件，不代表 Android 17/HyperOS 已通过全量隐私安全测试或 30 分钟持续定位稳定性。**

### 对照脚本判定

- 目标 App 在 active 中至少一个 Framework/Fused 位置方法明确报告与预设目的地接近（0.01° 比较阈值），才能具备局部 PASS 依据；任何明确的 `DIFFERENT` 为 FAIL。没有实际位置则 NOT_RUN。
- 三个非目标包**各至少有一个位置 API**在 baseline/active 两次测得可比较且相同的 HMAC 指纹，才具备“对照未被改变”的局部 PASS 依据；任一可比指纹不同则 FAIL；缺失、权限不足、超时、空值均为 NOT_RUN。
- 同一会话必须同时有四角色×两阶段、相同 SDK 版本/设备型号/源码提交；任何单包权限在两阶段发生变化则拒绝比较。JSON 中注入原始 `latitude`、`ssid`、`token` 等禁止字段将被拒绝；仍应人工确认报告真实性。
- HMAC 基于手动提供的临时密钥。定位指纹按约 0.01° 分桶；真实运动、粗定位系统随机化、信号波动可能产生假阳性/假阴性，**它不是精确轨迹，也不是可由报告直接反算的坐标**。秘钥泄露后隐私承诺不成立。
- 成功、失败、未运行必须独立表示；JSON 的 `method`、`expected`、`actual`、`status`、`sourceRevision`、`androidSdk`、`collectedAtUtc` 用于机器检查和归档。
- 用户导出 JSON 没有设备证明/签名，**可人为篡改或伪造**。脚本只校验输入和比较已观测值，不能作为平台合规证明或真实账户风控测试。

## 构建与测试证据

CI 新增：`:probe:testTargetDebugUnitTest`、`:probe:testControl1DebugUnitTest`、`:probe:testControl2DebugUnitTest`、`:probe:testControl3DebugUnitTest`；`:probe:assembleDebug` 产出四 APK；`python3 -m unittest discover -s tools/tests -v` 运行离线分析器单元测试。保留 GeoShift 原 App 既有 JVM 测试、Benchmark 编译与主 Debug APK 构建。

- **CI 代码/构建结果**：等待本轮 GitHub Actions 实际返回。无设备接入、尚未获取 8 份真实设备测量报告，所有真正的 G08 设备 API 隔离测试 = **NOT RUN**。
- G09 仍需在 Android 17/HyperOS 上实际安装全部四个变体，验证 SDK/LSPosed/GMS/权限、持续 30 分钟位置更新、停止/重启/取消和 3 个对照，并检查报告与 APK/源码 SHA 对应。
- 没有新的 VPN、代理、账号限制绕过或系统级 Hook；仅用于用户自己拥有并授权测试的设备、测试 App。
