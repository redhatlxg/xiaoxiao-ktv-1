# 正式签名、发布与安全检查

## 发布方式

正式版由 GitHub Actions 构建，必须有签名密钥。推送 `v*` 标签后自动运行 `.github/workflows/android-release.yml`，通过 Release Lint、APK 签名校验和非 Debuggable 检查后，把 APK 与 SHA-256 文件上传到 GitHub Releases。Release 附件不会像 Actions 临时 artifact 那样按 14 天自动过期；仓库所有者仍可自行删除。

## 必须配置的 GitHub Actions Secrets

在仓库 Settings → Secrets and variables → Actions → New repository secret 添加：

- `KTV_KEYSTORE_BASE64`：签名 JKS/PKCS12 文件的 Base64 内容
- `KTV_STORE_PASSWORD`：密钥库密码
- `KTV_KEY_ALIAS`：密钥别名
- `KTV_KEY_PASSWORD`：私钥密码
- `KTV_LICENSE_PUBLIC_KEY_B64`：离线注册机生成的 `public_key.b64` 文件内容（只放公钥，不是私钥）

**不要把密钥文件、密码或 Base64 内容提交到 Git。不要把密钥发到聊天中。** 建议把原始 keystore 离线备份到两个安全位置，密码使用密码管理器保存。GitHub Secret 只用于自动构建，不是密钥的唯一备份。

## Windows PowerShell 生成一次性发布密钥

在自己的安全电脑上运行（请把示例密码替换为独立强密码；命令行历史也可能保存输入，建议在安全环境操作）：

```powershell
keytool -genkeypair -v -keystore .\xiaoxiao-ktv-release.jks -alias xiaoxiao-ktv -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=晓晓呀, OU=Android, O=晓晓呀, C=CN"
$bytes = [IO.File]::ReadAllBytes(".\xiaoxiao-ktv-release.jks")
[Convert]::ToBase64String($bytes) | Set-Clipboard
```

将剪贴板内容作为 `KTV_KEYSTORE_BASE64` Secret；其余三项分别填写生成时的密钥库密码、别名 `xiaoxiao-ktv` 和私钥密码。不要将 JKS 上传到仓库。

## 离线一机一码授权密钥

在 `tools/offline-license` 目录运行 `启动注册机.bat`，首次点击“首次生成密钥对”。将生成的 `public_key.b64` 全部内容配置为 `KTV_LICENSE_PUBLIC_KEY_B64` Secret；`private_key.pem` 只留在发行方电脑并做离线加密备份，绝不能提交 Git 或注入 APK。后续签发授权只需把设备激活页显示的机器码粘贴到注册机。切勿重新生成密钥，否则旧 APK 的公钥无法验证新密钥签发的授权。

## 发布

Secrets 配置好后，在仓库 Actions → Signed Android Release → Run workflow，输入一个已存在的版本标签（例如 `v1.6.3`）；或者推送新标签触发自动发布。版本标签应对应已审查的源码提交，不要重复使用已发布标签。

## 关于杀毒误报

本项目不使用关闭杀毒、隐藏行为、加壳混淆来规避检测。正式发布前执行 Android Release Lint、签名验证和 Debuggable 检查。安全软件误报仍可能发生，尤其是含有网络下载、媒体文件处理、局域网服务、开机启动等功能的应用。需要用具体 APK 的 SHA-256、检测厂商名称、检测名称和检测日期向厂商提交误报复核；不能保证所有厂商或所有版本都给出零告警。

本仓库的歌曲/媒体源和第三方曲库服务不由 Android 签名流程控制。请勿将来源不明的 APK、热更新脚本或证书加入白名单。
