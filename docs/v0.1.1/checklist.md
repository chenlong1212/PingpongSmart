# v0.1.1 Checklist

> 每一项通过运行代码或观察行为来验证，聚焦系统行为。

## 实现完整性
- [ ] ChatMessage 实体已实现且可被调用（验证：编译通过）
- [ ] ChatMessageRepository 已实现且可被调用（验证：编译通过）
- [ ] SessionManager 已实现且可被调用（验证：编译通过）
- [ ] ChatHistoryResponse DTO 已实现（验证：编译通过）
- [ ] ChatRequest 新增 sessionId 字段（验证：编译通过）
- [ ] LlmService 改造后支持上下文注入（验证：编译通过）
- [ ] ChatController 改造后支持持久化和 history 接口（验证：编译通过）

## 集成
- [ ] 消息发送流程完整：用户消息 → 存 DB → 查历史 → 拼上下文 → 调 LLM → 存 AI 回复（验证：发送消息后检查 MySQL 表中有两条记录）
- [ ] 历史消息接口正确返回数据（验证：直接调 GET /api/chat/history?sessionId=xxx&limit=20，返回正确数量的消息）
- [ ] 前端 ChatView 正确调用 history 接口（验证：刷新页面后聊天记录正确展示）
- [ ] 前端 ChatView 正确发送 sessionId 到后端（验证：发送消息时 inspect network request，body 中包含 sessionId）

## 编译与测试
- [ ] 项目编译无错误（验证：`mvn compile` 在 backend 目录下成功）
- [ ] 前端构建无错误（验证：`cd frontend && npm run build` 成功）
- [ ] 后端启动无报错（验证：`mvn spring-boot:run` 启动后无异常日志）

## 端到端场景
- [ ] **场景 1：刷新不丢消息** — 发送一条消息 → 刷新页面 → 消息仍在聊天界面展示
- [ ] **场景 2：多轮对话上下文** — 发送"我叫张三，我喜欢打乒乓球" → 发送"你记得我叫什么吗？" → LLM 回答中包含"张三"
- [ ] **场景 3：新建对话** — 在一个 session 中发送 3 条消息 → 点击"新建对话" → 聊天界面清空 → 发送新消息 → LLM 回答不包含旧上下文
- [ ] **场景 4：用户标识** — 首次进入输入用户名"测试用户" → 刷新页面 → 用户名仍然显示
- [ ] **场景 5：历史加载上限** — 发送 25 条消息（模拟）→ 刷新页面 → 只显示最近 20 条
