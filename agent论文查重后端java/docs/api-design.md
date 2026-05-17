# 论文 AI 风格风险分析系统接口设计

本文档根据前端项目 `D:/桌面文件/agent查重论文网站` 的 `src/services/analysisApi.js`，以及 Java 后端项目 `D:/桌面文件/agent论文查重后端java` 的 Controller 层整理。

## 1. 基础约定

后端基础地址：

```text
http://localhost:8080/api
```

前端默认配置位置：

```text
D:/桌面文件/agent查重论文网站/src/services/analysisApi.js
```

如需修改前端请求地址，可以在前端项目根目录创建：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

统一响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

错误响应示例：

```json
{
  "code": -1,
  "message": "任务不存在或无权访问",
  "data": null
}
```

除注册、登录外，其余接口都需要请求头：

```http
Authorization: Bearer <token>
```

用户数据隔离规则：

```text
前端不传 userId
后端从 JWT 中解析当前用户 ID
所有论文、任务、报告、历史记录、个人设置都按当前 userId 查询
```

## 2. 前端接口调用映射

| 前端函数 | 请求方法 | 后端接口 | 后端 Controller |
|---|---:|---|---|
| `login(payload)` | POST | `/auth/login` | `AuthController` |
| `register(payload)` | POST | `/auth/register` | `AuthController` |
| `getMe()` | GET | `/auth/me` | `AuthController` |
| `uploadPaper(formData)` | POST | `/papers/upload` | `PaperController` |
| `getTaskProgress(taskId)` | GET | `/analysis/tasks/{taskId}/progress` | `AnalysisController` |
| `getReport(taskId)` | GET | `/analysis/reports/{taskId}` | `AnalysisController` |
| `regenerateSuggestion(payload)` | POST | `/analysis/suggestions/regenerate` | `AnalysisController` |
| `getHistoryTasks()` | GET | `/history/tasks` | `HistoryController` |
| `getUserSettings()` | GET | `/user/settings` | `UserSettingController` |
| `updateUserSettings(payload)` | PUT | `/user/settings` | `UserSettingController` |

## 3. 用户注册

```http
POST /auth/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "test001",
  "password": "123456",
  "nickname": "测试用户"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| username | string | 是 | 用户名，3 到 32 位 |
| password | string | 是 | 密码，6 到 64 位 |
| nickname | string | 否 | 昵称，不传则默认等于用户名 |

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 10001,
      "username": "test001",
      "nickname": "测试用户",
      "role": "USER"
    }
  }
}
```

后端处理：

```text
1. 校验用户名是否重复
2. BCrypt 加密密码
3. 插入 users
4. 初始化 user_settings
5. 返回 JWT
```

## 4. 用户登录

```http
POST /auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "test001",
  "password": "123456"
}
```

响应同注册接口。

前端处理：

```text
1. 保存 token 到 localStorage
2. 保存 user 到 localStorage
3. 后续请求自动携带 Authorization
```

## 5. 当前用户

```http
GET /auth/me
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 10001,
    "username": "test001",
    "nickname": "测试用户",
    "role": "USER"
  }
}
```

## 6. 上传论文并创建分析任务

```http
POST /papers/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---|---|
| file | file | 是 | 无 | 论文文件或图片 |
| depth | string | 否 | `standard` | `fast` / `standard` / `deep` |
| style | string | 否 | `academic` | `academic` / `natural` / `concise` |
| enableRag | boolean | 否 | `true` | 是否启用 RAG |
| suggestionCount | int | 否 | `2` | 每个句子生成几个推荐语句 |

支持文件类型：

```text
doc
docx
pdf
txt
png
jpg
jpeg
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "taskId": 40001,
    "status": "queued"
  }
}
```

后端处理：

```text
1. 从 JWT 获取当前 userId
2. 校验当前用户处理中任务数量，最多 3 个 queued/processing
3. 保存文件到本地 storage
4. 写入 papers 表
5. 写入 analysis_tasks 表
6. 如果 app.rabbit.enabled=false，使用 Java MockAnalysisService 生成模拟报告
7. 如果 app.rabbit.enabled=true，发送 JSON 消息到 RabbitMQ，由 Python AI 服务处理
```

## 7. 查询任务进度

```http
GET /analysis/tasks/{taskId}/progress
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "taskId": 40001,
    "status": "processing",
    "progress": 72,
    "errorMessage": null
  }
}
```

任务状态：

| 状态 | 说明 |
|---|---|
| queued | 已创建，等待分析 |
| processing | 分析中 |
| done | 分析完成 |
| failed | 分析失败 |

前端使用场景：

```text
进度页每隔一段时间轮询该接口。
当 status=done 时跳转或允许用户进入报告页。
```

## 8. 查询分析报告

```http
GET /analysis/reports/{taskId}
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "taskId": 40001,
    "title": "人工智能技术在高校教学评价中的应用研究",
    "overallRiskScore": 68,
    "summary": "文章整体结构完整，但部分段落存在概括性强、句式模板化、缺少具体研究对象和证据支撑的问题。",
    "agentTrace": [
      {
        "name": "文档解析工具",
        "status": "done",
        "detail": "已解析正文、标题、摘要与图片文字入口"
      },
      {
        "name": "RAG 检索工具",
        "status": "done",
        "detail": "已检索学术写作规范和领域表达参考"
      }
    ],
    "paragraphs": [
      {
        "id": "p1",
        "index": 1,
        "riskScore": 88,
        "text": "随着人工智能技术的不断发展，其在教育领域中的应用越来越广泛。本文旨在探讨人工智能技术在高校教学评价中的应用及其影响。",
        "sentences": [
          {
            "id": 60001,
            "text": "随着人工智能技术的不断发展，其在教育领域中的应用越来越广泛。",
            "riskScore": 76,
            "riskLevel": "high",
            "reasons": [
              "常见开头表达，概括性较强",
              "缺少具体教育场景或研究对象"
            ],
            "advice": "建议把笼统背景改成具体研究场景，并说明文章关注的评价环节。",
            "suggestedTexts": [
              "近年来，高校在课程评价、学习过程记录和教学反馈中逐步引入智能分析工具，使教学评价从期末结果判断转向过程性诊断。"
            ],
            "ragReferences": [
              "引言部分应明确研究对象、问题范围与具体应用场景。"
            ]
          }
        ]
      }
    ]
  }
}
```

前端展示逻辑：

```text
overallRiskScore → 顶部风险仪表盘
paragraphs → 左侧论文原文高亮
sentences.reasons → 风险原因
sentences.advice → 修改方向
sentences.suggestedTexts → 可复制推荐语句
agentTrace → Agent 工具调用过程
```

## 9. 重新生成推荐语句

```http
POST /analysis/suggestions/regenerate
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "sentenceId": 60001,
  "style": "academic",
  "suggestionCount": 2
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| sentenceId | long | 是 | 句子分析结果 ID |
| style | string | 否 | 推荐语句风格 |
| suggestionCount | int | 否 | 生成数量，建议 1 到 5 |

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sentenceId": 60001,
    "suggestedTexts": [
      "新的推荐语句 1",
      "新的推荐语句 2"
    ]
  }
}
```

后端权限：

```text
根据 sentenceId 找到 taskId
再判断 task.userId 是否等于当前登录用户
不允许跨用户重新生成
```

## 10. 历史记录

```http
GET /history/tasks
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 40001,
      "title": "人工智能技术在高校教学评价中的应用研究.docx",
      "createdAt": "2026/5/14 16:00:00",
      "status": "done",
      "score": 68
    }
  ]
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| id | 任务 ID，对应前端进入报告页的 `taskId` |
| title | 文件名 |
| createdAt | 创建时间 |
| status | 任务状态 |
| score | 总体风险分数，报告未生成时为 `null` |

