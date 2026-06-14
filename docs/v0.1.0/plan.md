# PingpongSmart v0.1.0 Plan

## 架构概览

项目为 monorepo，包含两个核心模块：

**backend（SpringBoot 3.x + Java 21）**
- 提供静态文件服务：开发模式下返回前端 index.html，生产模式下直接托管打包后的静态资源
- 提供 SSE 聊天接口 `POST /api/chat`，接收用户消息，调用大模型 API，流式转发响应

**frontend（Vue 3 + Element Plus + Vite）**
- 聊天页面：消息列表 + 输入框，使用 Element Plus 组件（el-input、el-button、el-message 等）
- 通过 `fetch` + `ReadableStream` 消费后端 SSE 接口，实现打字机效果
- 开发模式通过 Vite proxy 转发 `/api` 请求到后端

## 核心数据结构

**后端请求体：**
```json
{ "message": "string — 用户发送的文本消息，必填" }
```

**后端 SSE 流每条消息：**
```json
{ "content": "string — 模型返回的文本片段", "done": "boolean — 是否结束" }
```

**配置项（通过 .env 读取）：**
| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `LLM_BASE_URL` | 大模型 API 地址 | `https://maas-api.cn-huabei-1.xf-yun.com/v2` |
| `LLM_API_KEY` | API 密钥 | 必填，无默认 |
| `LLM_MODEL` | 模型名称 | `xopqwen36v35b` |

## 模块设计

### LlmConfig
**职责：** 从 `.env` 读取 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL
**对外接口：** 提供三个 String 属性的 getter
**依赖：** .env 文件（通过手写 EnvLoader 解析加载）

### ChatController
**职责：** 接收 `POST /api/chat` 请求，调用 LlmService，返回 SSE 流
**对外接口：** `SseEmitter chat(@RequestBody ChatRequest request)`
**依赖：** LlmService
**注意：** 设置 SseEmitter 超时时间为 120 秒

### LlmService
**职责：** 封装 OpenAI 兼容格式的 HTTP 调用，发送用户消息，接收流式响应并转发
**对外接口：** `Flux<String> chatStream(String message)` — 返回逐 token 的 Flux
**依赖：** Spring WebFlux 的 WebClient（用于异步流式请求）

### ChatView.vue
**职责：** 展示聊天界面，处理用户输入，消费 SSE 流并逐字渲染
**对外接口：** 无（纯页面组件）
**依赖：** 无外部服务，直接调用 `/api/chat`

### App.vue
**职责：** 根组件，引入 ChatView
**对外接口：** 无
**依赖：** ChatView

## 模块交互
（调用链、数据流。哪个模块调哪个，什么顺序。）

1. 用户在 `ChatView.vue` 输入消息，点击发送
2. `ChatView.vue` 禁用输入框，显示「思考中」状态
3. `fetch` 发起 `POST /api/chat` 请求
4. `ChatController` 接收请求，调用 `LlmService.chatStream(message)`
5. `LlmService` 构造 OpenAI 兼容格式的 HTTP 请求，发送异步请求
6. 大模型 API 逐 token 返回流式数据
7. `LlmService` 将每个 token 封装为 JSON，通过 Flux 返回
8. `ChatController` 将 Flux 包装为 SseEmitter，逐条推送 SSE 事件给前端
9. `ChatView.vue` 的 fetch 读取流，解析 JSON，追加到消息气泡
10. 收到 `done: true` 后，恢复输入框可用

错误路径：后端 API 返回 4xx/5xx → SseEmitter 的 `onError` 回调发送错误消息；超时 → SseEmitter 超时回调触发。

## 文件组织
```
PingpongSmart/
├── backend/
│   ├── pom.xml                          — Maven 配置：SpringBoot 3.x, WebFlux, dotenv 依赖
│   └── src/main/
│       ├── java/com/pingpongsmt/
│       │   ├── PingpongSmartApplication.java   — 启动类
│       │   ├── config/
│       │   │   ├── LlmConfig.java              — @ConfigurationProperties 读取 .env
│       │   │   └── EnvLoader.java              — 手写 .env 文件解析器
│       │   ├── controller/
│       │   │   └── ChatController.java         — POST /api/chat，返回 SseEmitter
│       │   └── service/
│       │       └── LlmService.java             — WebClient 调用大模型 API，返回 Flux
│       └── resources/
│           ├── application.yml                 — 端口、超时、LlmConfig 映射
│           └── .env.example                    — 配置模板
├── frontend/
│   ├── package.json                           — 依赖：vue, element-plus, vite
│   ├── vite.config.js                         — proxy 配置：/api → localhost:8080
│   ├── index.html                             — 入口 HTML
│   └── src/
│       ├── main.js                            — 引入 Element Plus
│       ├── App.vue                            — 根组件
│       └── views/
│           └── ChatView.vue                   — 聊天页面：消息列表 + 输入框 + SSE 消费
├── README.md                                  — 项目说明和启动指南
└── spec.md                                    — 需求文档
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 流式转发方式 | WebClient (Flux) + SseEmitter | SpringBoot 3.x 同时支持 WebFlux 和 Spring MVC，WebClient 异步流式最轻量，SseEmitter 前端兼容性好 |
| 配置读取 | 手写 .env 解析 | 项目简单，不需要 Spring Cloud Config 这种重量级方案 |
| 前端流式消费 | fetch + ReadableStream | 原生 API，不需要引入第三方 SSE 库，代码量小 |
| 跨域 | Vite proxy（开发模式）+ 同源（生产模式） | 开发时 vite.config.js 配置 proxy 转发 /api，生产时前端静态资源打包到 backend 内 |
| 构建方式 | Maven 构建前后端 | 前端 npm install + npm run build，后端 maven 打包时将前端 dist/ 复制到 resources/static/ |
| 模型参数 | system prompt 默认空 | v0.1.0 无 RAG，仅做通用问答，不注入 system prompt |
