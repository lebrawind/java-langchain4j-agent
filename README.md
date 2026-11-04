# 🤖 Java Agent Service (LangChain4j + Spring Boot)

基于 Spring Boot 3 和 LangChain4j 框架构建的智能体（Agent）服务，接入 DeepSeek 大模型，具备工具调用、上下文记忆持久化与多用户隔离能力。

## ✨ 核心特性

- **动态工具调用 (Function Calling)**：通过 `@Tool` 注解封装本地 Java 方法（如加减法、查天气），大模型可自主判断并调用。
- **多用户上下文隔离**：基于 `@MemoryId` 注解与 `ChatMemoryProvider`，实现不同用户（如 user1, user2）的对话记忆完全隔离。
- **对话历史持久化**：自定义实现 `ChatMemoryStore` 接口，结合 JdbcTemplate 将多轮对话（JSON格式）持久化至 MySQL 数据库，服务重启记忆不丢失。
- **鲁棒性与异常兜底**：配置 `@SystemMessage` 防御 Prompt 注入；Controller 层增加参数校验拦截空请求；利用滑动窗口（`maxMessages`）防止 Token 超限。

## 🛠️ 技术栈

- **后端框架**：Java 17, Spring Boot 3.4
- **AI 框架**：LangChain4j
- **大语言模型**：DeepSeek API (兼容 OpenAI 格式)
- **数据库**：MySQL 8.0 + Spring JDBC (HikariCP)

## 🚀 快速启动

1. **初始化数据库**：
   在 MySQL 中创建数据库 `agent_db`，并执行建表语句：
   ```sql
   CREATE TABLE IF NOT EXISTS chat_memory (
       id VARCHAR(255) PRIMARY KEY,
       messages LONGTEXT
   );