## 11. 查询用户设置

```http
GET /user/settings
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "defaultStyle": "academic",
    "enableRag": true,
    "highRiskThreshold": 75,
    "suggestionCount": 2
  }
}
```

## 12. 更新用户设置

```http
PUT /user/settings
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "defaultStyle": "academic",
  "enableRag": true,
  "highRiskThreshold": 75,
  "suggestionCount": 2
}
```

响应同查询用户设置。

## 13. Java 与 Python AI 服务对接

Java 后端配置：

```yaml
app:
  rabbit:
    enabled: true
    exchange: papercheck.analysis.exchange
    routing-key: papercheck.analysis.created
```

RabbitMQ：

```text
exchange: papercheck.analysis.exchange
queue: papercheck.analysis.queue
routingKey: papercheck.analysis.created
```

Java 投递给 Python 的消息：

```json
{
  "taskId": 40001,
  "userId": 10001,
  "paperId": 30001,
  "storagePath": "./data/uploads/10001/2026-05-14/demo.docx",
  "depth": "standard",
  "style": "academic",
  "enableRag": true,
  "suggestionCount": 2
}
```

Python AI 服务处理流程：

```text
1. 消费 RabbitMQ 消息
2. 更新 analysis_tasks.status=processing
3. 读取 papers.storage_path 对应文件
4. 文档解析：Word/PDF/TXT/图片 OCR
5. 句子切分
6. RAG 检索写作规范
7. AI/规则分析句子风险
8. 生成推荐语句
9. 写入 analysis_reports
10. 写入 analysis_sentences
11. 更新 analysis_tasks.status=done, progress=100
```

Python 写回的表：

```text
analysis_reports
analysis_sentences
analysis_tasks
```

## 14. 前端页面与接口关系

| 页面 | 路由 | 使用接口 |
|---|---|---|
| 登录页 | `/login` | `/auth/login`, `/auth/register` |
| 上传论文页 | `/` | `/papers/upload` |
| 进度页 | `/progress/:taskId` | `/analysis/tasks/{taskId}/progress` |
| 报告页 | `/report/:taskId` | `/analysis/reports/{taskId}`, `/analysis/suggestions/regenerate` |
| 历史记录页 | `/history` | `/history/tasks` |
| 个人设置页 | `/settings` | `/user/settings` |

## 15. 测试账号建议

推荐使用后端注册接口创建账号，因为密码会自动 BCrypt 加密：

```http
POST /auth/register
```

```json
{
  "username": "test001",
  "password": "123456",
  "nickname": "测试用户"
}
```

如果你手动插入 `users`，需要保证 `password_hash` 是后端 BCrypt 能识别的密文。

## 16. 当前仍可优化的接口

后续可以继续补：

| 接口 | 作用 |
|---|---|
| `DELETE /history/tasks/{taskId}` | 删除历史任务 |
| `GET /analysis/reports/{taskId}/export` | 导出 PDF/Word 报告 |
| `POST /analysis/tasks/{taskId}/cancel` | 取消分析任务 |
| `GET /papers/{paperId}/preview` | 论文原文预览 |
| `POST /analysis/suggestions/apply` | 将推荐语句应用到修改稿 |
