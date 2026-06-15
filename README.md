# PingpongSmart

面向乒乓球用户的私有知识管理与智能问答平台。

## 版本

- **v0.1.1**（当前）— 聊天记录持久化 + 多轮对话上下文
- v0.1.0 — 最小可运行版本，跑通核心链路

## 快速开始

### 前置条件

- Java 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

### 1. 安装并配置 MySQL

```powershell
# 使用 Chocolatey 安装（如未安装）
choco install mysql -y

# 创建数据库
mysql -u root -e "CREATE DATABASE pingpongsmt DEFAULT CHARACTER SET utf8mb4;"
```

### 2. 配置 API Key

```powershell
Copy-Item backend/src/main/resources/.env.example backend/src/main/resources/.env
```

编辑 `backend/src/main/resources/.env`，填写你的 API Key：

```
LLM_BASE_URL=https://maas-api.cn-huabei-1.xf-yun.com/v2
LLM_API_KEY=你的-API-Key
LLM_MODEL=xopqwen36v35b
```

编辑 `backend/src/main/resources/application.yml`，确认数据库连接配置。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端启动后监听 `http://localhost:8080`。

### 4. 启动前端

打开新终端：

```powershell
cd frontend
npm install
npm run dev
```

前端启动后访问 `http://localhost:5173`。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2 + Java 21 + Spring Data JPA |
| 前端 | Vue 3 + Element Plus + Vite |
| 数据库 | MySQL 8.0 |
| 流式通信 | SSE (Server-Sent Events) |
| API 格式 | OpenAI 兼容 |

## 项目结构

```
PingpongSmart/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/pingpongsmt/
│       │   ├── PingpongSmartApplication.java
│       │   ├── config/
│       │   │   ├── LlmConfig.java
│       │   │   └── EnvLoader.java
│       │   ├── controller/
│       │   │   └── ChatController.java
│       │   ├── dto/
│       │   │   ├── ChatRequest.java
│       │   │   ├── ChatHistoryResponse.java
│       │   │   └── SseMessage.java
│       │   ├── entity/
│       │   │   └── ChatMessage.java
│       │   ├── repository/
│       │   │   └── ChatMessageRepository.java
│       │   └── service/
│       │       ├── LlmService.java
│       │       └── SessionManager.java
│       └── resources/
│           ├── application.yml
│           └── .env.example
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── main.js
│       ├── App.vue
│       └── views/
│           └── ChatView.vue
├── README.md
├── CHANGELOG.md
└── docs/
    ├── v0.1.0/
    │   ├── spec.md
    │   ├── plan.md
    │   ├── task.md
    │   └── checklist.md
    └── v0.1.1/
        ├── spec.md
        ├── plan.md
        ├── task.md
        └── checklist.md
```

## API 接口

### POST /api/chat

接收用户消息，返回 SSE 流式响应。

**请求体：**

```json
{ "message": "如何提高反手拧拉质量", "sessionId": "uuid" }
```

**响应：** `text/event-stream`，每条数据为 JSON：

```json
{ "content": "你好", "done": false }
```

```json
{ "content": "", "done": true }
```

### GET /api/chat/history

加载指定会话的历史消息。

**参数：**
- `sessionId`（必填）：会话 ID
- `limit`（可选，默认 20）：加载最近 N 条消息

**响应：**

```json
{
  "sessionId": "uuid",
  "messages": [
    { "role": "user", "content": "如何提高反手拧拉质量", "createdAt": "2026-06-15T12:00:00" },
    { "role": "assistant", "content": "反手拧拉的关键是...", "createdAt": "2026-06-15T12:00:02" }
  ]
}
```

## 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pingpong.chat.history-limit` | 20 | 页面加载时拉取的最近消息数 |
| `pingpong.chat.context-rounds` | 10 | 发给 LLM 的上下文轮数 |
