# G07 — Provider 凭据加密、迁移与安全导出

日期：2026-10-18；分支：`feat/v0.5-privacy-reliability`

## 威胁模型与边界

本工作包保护 GeoShift 自身保存的 OpenCellID API key、WiGLE token name/token，避免在 SharedPreferences 中继续明文存储。采用 Android 原生 `AndroidKeyStore` 管理的 AES-256-GCM 密钥、平台自动生成的随机 96-bit IV、128-bit GCM tag，以及版本化 AAD。系统只在应用进程中按需解密；加密密钥不写入数据库、Preferences、Git 或 Profile JSON。

**不承诺** Root 或被侵入的应用运行时内存不可读取、平台硬件一定安全、服务器端不留请求记录。用户名/token 仍在用户主动编辑时短暂存在于界面进程内存；当前 Provider 请求仍会将凭据发送给指定 HTTPS API（OpenCellID 查询键、WiGLE Authorization），这是功能所需。

参考 Android 官方 KeyGenParameterSpec: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec 。

## 保存协议

- `ProviderCredentialEngine`：纯 JVM、可替换存储和加密依赖，控制状态迁移；`AndroidProviderCredentialCipher`：Keystore 实现，`AES/GCM/NoPadding`、AES-256、AAD `GeoShift/provider-credentials/v1`。
- `ProviderSettings`：只在本 App 私有 `provider_settings` 中存储 `sealed_credentials_v1`，一次 `SharedPreferences.Editor.commit()` 同步写入密文，并在**同一事务**清除 `opencellid_api_key`、`wigle_token_name`、`wigle_token` 三项旧明文字段。清理失败不报告加密成功。
- 首次加载若不存在密文而有旧明文：先在内存完成加密，再同步提交密文+删除旧字段；失败不销毁原明文，不启用 Provider；下次可重试。原值仍可能留在旧设备存储痕迹、文件系统备份或闪存历史块中，**不保证物理擦除**。
- 如果检测到已有密文但解密失败、密钥丢失、GCM 验签失败、记录损坏：`RECOVERY_REQUIRED`，**不读取旧明文作为后备，也不静默覆盖现有密文**。
- 已有密文同时仍有旧键且无法清理：`CLEANUP_FAILED`，不交付凭据给 Provider；重试清理或提示恢复。
- App 设置页显示 EMPTY / READY / MIGRATION_FAILED / RECOVERY_REQUIRED / CLEANUP_FAILED，不显示加密错误堆栈或提供者返回的任意原文。
- 只有异常状态下才允许通过二次确认执行 **Reset inaccessible provider credentials**：该操作清除所有已存储凭据（包括遗留明文），尝试删除 Keystore alias；用户必须自己重新输入，不可逆。若清除过程失败，显示错误且绝不自动回退明文。
- 应用安装清单已设置 `android:allowBackup="false"`。真实系统备份/恢复边界仍需设备验证。

## 其他隐私与导出

- `ProviderSettings` 仅在 App 私有空间使用，凭据**不写 ProfileCodec v5 原子快照**。
- Profile `Export` 不直接导出内部完整快照：先经 `ProfileExportPolicy` 默认移除公共出口 IP、同步时间及地区元数据、SSID/BSSID/扫描列表、运营商/蜂窝身份及来源，并关闭无线模拟项；真实手机的 GPS 硬件读取从未被加入这一导出路径。
- 导出的 **用户指定/GeoIP 衍生的目标模拟经纬度仍然保留**，用于后续导入恢复模拟目的地；它们仍可能是敏感位置。界面会要求谨慎分享；此功能绝不声称“导出完全无坐标”。
- `ProfileCodec` 本身仍用于私有 v5 原子持久化与导入兼容，不把导出脱敏变成内部数据丢失。
- OpenCellID/WiGLE API 服务端 JSON 错误文本不再原样进入 UI，网络查询异常不再显示原始 throwable.message，以避免 URL、认证参数或异常中的敏感内容传播。
- 不修改 Root/LSPosed 隐藏、VPN 网络加密、平台风控绕过等功能。

## 自动化测试

- `ProviderCredentialEngineTest`：数据格式、旧版迁移、commit 失败保留明文但禁用、密文损坏不回退、密钥失效、旧明文清理失败 fail closed、覆盖写入时清除明文。
- `ProfileExportPolicyTest`：导出时剔除 IP、无线识别符，不改原始 Profile，保留经用户配置的目标位置。
- GitHub Actions：JVM 测试、Macrobenchmark **编译**、Debug APK 构建、产物上传。没有 Android Keystore 运行时的本地/云端真机验收；G08/G09 前不能宣称加密在 HyperOS 实际硬件上已验证。

## 设备验收清单（G09）

1. 在旧版装入测试凭据后升级，检查新密文可读取且三个明文字段被清除；先验证不含凭据的公开日志与导出。
2. 模拟迁移保存失败、旧字段清理失败以及密文篡改，确保不继续请求 Provider、不删除恢复所需原数据。
3. 模拟密钥丢失/数据恢复，确认 RECOVERY_REQUIRED、二次确认重置、重新配置后可工作。
4. 前后台、重启、升级、停机、账号切换验证密钥持久性及失败呈现。
5. 不使用真实生产凭据或第三方平台账号进行攻击/风控规避测试。
