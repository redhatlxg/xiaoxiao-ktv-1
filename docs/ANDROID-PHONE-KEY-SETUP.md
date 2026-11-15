# 用安卓手机生成晓晓呀 KTV 正式签名密钥

> 全程在自己的手机本地完成。不要把 keystore 文件、密码或 Base64 内容发给任何人，也不要提交到 Git。

## 1. 安装 Termux

建议从 F-Droid 官方渠道安装 Termux，不要使用来源不明的安装包。

## 2. 安装 Java 工具

打开 Termux，逐行执行：

```sh
pkg update
pkg install openjdk-17
```

## 3. 生成正式签名文件

```sh
mkdir -p ~/xiaoxiao-ktv-key
cd ~/xiaoxiao-ktv-key
keytool -genkeypair -v -keystore xiaoxiao-ktv-release.jks -alias xiaoxiao-ktv -keyalg RSA -keysize 3072 -validity 10000
```

按提示设置一个全新的强密码并妥善保存。别名固定填写 `xiaoxiao-ktv`。如果提示输入密钥密码，可以设置与密钥库密码相同的密码，方便后续填写。

## 4. 转换为 GitHub Secret 所需的 Base64

```sh
base64 xiaoxiao-ktv-release.jks | tr -d '\n' | termux-clipboard-set
```

剪贴板中的内容只用于 GitHub Secret，切勿粘贴到聊天、Issue、PR 或代码文件。

## 5. 在 GitHub 网页添加四项 Secret

打开仓库 Settings → Secrets and variables → Actions → New repository secret，分别添加：

| Name | Value |
|---|---|
| `KTV_KEYSTORE_BASE64` | 刚才复制到剪贴板的 Base64 内容 |
| `KTV_STORE_PASSWORD` | 生成密钥库时设置的密码 |
| `KTV_KEY_ALIAS` | `xiaoxiao-ktv` |
| `KTV_KEY_PASSWORD` | 生成密钥时设置的私钥密码 |

每项分别保存。不要把任何 Value 发给我。

## 6. 备份

把 `xiaoxiao-ktv-release.jks` 文件安全备份到你自己控制的两个位置，并单独保存密码。GitHub Secret 不是密钥文件的备份。丢失原始密钥后，后续版本可能无法保持相同签名身份。
