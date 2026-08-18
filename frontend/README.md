# 前端管理端

Vue 2.7.16 + Element UI 2.15.14 的社区治理管理端。请使用 Node 22（见 `.nvmrc`）；当前依赖树已通过 lint 和生产构建验证。

已接入的表单与动作包括：用户新增/编辑/启停/角色分配，社区与网格新增/编辑/启停/网格员分配，家庭户与居民新增/编辑/状态迁移，事件上报/受理/驳回/派发/撤销，以及独立任务创建/接单/提交复核/复核/取消。按钮按权限隐藏仅用于改善体验，所有授权与状态校验仍由后端负责。

## 本地启动

```bash
nvm use
npm ci
npm run lint
npm run build
npm run serve
```

Element UI 在 `src/main.js` 中按组件注册，并由 `babel-plugin-component` 按需引入样式；不要改回 `Vue.use(ElementUI)` 或全量主题 CSS。最近一次生产构建的 vendor JavaScript 为 792.24 KiB（gzip 219.09 KiB），仍有 Vue CLI 的体积提示，但已不再包含 Element UI 全量包。

前端开发端口默认为 `5173`，并把 `/api` 转发到 `http://localhost:8080`；可分别通过 `FRONTEND_PORT`、`DEV_API_TARGET` 覆盖。生产环境建议前后端同源部署。

认证使用服务端 Session Cookie。前端不保存访问令牌，Axios 开启 `withCredentials`，并使用 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` Header。登录和退出后都会重新获取 CSRF Token。

Vue 2 已停止长期演进。2026-07-31 审计显示生产依赖只有 3 个低等级告警、无中高危；完整开发依赖树还有 Vue CLI 5 / webpack 的 11 个中等级和 19 个高等级遗留告警。不要执行 `npm audit fix --force` 进行破坏性降级，长期消除这些告警需要单独规划 Vue 3 + Vite + Element Plus 迁移。
