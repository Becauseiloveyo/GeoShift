# G06 · 原生位置权限与证据分离

日期：2026-10-17 · 分支：`feat/v0.5-privacy-reliability`

## 目标和信任边界

GeoShift 的 Profile 评分仅说明**配置完整度**，并非定位防泄漏成功率。LSPosed / logcat 导入事件说明曾有 Hook 安装、命中或替换报告，可能是历史记录、旧进程或被人工编辑，不等于应用实际收到了替换值。

新增的权限状态是 **Android PackageManager 的只读快照**，不包括 AppOps、后台定位授予、一次性授权过期后的事件竞态、设备级位置开关、跨应用/网络侧地区信息、权限使用历史及真实位置 API 的最终返回值。

## 实现

- `NativeLocationPermission` 将系统包可见性、清单中声明的定位权限、当前细粒度/粗粒度授权分类为 `UNKNOWN` / `NOT_REQUESTED` / `DENIED` / `APPROXIMATE` / `PRECISE`。包不可见、查询异常不伪装成拒绝；只申明精确或粗略一种权限的应用也可分类。
- `AndroidLocationPermissionReader` 使用系统 PackageManager 读取目标应用声明和授权状态；不需要 GeoShift 自己申请位置权限，不修改其他应用权限、不调用特权 API。
- `MainActivity` 在 `onResume`、Profile 清单更新时刷新授权快照；点击系统设置按钮只打开 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`，由用户在 Android 设置中选择定位权限；若系统没有对应页面则显示失败提示。导航回 GeoShift 后再次刷新。
- Android 12 / API 31+ 可以在系统设置中将支持该权限的应用选择精确或大致位置；Android 10/11 不建议存在的精度切换。
- `PrivacyEvidence.summarize` 分别计算配置分数、历史 Hook 状态计数、原生权限快照以及 Probe 状态；G08 尚未存在可验证的独立 Probe，因此 **所有 Profile 的实际 Probe 结果固定为 `NOT_RUN`**，不因为 100/100 配置或日志 `REWRITTEN` 而变成 PASS。
- `DiagnosticsScreen` 逐 Profile 显示独立的四项信息，并提供官方设置入口；仍保留 G03 的逐接口历史事件详情。

## Android 用户操作

1. GeoShift → **Diagnostics** → 找到目标测试应用 → **Open Android app permissions**。
2. Android 官方应用信息 → **权限** → **位置**（是否存在由目标应用声明及 Android 版本决定）。
3. 应用不需要定位时优先 **不允许**；需要粗略位置且 Android 12+ 支持时可考虑 **大致位置**；仅必须使用精确位置时才授予 **精确位置**。
4. 返回 GeoShift，查看刷新的权限状态。权限改变可能重启目标应用；不能把日志中的旧状态当成新权限的实际行为。
5. 实际防泄漏效果必须另用 G08 自控 Probe 验证（独立接口、对照 App、失败/未运行明确记录）。

## 测试与无法推断的信息

- `NativeLocationPermissionTest`：包未知、未请求、拒绝、仅粗定位、精确授予优先及错误参数不产生精确结果。
- `PrivacyEvidenceSummaryTest`：配置再完整也不能得出 Probe PASS，历史 Hook 改写也不能变成实际 API PASS；其他包和身份未知的事件不记为目标成功。
- GitHub Actions 需要 JVM tests、Macrobenchmark 编译、Debug APK 编译和产物上传全部成功；CI 结果写入项目统一日志。
- **Android 17 / HyperOS 真实设置跳转、权限读取与切换、Hook 身份归因和回调交付仍为 NOT RUN（G08/G09）**；代码级通过不等于真机兼容。

## 官方设计依据

- https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- https://developer.android.com/codelabs/approximate-location
- https://developer.android.com/develop/sensors-and-location/location/testing

## 明确排除

G06 不引入新的系统权限、不自动授予/撤销任何第三方权限、不开发 VPN 加密或流量代理、不添加账号风控绕过、不修改 LSPosed 作用域、不以漂亮的分数宣称百分百保护。
