# 论文 AI 风格风险分析前端

## 技术栈

- Vue 3
- Vite
- JavaScript
- Pinia
- Vue Router
- Element Plus
- ECharts
- Axios

## 已对接的后端接口

默认 API 地址：

```text
http://localhost:8080/api
```

如需修改，可创建 `.env.local`：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

## 登录隔离

前端新增登录页 `/login`，登录成功后保存 JWT 到 `localStorage`。所有上传、历史、报告、设置接口都会自动携带：

```http
Authorization: Bearer <token>
```

后端根据 token 中的用户 ID 查询数据，因此每个用户只能看到自己的论文、任务和报告。

## 启动

```bash
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```
