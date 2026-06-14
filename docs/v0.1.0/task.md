# PingpongSmart v0.1.0 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `backend/pom.xml` | Maven 配置：SpringBoot 3.x, WebFlux, Lombok |
| 新建 | `backend/src/main/java/com/pingpongsmt/PingpongSmartApplication.java` | 启动类 |
| 新建 | `backend/src/main/java/com/pingpongsmt/config/LlmConfig.java` | @ConfigurationProperties 读取 .env 配置 |
| 新建 | `backend/src/main/java/com/pingpongsmt/config/EnvLoader.java` | 手写 .env 文件解析器 |
| 新建 | `backend/src/main/java/com/pingpongsmt/dto/ChatRequest.java` | 请求体 DTO |
| 新建 | `backend/src/main/java/com/pingpongsmt/dto/SseMessage.java` | SSE 消息体 DTO |
| 新建 | `backend/src/main/java/com/pingpongsmt/controller/ChatController.java` | POST /api/chat，返回 SseEmitter |
| 新建 | `backend/src/main/java/com/pingpongsmt/service/LlmService.java` | WebClient 调用大模型 API，返回 Flux |
| 新建 | `backend/src/main/resources/application.yml` | 端口、超时、LlmConfig 映射 |
| 新建 | `backend/src/main/resources/.env.example` | 配置模板 |
| 新建 | `frontend/package.json` | 依赖：vue, element-plus, vite |
| 新建 | `frontend/vite.config.js` | proxy 配置：/api → localhost:8080 |
| 新建 | `frontend/index.html` | 入口 HTML |
| 新建 | `frontend/src/main.js` | 引入 Element Plus |
| 新建 | `frontend/src/App.vue` | 根组件 |
| 新建 | `frontend/src/views/ChatView.vue` | 聊天页面：消息列表 + 输入框 + SSE 消费 |
| 新建 | `README.md` | 项目说明和启动指南 |

## T1: 创建后端项目骨架

**文件：** `backend/pom.xml`, `backend/src/main/java/...`, `backend/src/main/resources/`
**依赖：** 无
**步骤：**
1. 创建 `backend/pom.xml`，引入依赖：spring-boot-starter-web（不含 webflux，用 SseEmitter）、spring-boot-starter-webflux（WebClient）、spring-boot-starter（默认），lombok
2. 创建 `backend/src/main/java/com/pingpongsmt/PingpongSmartApplication.java`，添加 `@SpringBootApplication` 注解
3. 创建 `backend/src/main/resources/application.yml`，设置 server.port=8080，spring.application.name=pingpongsmt
4. 创建 `backend/src/main/resources/.env.example`，包含三个配置项：LLM_BASE_URL、LLM_API_KEY、LLM_MODEL

**验证：** `cd backend && mvn compile` 编译通过

## T2: 实现 .env 解析器和配置类

**文件：** `backend/src/main/java/com/pingpongsmt/config/EnvLoader.java`, `backend/src/main/java/com/pingpongsmt/config/LlmConfig.java`
**依赖：** T1
**步骤：**
1. 创建 `EnvLoader.java`：静态方法 `load()` 读取 `src/main/resources/.env` 文件，逐行解析 `KEY=VALUE` 格式，跳过注释行（#开头）和空行，将结果写入 `System.getenv()`（通过 `System.setProperty()`）
2. 创建 `LlmConfig.java`：使用 `@ConfigurationProperties(prefix = "llm")` 注解，定义三个字段：`baseUrl`、`apiKey`、`model`，提供 getter/setter
3. 在 `application.yml` 中配置 `llm.base-url`、`llm.api-key`、`llm.model` 绑定到配置类
4. 在 `PingpongSmartApplication.main()` 方法入口调用 `EnvLoader.load()`

**验证：** 在 `.env` 中填写测试值，启动 SpringBoot 应用，通过断点或日志确认配置正确加载（不打印密钥内容）

## T3: 创建 DTO 类

**文件：** `backend/src/main/java/com/pingpongsmt/dto/ChatRequest.java`, `backend/src/main/java/com/pingpongsmt/dto/SseMessage.java`
**依赖：** 无（可与 T1 并行）
**步骤：**
1. 创建 `ChatRequest.java`：包含 `private String message`，提供 getter/setter
2. 创建 `SseMessage.java`：包含 `private String content`、`private boolean done`，提供 getter/setter，使用 Lombok `@Data`

**验证：** `mvn compile` 编译通过

## T4: 实现 LlmService（大模型调用）

**文件：** `backend/src/main/java/com/pingpongsmt/service/LlmService.java`
**依赖：** T2
**步骤：**
1. 注入 `LlmConfig` 配置类
2. 创建 `WebClient` Bean（`@Bean` 方法），baseUrl 从 `llm.base-url` 读取，设置默认请求头 `Authorization: Bearer {apiKey}`
3. 实现 `chatStream(String message)` 方法：
   - 构造请求体：`{ "model": "xopqwen36v35b", "messages": [{ "role": "user", "content": message }], "stream": true }`
   - 通过 WebClient 发送 POST 请求到 `/chat/completions`
   - 读取 `data` 字段（SSE 格式），用 Jackson 解析每行 JSON
   - 提取 `choices[0].delta.content` 非空字段
   - 将每个非空 token 封装为 `SseMessage` 的 JSON 字符串，通过 `Flux<String>` 返回
4. 处理 `done: true` 信号：当收到 `choices[0].finish_reason` 非空或 `done` 字段为 true 时，发送最后一条 `SseMessage("", true)`

**验证：** 写一个简单的 `main` 方法或单元测试，调用 `chatStream("你好")` 并打印结果到控制台，确认收到流式响应

