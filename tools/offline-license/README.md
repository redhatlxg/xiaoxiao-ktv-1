# 晓晓呀 KTV 离线一机一码授权

## 规则
- 永久授权、一次付费、完全离线。
- Android 使用 ANDROID_ID 作为设备标识；机器码只用于给卖家生成授权，不上传服务器。
- 注册机用 RSA-3072 / SHA-256 签名；APK 仅内置公钥验签，私钥不进入 APK。
- 授权码包含产品标识、设备标识、永久授权标志和签发编号；修改任何字段都会验签失败。

## 注册机使用
在卖家自己的 Windows 电脑执行：
```powershell
python -m pip install -r requirements.txt
python register_gui.py
```
也可以双击 `启动注册机.bat`。首次点击“首次生成密钥对”会生成 private_key.pem 和 public_key.b64；粘贴用户机器码后点击“生成永久授权码”，复制授权串发给该用户。命令行版本仍可用：`python register.py init` / `python register.py issue 机器码`。

**private_key.pem 绝不能提交 Git、发给客户或放进 APK。** 请离线加密备份；私钥丢失后无法签发兼容旧公钥的新授权码。将 public_key.b64 内容保存为 GitHub Actions Secret：`KTV_LICENSE_PUBLIC_KEY_B64`。正式版工作流会把它注入 APK；构建机不需要私钥。


## Android 手机版授权助手

独立 Android 工程位于 `mobile-issuer/`，安装后可在手机上离线签发，与 KTV APK 使用相同的 RSA-SHA256 格式和 payload 字段。

首次使用：
1. 在电脑注册机目录中找到 `private_key.pem` 和 `public_key.b64`。
2. 通过 USB、加密文件传输等方式把这两个文件传到自己的手机（不要发给客户）。
3. 打开“晓晓呀 KTV 授权助手”，分别导入私钥和公钥。应用会检查二者是否匹配。
4. 私钥会使用 Android Keystore 中的 AES-GCM 密钥加密后保存在手机应用私有数据中；签发不需要网络。
5. 将客户电视激活页上的机器码粘贴到输入框，生成、复制或分享授权码。

手机端只导入既有密钥，不自动另建密钥，确保和已经发布的 KTV APK、Windows 注册机保持兼容。若手机丢失、恢复出厂或卸载应用，Android Keystore 加密密钥可能无法恢复；务必保留电脑端原始私钥的离线加密备份。当前手机助手为独立 APK，不是 KTV 点歌端的一部分。
