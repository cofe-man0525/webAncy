# 前端接口调用层

接口封装文件：

```text
src/services/analysisApi.js
```

默认后端地址：

```text
http://localhost:8080/api
```

## 鉴权

登录成功后，`authStore` 会把 token 保存到 `localStorage`。  
`analysisApi.js` 的 Axios 拦截器会自动携带：

```http
Authorization: Bearer <token>
```

## 当前接口

```js
login(payload)
register(payload)
getMe()

uploadPaper(formData)

getTaskProgress(taskId)
getReport(taskId)
regenerateSuggestion(payload)

getHistoryTasks()

getUserSettings()
updateUserSettings(payload)
```

## 页面对应关系

| 页面 | 调用接口 |
|---|---|
| 登录页 | `login`, `register` |
| 上传论文页 | `uploadPaper` |
| 分析进度页 | `getTaskProgress` |
| 报告详情页 | `getReport`, `regenerateSuggestion` |
| 历史记录页 | `getHistoryTasks` |
| 个人设置页 | `getUserSettings`, `updateUserSettings` |