## T5: 实现 ChatController（SSE 接口）

**文件：** `backend/src/main/java/com/pingpongsmt/controller/ChatController.java`
**依赖：** T3, T4
**步骤：**
1. 创建 `@RestController` + `@RequestMapping("/api")` 的 `ChatController`
2. 注入 `LlmService`
3. 实现 `POST /api/chat` 方法：
   - 接收 `@RequestBody ChatRequest request`
   - 创建 `SseEmitter`，设置超时时间 120000ms（120秒）
   - 在异步线程（`CompletableFuture.runAsync`）中调用 `LlmService.chatStream(request.getMessage())`
   - 对 Flux 的每个事件：通过 `emitter.send(SseEmitter.event().defaultData(event))` 推送给前端
   - 订阅完成时：`emitter.send(SseEmitter.event().defaultData(new SseMessage("", true)))`，然后 `emitter.complete()`
   - 订阅异常时：`emitter.send(SseEmitter.event().defaultData(new SseMessage("请求失败，请稍后重试", true)))`，然后 `emitter.complete()`
   - 返回 `SseEmitter`

**验证：** 启动应用，用 `curl -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d '{"message":"你好"}'` 测试，确认收到 SSE 流式响应

## T6: 创建前端项目骨架

**文件：** `frontend/package.json`, `frontend/vite.config.js`, `frontend/index.html`
**依赖：** 无
**步骤：**
1. 创建 `frontend/package.json`：
   - 依赖：`vue ^3.4`, `element-plus ^2.7`
   - 开发依赖：`@vitejs/plugin-vue ^5.0`, `vite ^5.0`
   - scripts：`dev`, `build`, `preview`
2. 创建 `frontend/vite.config.js`：
   - 使用 `@vitejs/plugin-vue`
   - 设置 `server.proxy`：`/api` 代理到 `http://localhost:8080`
   - 设置 `server.port` 为 5173
3. 创建 `frontend/index.html`：引入 `src/main.js`

**验证：** `cd frontend && npm install && npm run dev` 启动成功，浏览器访问 `http://localhost:5173` 不报错

## T7: 实现 ChatView 聊天页面

**文件：** `frontend/src/main.js`, `frontend/src/App.vue`, `frontend/src/views/ChatView.vue`
**依赖：** T6
**步骤：**
1. 创建 `frontend/src/main.js`：引入 Vue 和 Element Plus，`app.use(ElementPlus)`，挂载应用
2. 创建 `frontend/src/App.vue`：单文件组件，`<ChatView />` 占位
3. 创建 `frontend/src/views/ChatView.vue`：
   - **数据结构：**
     - `messages: Array<{role: 'user'|'assistant', content: string}>` — 消息列表
     - `input: string` — 输入框绑定值
     - `loading: boolean` — 是否正在发送
     - `currentContent: string` — 当前正在接收的 assistant 消息内容
   - **方法：**
     - `sendMessage()`：校验输入非空 → 将用户消息推入 messages → 清空输入框 → 禁用输入框 → loading = true → 初始化 currentContent 为空 → 发起 SSE 请求
     - `appendContent(text)`：将 text 追加到 currentContent
     - `finishResponse()`：将 currentContent 拼成完整 assistant 消息推入 messages → loading = false → currentContent = ''
     - `showError(msg)`：Element Plus 的 `ElMessage.error(msg)`，同时 finishResponse
   - **SSE 消费逻辑：**
     - 使用 `fetch('/api/chat', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ message: input.trim() }) })`
     - 获取 `response.body.getReader()`，创建 `TextDecoder`
     - 逐块读取数据，按行分割，解析每行 JSON
     - 如果 `done === true`，调用 `finishResponse()`
     - 否则将 `content` 追加到 `currentContent`
     - 如果 fetch 抛出异常，调用 `showError('网络请求失败')`
   - **模板：**
     - 消息列表区域：v-for 渲染 messages（user 右对齐蓝色气泡，assistant 左对齐灰色气泡）
     - 当前 assistant 消息（currentContent 非空时）实时追加显示
     - 底部输入栏：el-input（v-model=input，:disabled=loading，placeholder="请输入问题..."）+ el-button（发送，:disabled=loading）
     - loading 状态：显示「思考中...」动画效果

**验证：** 启动前端和后端，打开页面输入「你好」，确认：
- 消息以气泡形式展示
- 发送期间输入框禁用，显示「思考中」
- 模型回复逐字出现（打字机效果）
- 回复完成后输入框恢复可用

## T8: 编写 README.md

**文件：** `README.md`
**依赖：** 无
**步骤：**
1. 添加项目标题和一句话描述
2. 添加系统要求：Java 21+, Node.js 18+, Maven 3.8+
3. 编写快速开始步骤：
   ```bash
   # 1. 复制环境变量配置
   cp backend/src/main/resources/.env.example backend/src/main/resources/.env
   # 2. 编辑 .env 填写你的 API Key
   # 3. 启动后端
   cd backend && mvn spring-boot:run
   # 4. 启动前端（新终端）
   cd frontend && npm install && npm run dev
   # 5. 打开 http://localhost:5173
   ```
4. 添加技术栈说明（后端/前端列表）
5. 添加项目结构说明

**验证：** README 内容清晰，按步骤操作可跑通项目

## 执行顺序

```
T1 → T2 → T4 → T5
       ↑
T3（可并行于 T1）→ T5（等 T3, T4）

T6 → T7 → T8
```

- T1、T3 可并行（无依赖）
- T2 依赖 T1
- T4 依赖 T2
- T5 依赖 T3 + T4
- T6、T7、T8 为前端链路，T6 完成后顺序执行
