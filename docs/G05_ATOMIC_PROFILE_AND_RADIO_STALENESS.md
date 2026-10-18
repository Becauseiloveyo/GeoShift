# G05: 原子 Profile 与无线数据有效期

日期：2026-10-17 · 分支 `feat/v0.5-privacy-reliability` · 代码级验收阶段

## 目标与因果问题

原版 `GeoProfileSynchronizer` 更改 GPS/地区/时区但保留旧 BSSID、Wi-Fi 扫描结果、运营商与基站信息；一次同步可能向目标测试应用组合不同城市的数据。旧 Profile 还通过几十个 Remote Preferences 字段表示，无法直接保证某个读取时刻取得完整一致的地理及无线值。

G05 采取“**保留数据、停止使用旧数据、原子发布、拒绝旧版本覆盖**”方案。

## 数据模型

`GeoProfile` 增加：
- `revision`：完整 Profile 的单调提交版本（成功提交 +1；后台过期更新拒绝）。
- `geoRevision`：有意义的国家/地区/城市切换或 GPS 坐标移动约 1 km 时递增。
- `radioGeoRevision`：Wi-Fi/蜂窝身份绑定的地理版本。
- `radioStale`：地理环境发生变化后标记无线身份已过期；原始数据不丢失。
- `geoSource`：GeoIP 同步写 `geoip`，与已有 `radioSource` 分开。

`isRadioCurrent()` 同时要求 `!radioStale`、`radioGeoRevision == geoRevision`。不满足时 GeoShift App Hook 不再替换 SSID/BSSID、Wi-Fi 扫描、SIM/基站字段；这会**透传系统原始值**，不是能阻止系统值暴露的保证，诊断页必须显示无线保护不完整。应结合 Android 原生位置/附近设备权限。

## 原子发布协议

- `ProfileStoreV2.save` 使用 `ProfileRevisionPolicy.next`，对旧 `revision` 进行比较；写入一个完整 `atomic_snapshot_v5` JSON 与旧兼容键，单次 `SharedPreferences.Editor.commit()` 发布。**每个 Profile 原子**，不宣称多个 Profile 构成一个跨记录事务。
- `load` 首选快照，不从单独旧键拼合混合数据。快照若损坏/不合法则返回 null（不回退旧键）；调用方应显示缺失，禁止伪称防护正常。
- 没有 v5 快照的旧格式按原逻辑读取。旧版 Follow-VPN 且配置了无线身份的记录，因无法确定地理关联，迁移为 `radioStale=true`，字段保存以供人工确认。
- schemaVersion = 5；旧 JSON 仍可解析，新增元数据提供安全默认值。
- **特别限制**：CI 可以验证代码逻辑/编译，但 Remote Preferences 在 LSPosed 的真实 commit、通知可见性、异常断电一致性只能在 G09 实机证明，不可把编译通过当成跨进程持久化证明。

## 刷新和恢复

- `GeoProfileSynchronizer` 检查地理变化：国家/地区/城市变化、坐标显著变化会进入下一 `geoRevision`，标旧无线数据过期；仅出口 IP 改变或很小坐标漂移不自动使无线数据失效。
- 自动同步/人工同步共享同一个保存入口；请求过程中若用户手动修改了 Profile，旧结果 `revision` 不再匹配，会被拒绝，不覆盖新配置。
- 当第三方无线提供者成功返回新数据：只确认实际获取到的类别。如果旧无线数据已过期而本次只收到 Wi-Fi，则禁用旧蜂窝模拟；反之亦然。新结果绑定当前 `geoRevision`。
- 手动恢复：Profile 编辑页面显示旧无线数据警告，用户检查当前位置后点击 **Confirm radio matches current location**，明确确认并保存，解除 stale 标记。修改位置与确认旧无线数据同时发生时，地理变化优先使之过期，应在位置保存之后再确认。
- GeoIP 失败不执行 `save`，保留最后成功同步的 Profile；现有 UI/前台服务显示同步失败或未同步，不能误说本次刷新成功。24h 过期提示仍由 ProfileDiagnostics 输出。

## 回归测试与真实性限制

自动化：
- `GeoContextPolicyTest`：跨国/跨市过期、同城微小漂移保留、>1 km 过期、手动确认、仅 IP 更新保持 stale。
- `ProfileRevisionPolicyTest`：CAS 拒绝较旧更新、每次提交版本增长、手动无线重新绑定、超限拒绝。
- `GeoProfileSynchronizerTest`：GeoIP 地区切换时保留旧数据但标为 stale；查询失败无新 Profile。
- 原有 JVM suites、Benchmark 编译、Debug APK 构建、上传均不得删除或跳过。

待 G08/G09 在自控设备运行：真实 libxposed/Remote Preferences commit/readback、重启后快照一致性、断电/进程终止时原子性、目标与非目标应用无线 API 对照、混合 GeoIP 同步/用户编辑竞争、导入/导出 v1–v5、手动再确认、两个无线提供者分别成功/失败。

本任务不开发 VPN 加密、网络代理、Root 隐蔽、Play Integrity 或平台风控绕过，不保证 100% 隐私防泄漏或任何第三方账号的使用资格。
