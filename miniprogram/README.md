# WeChat Mini Program Frontend

目录 `miniprogram/` 是原生微信小程序工程，可直接用微信开发者工具导入。

## 已包含页面

- `pages/login`：仅微信登录，作为小程序唯一入口
- `pages/chat`：AI 助手主界面，包含聊天、历史会话、退出登录

## 后端接口

默认请求地址配置在 `utils/config.js`：

```js
const BASE_URL = "https://api.ndakjin.asia";
```

首次导入项目时，请先复制 `utils/config.example.js` 为 `utils/config.js`，再填写真实后端地址。

当前对接接口：

- `POST /api/auth/wx/login`
- `POST /api/auth/logout`
- `GET /api/agent/sessions`
- `GET /api/agent/sessions/{sessionId}`
- `POST /api/agent/chat`

## 导入方式

1. 打开微信开发者工具。
2. 选择“导入项目”。
3. 项目目录选择 `D:\travel-agent\miniprogram`。
4. 复制 `project.config.example.json` 为 `project.config.json`，再填写真实小程序 AppID。

## 注意

- 当前小程序前端已经移除账号密码登录，只保留微信登录入口；未登录不能进入聊天页。
- 登录成功后会直接进入 AI 助手聊天界面，不再经过首页。
- `127.0.0.1` 仅适合开发者工具本地联调。真机调试时需要改成可访问的内网或公网地址，并配置小程序合法域名。
- 当前已改为标准小程序登录流：前端发送 `wx.login()` 返回的 `code`，后端调用微信 `code2Session` 获取真实 `openid`。
- 你需要在后端 `application.yml` 里配置 `travel-agent.auth.wx.app-id` 和 `travel-agent.auth.wx.secret`，并确保它们与开发者工具里使用的 `AppID` 属于同一个小程序。
