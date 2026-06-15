# v0.1.1 Plan

## 架构概览
改动集中在后端，前端改动较小。三个新增组件：
- **SessionManager**：Session 的创建、查询、切换，纯内存实现。
- **MessageRepository**：JPA Repository，负责 ChatMessage 实体的 CRUD，支持按 session_id 分页查询。
- **LlmService 改造**：chat() 方法先查历史消息，拼成 messages 数组后发给 LLM。历史消息数量和轮数通过 application.yml 配置。

## 核心数据结构
### ChatMessage
- id: Long（主键，自增）
- sessionId: String（会话 ID，UUID）
- role: String（"user" 或 "assistant"）
- content: String（消息内容）
- createdAt: LocalDateTime（创建时间）

### SessionInfo
- sessionId: String（UUID）
- username: String（用户标识）
- createdAt: LocalDateTime

### ChatRequest（改造）
- message: String
- sessionId: String（新增）

### 配置项（application.yml）
- pingpong.chat.history-limit: 20
- pingpong.chat.context-rounds: 10

## 模块设计
### SessionManager
- 职责：Session 创建与管理
- 接口：createSession(String username) → SessionInfo；getSession(String sessionId) → SessionInfo
- 依赖：无

### MessageRepository
- 职责：消息持久化，Spring Data JPA
- 接口：findBySessionIdOrderByCreatedAtAsc(sessionId, limit)；save(message)；findBySessionIdOrderByCreatedAtAsc(sessionId)
- 依赖：Spring Data JPA, MySQL 驱动

### LlmService（改造）
- 职责：LLM 调用，构建请求体时先查历史消息
- 接口：chat(String message, String sessionId) → Flux<String>
- 依赖：MessageRepository, LlmConfig

### ChatController（改造）
- 职责：HTTP 接口，接收 sessionId，存消息到 DB
- 接口：chat(ChatRequest) → SseEmitter（改造）；GET /api/chat/history → List<ChatMessage>（新增）
- 依赖：SessionManager, MessageRepository, LlmService

### 新增接口：获取历史消息
- 接口：GET /api/chat/history?sessionId=xxx&limit=20
- 依赖：MessageRepository

## 模块交互
### 消息发送流程
前端 fetch POST /api/chat { message, sessionId }
→ ChatController.chat()
→ 校验 sessionId → 保存用户消息
→ LlmService.chat(message, sessionId)
→ 查询历史 → 取最近 N 轮拼入 messages
→ WebClient POST LLM API (stream)
→ 接收 SSE chunk → 保存 AI 回复
→ SseEmitter 推送 → 前端

### 历史消息加载流程
前端 GET /api/chat/history?sessionId=xxx&limit=20
→ MessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, limit)
→ 返回 List<ChatMessage> → 前端渲染

### 新建对话流程
前端生成新 UUID → localStorage 存 sessionId → 清空 messages

## 文件组织
backend/
├── pom.xml                    ← 新增 jpa, mysql 依赖
├── src/main/resources/
│   ├── application.yml        ← 新增 pingpong.chat 配置
│   └── .env.example
└── src/main/java/com/pingpongsmt/
    ├── config/
    │   ├── JpaConfig.java     ← 新增
    │   ├── EnvLoader.java
    │   └── LlmConfig.java
    ├── controller/
    │   └── ChatController.java ← 改造
    ├── dto/
    │   ├── ChatRequest.java   ← 改造
    │   ├── ChatResponse.java  ← 新增
    │   └── SseMessage.java
    ├── entity/
    │   └── ChatMessage.java   ← 新增
    ├── repository/
    │   └── ChatMessageRepository.java ← 新增
    └── service/
        ├── LlmService.java    ← 改造
        └── SessionManager.java ← 新增

frontend/
├── package.json               ← 版本升级
├── src/
│   ├── App.vue                ← 改造
│   └── views/
│       └── ChatView.vue       ← 改造

## 技术决策
| 决策点 | 选择 | 理由 |
|--------|------|------|
| ORM | Spring Data JPA | 与 Spring Boot 生态集成良好 |
| 数据库 | MySQL（本地） | 用户本地已有 MySQL |
| 主键策略 | 消息自增 Long，Session UUID | 消息表自增性能好；Session UUID 全局唯一 |
| 历史查询 | JPA @Query 带 LIMIT | 避免全表扫描 |
| 上下文轮数配置 | application.yml | 配置化支持热更新 |
| Session 存储 | 前端 localStorage + 后端查表 | localStorage 刷新不丢；后端存表消息不丢 |
| 消息角色 | "user" / "assistant" | 与 LLM API 标准格式一致 |
