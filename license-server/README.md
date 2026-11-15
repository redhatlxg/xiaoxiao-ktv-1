# 晓晓呀 KTV 独立授权服务器

独立的 Cloudflare Workers + D1 服务，不依赖 Android 工程。适合单独复制到新 Git 仓库并部署。

## 已实现的 API

- `GET /health` 健康检查
- `POST /v1/activate` 激活/续费：`{"code":"ABCDE-FG...","deviceId":"设备安装UUID"}`
- `POST /v1/validate` 在线验证设备授权
- `POST /v1/admin/codes` 管理员批量生成 1-100 个年度/永久码
- `GET /v1/admin/licenses?limit=100` 授权列表
- `POST /v1/admin/revoke` 冻结/撤销授权
- `POST /v1/admin/unbind` 管理员明确批准后解绑换机

年度码首次激活 365 天；年度续费从当前有效期末尾追加 365 天。永久授权无到期时间。永久授权优先于年度授权。数据库仅保存激活码 SHA-256 摘要，明文仅在生成时返回一次。

## 部署

1. 安装 Node.js 与 Wrangler：`npm install -g wrangler`
2. 登录：`wrangler login`
3. 创建 D1：`wrangler d1 create xiaoxiao-ktv-license`
4. 将返回的 database_id 填入 `wrangler.toml`
5. 初始化数据库：`wrangler d1 execute xiaoxiao-ktv-license --file=./schema.sql`
6. 设置两个强随机 Secret：`wrangler secret put ADMIN_SECRET` 和 `wrangler secret put LICENSE_SIGNING_SECRET`
7. 部署：`wrangler deploy`

## 安全说明

- 管理员 Secret 只存在服务端，不得写入 APK、网页前端或仓库。
- 签名令牌由 HMAC-SHA256 生成，客户端仍须通过内置公钥验签方案升级为非对称签名后再用于强抗篡改授权；当前 HMAC token 适合服务端接口返回，不应把密钥放进客户端。
- 当前限流基于 Worker 请求来源 IP 与 D1 窗口计数；正式大规模商用建议启用 Cloudflare WAF/Rate Limiting。
- 换机解绑接口要求管理员调用并显式传入 approved=true；应在受控管理环境调用。
- 管理接口使用单一管理员 Secret，暂未实现多管理员账号/RBAC/审计登录后台。
- 支付订单、自动支付回调、退款、发票和客服工单未包含在本服务中。
- 客户端尚未接入前，不可宣称激活收费功能已经上线。歌曲、伴奏、MV 商业版权需单独确认。

## 管理请求示例

请求头：`x-admin-secret: <你的服务端密钥>`。不要把密钥发给任何人或放进 APK。
