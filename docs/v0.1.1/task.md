# v0.1.1 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `backend/pom.xml` | 新增 Spring Data JPA、MySQL 驱动依赖 |
| 修改 | `backend/src/main/resources/application.yml` | 新增 MySQL 数据源配置、聊天配置 |
| 新建 | `backend/src/main/java/com/pingpongsmt/entity/ChatMessage.java` | JPA 实体类 |
| 新建 | `backend/src/main/java/com/pingpongsmt/repository/ChatMessageRepository.java` | JPA Repository 接口 |
| 新建 | `backend/src/main/java/com/pingpongsmt/service/SessionManager.java` | Session 管理 |
| 新建 | `backend/src/main/java/com/pingpongsmt/dto/ChatHistoryResponse.java` | 历史消息响应 DTO |
| 修改 | `backend/src/main/java/com/pingpongsmt/dto/ChatRequest.java` | 新增 sessionId 字段 |
| 修改 | `backend/src/main/java/com/pingpongsmt/service/LlmService.java` | 改造：注入 Repository，查询历史上下文 |
| 修改 | `backend/src/main/java/com/pingpongsmt/controller/ChatController.java` | 改造：接收 sessionId，存消息，新增 history 接口 |
| 修改 | `frontend/package.json` | 版本升级为 0.1.1 |
| 修改 | `frontend/src/App.vue` | 新增用户名输入/检查逻辑 |
| 修改 | `frontend/src/views/ChatView.vue` | 改造：sessionId、历史加载、新建对话 |

## T1: 新增后端依赖（JPA + MySQL）

**文件：** `backend/pom.xml`
**依赖：** 无
**步骤：**
1. 在现有 dependencies 中新增 `spring-boot-starter-data-jpa` 依赖
2. 新增 `mysql-connector-j` 依赖（scope: runtime）
3. 在 properties 中新增 `mysql.version` 属性（如需要锁定版本）

**验证：** `cd backend && mvn dependency:resolve` 无报错

## T2: 配置 MySQL 数据源和聊天参数

**文件：** `backend/src/main/resources/application.yml`
**依赖：** 无
**步骤：**
1. 新增 `spring.datasource.url` 指向本地 MySQL
2. 新增 `spring.datasource.username` 和 `spring.datasource.password`
3. 新增 `spring.jpa.hibernate.ddl-auto=update`
4. 新增 `spring.jpa.show-sql=true`
5. 新增 `pingpong.chat.history-limit: 20`
6. 新增 `pingpong.chat.context-rounds: 10`

**验证：** `mvn compile -pl backend` 编译通过

## T3: 新建 ChatMessage 实体

