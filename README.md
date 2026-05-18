
## 文析智审 Agent - 论文分析网站

项目简介：
文析智审 Agent 是一个面向论文写作场景的智能分析网站，支持用户上传论文文件或论文截图，系统能够自动解析论文内容，识别句子级 AI 风格风险，并生成风险报告、修改建议和学术化改写语句。项目通过 Java 后端管理用户、任务、报告和历史记录，结合 Python Agent 调度 RAG 检索、风险识别、改写生成和图片分析工具，实现论文分析与写作辅助。
<img width="1919" height="1014" alt="f703caaf29c72d25d15712dd9d7f2b7b" src="https://github.com/user-attachments/assets/49ebbd71-a929-45e7-8db8-236c6924a604" />
<img width="1900" height="1016" alt="60faa167e8dd670445c9259cda8e6b66" src="https://github.com/user-attachments/assets/42221d8a-0abb-4c23-b1ba-fd6207338423" />
<img width="1899" height="997" alt="7f604c20dea02255ff0f37bc9f4ecf89" src="https://github.com/user-attachments/assets/a062f03b-9127-4037-acdd-5e90fed97eb1" />
<img width="1919" height="1024" alt="c15ec21a60df1092999c7118b8436cec" src="https://github.com/user-attachments/assets/0926ce64-c44a-4619-b2d9-6df7bb84dad6" />
<img width="1919" height="1020" alt="3040f94bb90420b4793e306ce297416b" src="https://github.com/user-attachments/assets/83ecc6d8-9e0f-4d48-bd66-69271d07c2d5" />

技术栈：
Vue 3、Element Plus、Axios、Spring Boot 3、MySQL、Redis、RabbitMQ、Redisson、Python、FastAPI、Agent、Tool Calling、RAG、向量记忆

核心功能：
● 用户登录注册：支持用户账号注册、登录和退出，登录后可保存个人模型配置、查看历史报告和使用论文助手。

● 论文上传分析：支持上传 Word、PDF、TXT、PNG、JPG 等文件，后端创建分析任务，Python AI 服务解析论文内容并生成分析结果。

● 异步任务处理：上传论文后立即返回任务 ID，前端展示任务进度，后端通过 RabbitMQ 异步调用 Python Agent 分析论文，避免长时间阻塞等待。

● 句子级风险报告：系统按段落和句子拆分论文内容，对每个句子生成 AI 风格风险分数、风险等级、风险原因和优化建议。

● RAG 知识增强：Python AI 服务结合论文写作规范、用户历史报告和聊天内容进行检索增强，使生成建议更贴合论文写作场景。

● Agent 工具调度：通过 PaperAnalysisAgent 调度文档解析、句子切分、RAG 检索、风险识别、改写生成和图片分析工具，实现多工具协同分析。

● 论文助手：提供面向论文修改、语句润色、报告解读的 AI 对话页面，支持图片上传分析，并保存用户会话历史。

● 个人模型配置：支持用户配置 Base URL、API Key 和模型名称，Python AI 服务按用户配置动态调用不同大模型。

● 历史报告管理：支持查看历史分析记录和报告详情，也可删除不需要的报告，方便用户管理分析结果。

项目亮点：
● 采用 Java 后端 + Python AI 服务分层架构，Java 负责业务流程和数据管理，Python 负责 AI 分析与工具调用，实现业务与 AI 能力解耦。

● 使用 RabbitMQ + Redisson 实现论文分析任务异步调度和用户级并发控制，上传接口可快速返回任务 ID，并限制单用户最多 3 个任务并行。

● 构建单智能体 Agent 工作流，将论文解析、RAG 检索、风险识别、改写建议和图片分析封装为工具，提高论文分析流程的扩展性。

● 引入向量记忆机制，将用户历史报告、AI 对话和图片分析结果写入向量库，使后续回答能够结合用户历史上下文。

● 前端实现上传分析、进度轮询、报告展示、论文助手逐字输出和模型配置页面，提升用户交互体验。
