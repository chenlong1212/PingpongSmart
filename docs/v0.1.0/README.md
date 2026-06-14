# PingpongSmart

面向乒乓球用户的私有知识管理与智能问答平台。

v0.1.0 — 最小可运行版本，跑通「用户提问 → 大模型回答 → 流式展示」这条核心链路。

## 系统要求

- Java 21+
- Maven 3.8+
- Node.js 18+

## 快速开始

### 1. 配置 API Key

```powershell
Copy-Item backend/src/main/resources/.env.example backend/src/main/resources/.env
```

编辑 `backend/src/main/resources/.env`，填写你的 API Key：

```
LLM_BASE_URL=https://maas-api.cn-huabei-1.xf-yun.com/v2
LLM_API_KEY=你的-API-Key
LLM_MODEL=xopqwen36v35b
```

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端启动后监听 `http://localhost:8080`。

### 3. 启动前端

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
| 后端 | Spring Boot 3.2 + Java 21 |
| 前端 | Vue 3 + Element Plus + Vite |
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
│       │   │   └── SseMessage.java
│       │   └── service/
│       │       └── LlmService.java
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
└── docs/
    └── v0.1.0/
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
{ "message": "如何提高反手拧拉质量" }
```

**响应：** `text/event-stream`，每条数据为 JSON：

```json
{ "content": "你好", "done": false }
```

```json
{ "content": "", "done": true }
```

## 不做的事 (v0.1.0)

- 用户注册/登录
- 聊天记录持久化
- 乒乓球知识库 / RAG
- 多轮对话上下文
- 语音输入/输出
