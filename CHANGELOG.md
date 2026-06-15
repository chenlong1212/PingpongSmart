# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-06-15

### Added
- **MySQL 持久化** — 引入 Spring Data JPA + MySQL，用户消息和 AI 回复自动保存到 `chat_messages` 表
- **多轮对话上下文** — 每次请求携带最近 N 轮对话历史（默认 10 轮），LLM 能基于上下文给出连贯回答
- **Session 管理** — 每个对话自动生成 UUID 标识，通过 localStorage 存储，刷新页面不丢失
- **新建对话** — 前端新增"新对话"按钮，可切换独立会话
- **历史消息接口** — 新增 `GET /api/chat/history` 接口，支持按 session 和 limit 分页查询
- **用户标识** — 首次进入页面时输入用户名，存储于 localStorage
- **历史加载限制** — 页面默认只加载最近 20 条消息（可配置 `pingpong.chat.history-limit`）

### Changed
- **后端版本** 0.1.0 → 0.1.1
- **前端版本** 0.1.0 → 0.1.1
- **ChatRequest DTO** — 新增 `sessionId` 字段
- **LlmService** — `chatStream` 方法支持传入 sessionId，内部查询历史消息作为上下文
- **ChatController** — 新增 `@RestController`，用户消息在发送时立即存入数据库，AI 回复在 SSE 流完成后存入
- **ChatView.vue** — 新增 sessionId 管理、历史消息加载、新建对话功能
- **App.vue** — 新增用户名输入弹窗

### Added (Dependencies)
- `spring-boot-starter-data-jpa`
- `mysql-connector-j` (runtime)

### Configuration
- `application.yml` 新增 `spring.datasource.*` 配置（MySQL 数据源）
- `application.yml` 新增 `pingpong.chat.history-limit` 和 `pingpong.chat.context-rounds` 配置

### Database
- 自动创建 `chat_messages` 表（含 `id`, `session_id`, `role`, `content`, `created_at` 字段）
- 自动创建索引 `idx_session_created(session_id, created_at)`

## [0.1.0] - 2026-06-15

### Added
- 最小可运行版本，跑通「用户提问 → 大模型回答 → 流式展示」这条核心链路
- SSE 流式回复（打字机效果）
- 用户气泡（右对齐蓝色）和 AI 气泡（左对齐灰色）
- 自定义 `.env` 加载器（EnvLoader）

### Not included
- 用户注册/登录
- 聊天记录持久化
- 多轮对话上下文
- 语音输入/输出