**文件：** `backend/src/main/java/com/pingpongsmt/entity/ChatMessage.java`
**依赖：** 无
**步骤：**
1. 创建 `@Entity` 类 `ChatMessage`
2. 定义字段：id（Long, @Id, @GeneratedValue(strategy=IDENTITY)）、sessionId（String）、role（String）、content（String）、createdAt（LocalDateTime, @CreationTimestamp）
3. 添加 Lombok `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
4. 添加 `@Table(name = "chat_messages")` 和 `@Index(name = "idx_session_created", columnList = "sessionId, createdAt")`

**验证：** `mvn compile -pl backend` 编译通过

## T4: 新建 ChatMessageRepository

**文件：** `backend/src/main/java/com/pingpongsmt/repository/ChatMessageRepository.java`
**依赖：** T3
**步骤：**
1. 创建接口 `ChatMessageRepository extends JpaRepository<ChatMessage, Long>`
2. 定义方法：`List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId)`
3. 定义方法：`List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable)`

**验证：** `mvn compile -pl backend` 编译通过

## T5: 新建 SessionManager

**文件：** `backend/src/main/java/com/pingpongsmt/service/SessionManager.java`
**依赖：** 无
**步骤：**
1. 创建 `@Service` 类 `SessionManager`
2. 定义内部类 `SessionInfo`（sessionId, username, createdAt）
3. 实现 `createSession(String username)` → 生成 UUID，返回 SessionInfo
4. 实现 `isValidSession(String sessionId)` → 校验 UUID 格式

**验证：** `mvn compile -pl backend` 编译通过

## T6: 新建 ChatHistoryResponse DTO

**文件：** `backend/src/main/java/com/pingpongsmt/dto/ChatHistoryResponse.java`
**依赖：** 无
**步骤：**
1. 创建类 `ChatHistoryResponse`
2. 定义字段：sessionId（String）、messages（List<HistoryMessage>）
3. 定义内部类 `HistoryMessage`：role（String）、content（String）、createdAt（LocalDateTime）
4. 添加构造函数，支持从 List<ChatMessage> + sessionId 构建

**验证：** `mvn compile -pl backend` 编译通过

## T7: 改造 ChatRequest（新增 sessionId）

**文件：** `backend/src/main/java/com/pingpongsmt/dto/ChatRequest.java`
**依赖：** 无
**步骤：**
1. 新增 `private String sessionId` 字段
2. 新增 getter/setter
3. `@NotBlank` 校验保留在 `message` 上，`sessionId` 不加 `@NotBlank`

**验证：** `mvn compile -pl backend` 编译通过

## T8: 改造 LlmService（注入上下文）

**文件：** `backend/src/main/java/com/pingpongsmt/service/LlmService.java`
**依赖：** T4（ChatMessageRepository）
**步骤：**
1. 在构造函数中注入 `ChatMessageRepository`
2. 新增 `@Value("${pingpong.chat.context-rounds:10}")` 读取配置
3. 新增私有方法 `buildMessageBody(String message, String sessionId)`：
   - sessionId 为空 → 返回与 v0.1.0 相同的单消息 body
   - 否则：查询历史 → 取最近 contextRounds 轮 → 拼入 messages 数组 → 末尾追加当前消息
4. 改造 `chatStream` 签名，内部调用 `buildMessageBody`
5. 在 `chatStream` 的 `onComplete` 回调中保存 AI 回复

**验证：** `mvn compile -pl backend` 编译通过

## T9: 改造 ChatController（持久化 + history 接口）

**文件：** `backend/src/main/java/com/pingpongsmt/controller/ChatController.java`
**依赖：** T5、T6、T7、T8
**步骤：**
1. 注入 `MessageRepository` 和 `SessionManager`
2. 改造 `chat()` 方法：校验 sessionId，为空则创建新 session，存用户消息
3. SSE 完成后不直接存 AI 回复（T8 中 LlmService 内部处理）
4. 新增 `GET /api/chat/history` 接口

**验证：** `mvn compile -pl backend` 编译通过

## T10: 前端 — package.json 版本升级

**文件：** `frontend/package.json`
**依赖：** 无
**步骤：**
1. `"version": "0.1.0"` → `"version": "0.1.1"`

**验证：** 文件内容正确

## T11: 前端 — App.vue 用户名检查

**文件：** `frontend/src/App.vue`
**依赖：** 无
**步骤：**
1. 新增 `username` ref，从 localStorage 读取
2. 如果 username 为空，显示 el-dialog 输入框
3. 输入后存 localStorage
4. 用户输入后渲染 ChatView

**验证：** 首次进入显示用户名输入框，输入后下次自动显示

## T12: 前端 — ChatView.vue 改造（sessionId + 历史加载 + 新建对话）

**文件：** `frontend/src/views/ChatView.vue`
**依赖：** T11
**步骤：**
1. 新增 `sessionId` ref，从 localStorage 读取，无则生成 UUID
2. 新增 `loadHistory()` 方法，调用 `/api/chat/history` 接口
3. 改造 `sendMessage()`：fetch body 增加 sessionId
4. 新增"新建对话"按钮
5. 页面加载时（onMounted）调用 `loadHistory()`
6. loadHistory 完成后调用 `scrollToBottom()`

**验证：** 刷新页面后消息不丢失，新建对话可切换

## 执行顺序

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9
                                    ↗
T10 → T11 → T12
```
