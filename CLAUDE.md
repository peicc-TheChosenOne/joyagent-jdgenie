# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在本代码库中工作提供指导。

## 项目概述

JoyAgent-JDGenie 是业界首个开源的高完成度轻量化通用多智能体产品，在 GAIA 榜单验证集准确率 75.15%，测试集 65.12%，超越多个知名产品。这是一个端到端的完整多智能体产品，开箱即用，支持二次开发。

## 系统架构

系统由4个主要组件组成：
- **genie-backend**: Java Spring Boot 服务（端口8080）- 协调智能体并提供API
- **ui**: React 前端（端口3000）- 用户界面
- **genie-tool**: Python 服务（端口1601）- 工具集，包括代码解释器、搜索、报告生成
- **genie-client**: MCP 客户端服务（端口8188）- 模型上下文协议实现

## 快速启动命令

### Docker方式（推荐）
```bash
docker build -t genie:latest .
docker run -d -p 3000:3000 -p 8080:8080 -p 1601:1601 -p 8188:8188 --name genie-app genie:latest
```

### 手动部署
```bash
# 1. 检查依赖和端口
sh check_dep_port.sh

# 2. 一键启动所有服务
sh Genie_start.sh

# 或分步骤启动：
cd ui && sh start.sh          # 启动前端 - http://localhost:3000
cd genie-backend && sh build.sh && sh start.sh  # 启动后端 - http://localhost:8080
cd genie-tool && uv run python server.py         # 启动工具服务 - http://localhost:1601
cd genie-client && sh start.sh                   # 启动MCP客户端 - http://localhost:8188
```

## 配置要求

### 必需的环境变量
- **后端**: 更新 `genie-backend/src/main/resources/application.yml`
  - `base_url`: LLM API 端点
  - `apikey`: LLM API 密钥
  - `model`: 模型名称（如 "gpt-4.1", "deepseek/deepseek-chat"）
  - `max_tokens`: 最大token数（deepseek-chat为8192）

- **工具服务**: 复制 `genie-tool/.env_template` 到 `genie-tool/.env`
  - `OPENAI_API_KEY`: OpenAI API 密钥
  - `OPENAI_BASE_URL`: OpenAI 基础URL
  - `SERPER_SEARCH_API_KEY`: Serper.dev API 密钥
  - `DEFAULT_MODEL`: 模型标识符（如 "deepseek/deepseek-chat"）

## 开发命令

### 前端（React/TypeScript）
```bash
cd ui
pnpm install          # 安装依赖
pnpm dev              # 开发服务器
pnpm build            # 生产构建
pnpm lint             # 运行ESLint
pnpm fix              # 修复代码格式
pnpm preview          # 预览生产构建
```

### 后端（Java/Spring Boot）
```bash
cd genie-backend
sh build.sh           # Maven构建
sh start.sh           # 启动服务
mvn test              # 运行测试
mvn clean package     # 创建JAR包
mvn spring-boot:run   # 开发模式运行
```

### 工具服务（Python）
```bash
cd genie-tool
uv sync               # 安装依赖
uv run python server.py    # 启动服务
uv run pytest         # 运行测试（如有）
uv run --dev python server.py  # 开发模式启动
```

### MCP客户端（Python）
```bash
cd genie-client
uv sync               # 安装依赖
uv run python server.py    # 启动MCP客户端服务
```

## 关键目录与架构

### 后端结构 (`genie-backend/`)
- `src/main/java/com/jd/genie/` - Java后端核心
  - `agent/` - 智能体实现 (ReactAgent, PlanningAgent, ExecutorAgent, SummaryAgent)
  - `controller/` - REST API控制器
  - `service/` - 业务逻辑服务
  - `tool/` - 工具实现和接口
  - `prompt/` - 系统提示和配置

### 前端结构 (`ui/`)
- `src/` - React前端组件
  - `components/` - 可复用UI组件
  - `pages/` - 页面组件
  - `services/` - API服务
  - `types/` - TypeScript类型定义
  - `utils/` - 工具函数

### 工具服务结构 (`genie-tool/`)
- `genie_tool/` - Python工具和实用程序
  - `tool/` - 核心工具实现 (code_interpreter, deepsearch, report, ci_agent)
  - `api/` - API端点和请求处理
  - `model/` - 数据模型和模式
  - `prompt/` - YAML提示模板
  - `util/` - 实用模块 (llm_util, file_util, log_util)

### MCP客户端结构 (`genie-client/`)
- `app/` - MCP客户端实现
  - `client.py` - MCP客户端核心
  - `config.py` - 配置管理
  - `server.py` - FastAPI服务器

## 智能体类型与模式

### 多智能体架构
- **ReactAgent**: 带规划能力的反应式智能体
- **PlanningAgent**: 任务规划和执行
- **ExecutorAgent**: 任务执行专家
- **SummaryAgent**: 结果总结

### 核心特性
- 多层次多模式思考
- 跨任务工作流记忆
- 通过自动拆解重组实现工具进化
- 全链路流式输出
- 高并发DAG执行引擎

## 添加自定义工具

### Java后端工具
1. 在 `genie-backend/src/main/java/com/jd/genie/tool/` 中实现 `BaseTool` 接口
2. 在 `GenieController#buildToolCollection` 中注册工具
3. 重启后端服务

### Python工具
1. 在 `genie-tool/genie_tool/tool/` 中创建新工具类
2. 添加到 `genie-tool/genie_tool/api/tool.py`
3. 重启工具服务

### MCP工具
1. 在 `application.yml` 中配置MCP服务器URL
2. 添加MCP服务器端点到 `mcp_server_url` 配置
3. 重启后端服务

## 配置文件

### 后端配置 (`genie-backend/src/main/resources/application.yml`)
- LLM设置和API配置
- 智能体提示模板
- 工具服务URL
- MCP服务器配置

### 前端配置 (`ui/.env`)
- 后端API URL
- 环境特定设置

### 工具服务配置 (`genie-tool/.env`)
- 外部服务API密钥 (OpenAI, Serper)
- 模型配置
- 服务URL

## 端口映射

- **3000**: 前端（Vite开发服务器）
- **8080**: 后端API（Spring Boot）
- **1601**: 工具服务（Python FastAPI）
- **8188**: MCP客户端服务（FastAPI）

## 依赖与要求

### 系统要求
- Java 17+
- Python 3.11+
- Node.js 18+ with pnpm 7+
- Maven 3.6+
- uv（Python包管理器）

### 构建依赖
- **前端**: React 19, TypeScript, Ant Design, Vite
- **后端**: Spring Boot 3.2, Java 17, Maven
- **工具服务**: FastAPI, Pydantic, LiteLLM, Pandas
- **MCP客户端**: FastAPI, MCP协议 1.9.4

## 测试与质量

### 运行测试
```bash
# 前端测试
cd ui && pnpm test

# 后端测试
cd genie-backend && mvn test

# 工具服务测试
cd genie-tool && uv run pytest
```

### 代码质量
- 前端: ESLint + Prettier
- 后端: Maven Checkstyle
- Python: Black格式化, mypy类型检查

## 部署选项

### 生产部署
```bash
# 构建所有服务
cd ui && pnpm build
cd genie-backend && mvn clean package -DskipTests
cd genie-tool && uv run python -m build
```

### Docker Compose
```yaml
version: '3.8'
services:
  genie-backend:
    build: ./genie-backend
    ports:
      - "8080:8080"
  genie-tool:
    build: ./genie-tool
    ports:
      - "1601:1601"
  genie-client:
    build: ./genie-client
    ports:
      - "8188:8188"
  ui:
    build: ./ui
    ports:
      - "3000:3000"
```