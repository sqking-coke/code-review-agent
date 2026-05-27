# 代码智能审查 Agent - 学习指南

> **项目地址**：https://github.com/sqking-coke/code-review-agent <br>
> **适用对象**：Java 后端开发者，具备 Spring Boot 基础，想深入学习 AI Agent 架构设计

---

## 目录

1. [项目总览](#1-项目总览)
2. [技术栈与开发环境](#2-技术栈与开发环境)
3. [项目结构全景图](#3-项目结构全景图)
4. [核心设计思想](#4-核心设计思想)
5. [分层架构详解](#5-分层架构详解)
6. [Agent 审查闭环：8 步全流程](#6-agent-审查闭环8-步全流程)
7. [RAG 知识库体系](#7-rag-知识库体系)
8. [数据库设计](#8-数据库设计)
9. [API 接口设计](#9-api-接口设计)
10. [关键代码深度剖析](#10-关键代码深度剖析)
11. [线程池与异步任务机制](#11-线程池与异步任务机制)
12. [容错降级策略](#12-容错降级策略)
13. [Prompt 工程](#13-prompt-工程)
14. [部署与运行](#14-部署与运行)
15. [扩展学习路径](#15-扩展学习路径)

---

## 1. 项目总览

### 1.1 这是什么项目？

**代码智能审查 Agent** 是一个企业级 AI 代码审查系统。它接收开发者提交的 Java 代码，自动完成从语法规范校验、语义深度分析、安全漏洞检测、性能评估到优化建议生成的全自动化闭环。

### 1.2 解决什么问题？

传统代码审查的三大痛点：

| 痛点 | 传统方式 | 本项目方案 |
|------|---------|-----------|
| 人工审查效率低 | 靠人肉 review | AI 自动化秒级审查 |
| 审查标准不统一 | 因人而异 | 规则引擎 + AI 双重校验，标准一致 |
| 不贴合企业规范 | 通用工具一刀切 | RAG 注入企业私有规范，专属审查 |

### 1.3 核心差异化亮点

- **原生 Agent 架构**：不依赖 LangChain/Semantic Kernel 等 AI 框架，手写完整 Agent 闭环
- **规则 + AI + RAG 三引擎审查**：后端硬规则兜底 + 大模型语义分析 + 知识库上下文增强
- **RAG 自学习闭环**：审查结果高质量案例自动回流入知识库，越用越聪明

### 1.4 学完你能掌握什么？

1. 如何从零设计一个 AI Agent 系统
2. 多引擎混合检索（向量 + 关键词）的实现原理
3. 大模型 Prompt 工程的实际应用
4. 企业级异步任务处理与容错设计
5. RAG 知识库的完整生命周期管理

---

## 2. 技术栈与开发环境

### 2.1 核心技术栈

```
┌─────────────────────────────────────────────┐
│                 接入层                       │
│         Spring Boot 3.5.x + Java 21         │
├─────────────────────────────────────────────┤
│              持久层                          │
│   MyBatis-Plus 3.5.6 + MySQL 8.0           │
├─────────────────────────────────────────────┤
│              AI 能力层                       │
│   OkHttp 4.12 (HTTP 调用 LLM API)           │
│   FastJSON2 2.0.53 (JSON 序列化)            │
├─────────────────────────────────────────────┤
│            工具与文档解析                     │
│   Hutool 5.8.34    通用工具集                │
│   Apache POI 5.3   Word 解析                │
│   PDFBox 3.0        PDF 解析                │
│   CommonMark 0.22   Markdown 解析           │
│   HanLP            中文分词                  │
└─────────────────────────────────────────────┘
```

### 2.2 环境要求

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 21 | 编译运行 |
| Maven | 3.8+ | 构建管理 |
| MySQL | 8.0 | 业务数据 + 向量存储（兼容模式） |
| Embedding 服务 | BGE-large-zh（推荐） | 文本向量化 |
| LLM API | DeepSeek/Qwen/OpenAI 等 | 语义分析 |

---

## 3. 项目结构全景图

```
code-review-agent/
├── pom.xml                                    # Maven 依赖配置
├── README.md                                  # 项目设计文档
├── sql/
│   └── init.sql                               # 数据库初始化脚本（6 张表 + 17 条默认规则）
└── src/main/
    ├── java/com/codereview/
    │   ├── CodeReviewAgentApplication.java    # SpringBoot 启动入口
    │   │
    │   ├── config/                            # === 配置层 ===
    │   │   ├── LLMConfig.java                 # 大模型配置（provider/API/key/model）
    │   │   ├── EmbeddingConfig.java           # Embedding 模型配置
    │   │   ├── MyBatisPlusConfig.java         # 分页插件配置
    │   │   └── ThreadPoolConfig.java          # 异步线程池配置
    │   │
    │   ├── client/                            # === 外部服务客户端 ===
    │   │   ├── LLMClient.java                 # LLM HTTP 客户端（OkHttp + 重试）
    │   │   └── EmbeddingClient.java           # Embedding HTTP 客户端 + 余弦相似度
    │   │
    │   ├── controller/                        # === 接入层 ===
    │   │   ├── CodeReviewController.java      # 审查 API（提交/查询/报告/规则/统计）
    │   │   └── RagKnowledgeController.java    # RAG 知识库管理 API
    │   │
    │   ├── service/                           # === 业务服务层 ===
    │   │   ├── CodeReviewTaskService.java     # 审查业务服务
    │   │   ├── RagKnowledgeService.java       # RAG 知识库管理服务
    │   │   ├── ReviewStatisticsService.java   # 代码质量统计服务
    │   │   │
    │   │   ├── agent/                         # === Agent 核心层 ★ ===
    │   │   │   ├── AgentOrchestrator.java     # ★ 总调度器（8 步审查闭环）
    │   │   │   ├── CodePreprocessor.java      # 代码预处理
    │   │   │   ├── RuleChecker.java           # 基础规则校验（正则引擎）
    │   │   │   ├── LLMCodeReviewer.java       # LLM 语义审查
    │   │   │   ├── CodeOptimizer.java         # 代码优化生成
    │   │   │   └── ReportGenerator.java       # 审查报告生成
    │   │   │
    │   │   └── rag/                           # === RAG 知识增强层 ===
    │   │       ├── RetrievalService.java      # ★ 混合检索引擎（向量+关键词+融合）
    │   │       ├── EmbeddingService.java      # 向量化业务封装
    │   │       ├── VectorStoreService.java    # 向量存储与 Top-K 搜索
    │   │       ├── TextChunker.java           # 智能文本分块
    │   │       ├── DocumentParser.java        # 多格式文档解析
    │   │       └── KnowledgeFeedbackService.java  # 知识反馈与回流
    │   │
    │   ├── entity/                            # === 数据实体层 ===
    │   │   ├── CodeReviewTask.java            # 审查任务
    │   │   ├── CodeReviewDetail.java          # 问题明细
    │   │   ├── CodeReviewRule.java            # 审查规则
    │   │   ├── RagKnowledgeDoc.java           # RAG 知识文档
    │   │   ├── RagKnowledgeChunk.java         # RAG 知识块
    │   │   └── RagRetrievalLog.java           # RAG 检索日志
    │   │
    │   ├── mapper/                            # === 数据访问层 ===
    │   │   ├── CodeReviewTaskMapper.java      # 含统计 SQL（问题分布/趋势/排名）
    │   │   ├── CodeReviewDetailMapper.java
    │   │   ├── CodeReviewRuleMapper.java
    │   │   ├── RagKnowledgeDocMapper.java
    │   │   ├── RagKnowledgeChunkMapper.java   # 含命中计数/按文档查询
    │   │   └── RagRetrievalLogMapper.java
    │   │
    │   ├── dto/                               # === 数据传输对象 ===
    │   │   ├── request/                       # 请求体
    │   │   │   ├── ReviewSubmitRequest.java   # 提交审查请求
    │   │   │   ├── RuleSaveRequest.java       # 规则保存请求
    │   │   │   ├── RagSearchRequest.java      # 知识检索请求
    │   │   │   └── RagFeedbackSubmitRequest.java  # 知识回流请求
    │   │   └── vo/                            # 响应视图
    │   │       ├── Result.java                # 统一响应包装
    │   │       ├── ReviewTaskVO.java          # 审查任务视图
    │   │       ├── ReviewReportVO.java        # 审查报告视图
    │   │       ├── ReviewStatisticsVO.java    # 质量统计视图
    │   │       └── RagSearchResultVO.java     # 检索结果视图
    │   │
    │   ├── exception/                         # === 异常处理 ===
    │   │   ├── BusinessException.java         # 业务异常
    │   │   └── GlobalExceptionHandler.java    # 全局异常拦截
    │   │
    │   └── util/                              # === 工具类 ===
    │       ├── PromptTemplate.java            # LLM Prompt 模板工程
    │       └── JsonParser.java                # JSON 安全解析 + 向量序列化
    │
    └── resources/
        ├── application.yml                    # 全局配置
        └── prompt/
            └── review-prompt.txt              # 审查 Prompt 备用模板
```

---

## 4. 核心设计思想

### 4.1 为什么不用 AI 框架（LangChain 等）？

```
LangChain/SpringAI 等框架的特点：
  - 黑盒封装，屏蔽细节 → 难以定制和调试
  - 版本迭代快 → API 不稳定，维护成本高
  - 依赖重 → 引入大量不需要的依赖

本项目原生手写 Agent 的优势：
  - 完全透明，每个环节都可控可调
  - 零 AI 框架依赖 → 包体积小、启动快
  - 彻底掌握 Agent 核心原理 → 面试高频亮点
```

### 4.2 "三引擎"审查机制

```
                  ┌─────────────┐
                  │  用户代码    │
                  └──────┬──────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
   ┌──────────┐  ┌──────────────┐  ┌──────────┐
   │ 规则引擎  │  │  LLM 语义    │  │ RAG 增强 │
   │          │  │  深度分析     │  │ 上下文    │
   │ 正则匹配  │  │ 空指针/性能  │  │ 企业规范  │
   │ 格式校验  │  │ 安全/设计    │  │ 历史案例  │
   │ 命名规范  │  │ 逻辑缺陷     │  │ 最佳实践  │
   └────┬─────┘  └──────┬───────┘  └────┬─────┘
        │               │               │
        └───────────────┼───────────────┘
                        ▼
                  ┌──────────┐
                  │ 问题聚合  │
                  │ 去重合并  │
                  └─────┬────┘
                        ▼
                  ┌──────────┐
                  │ 审查报告  │
                  └──────────┘
```

三个引擎的定位：

| 引擎 | 技术 | 覆盖问题 | 特点 |
|------|------|---------|------|
| **规则引擎** | Java 正则匹配 | 命名、格式、基础 BUG 模式 | 100% 可靠，零 LLM 依赖 |
| **LLM 语义** | 大模型深度理解 | 逻辑缺陷、设计问题、隐性风险 | 语义级分析，传统工具无法覆盖 |
| **RAG 增强** | 向量检索 + 关键词 | 企业规范匹配、历史案例预警 | 使审查贴合企业实际 |

---

## 5. 分层架构详解

### 5.1 五层架构

```
┌─────────────────────────────────────────────────┐
│     接入层 (Controller)                          │
│     CodeReviewController / RagKnowledgeController│
│     职责: 参数校验、权限校验、路由调度              │
├─────────────────────────────────────────────────┤
│     业务服务层 (Service)                         │
│     CodeReviewTaskService / RagKnowledgeService  │
│     职责: 业务逻辑编排、事务管理                   │
├─────────────────────────────────────────────────┤
│     Agent 核心层 ★ (service/agent)               │
│     AgentOrchestrator 调度 6 个 Agent 组件        │
│     职责: 审查全流程调度、问题聚合、结果持久化      │
├─────────────────────────────────────────────────┤
│     RAG 知识增强层 (service/rag)                  │
│     RetrievalService + Embedding + VectorStore   │
│     职责: 文档管理、向量化、混合检索、知识回流      │
├─────────────────────────────────────────────────┤
│     基础服务层 (config / client / mapper)         │
│     LLM配置、HTTP客户端、数据库持久化、线程池      │
│     职责: 底层基础设施支撑                        │
└─────────────────────────────────────────────────┘
```

### 5.2 AGENT 核心层内部组成

Agent 核心层采用**组件化设计**，每个组件单一职责：

```
AgentOrchestrator（总调度器）
    │
    ├── CodePreprocessor    → 代码清洗、格式化、分片
    ├── RuleChecker         → 17 条基础规则正则匹配
    ├── RetrievalService    → RAG 混合检索（向量 + 关键词）
    ├── LLMCodeReviewer     → LLM 语义深度审查
    ├── CodeOptimizer       → AI 优化方案生成
    └── ReportGenerator     → 结构化报告组装
```

---

## 6. Agent 审查闭环：8 步全流程

这是整个项目最核心的部分。每次用户提交代码审查，系统严格按照以下 8 步执行：

### 完整流程图

```
用户提交代码
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 1: 代码预处理 (CodePreprocessor)                 │
│ · 统一换行符 \r\n → \n                               │
│ · 压缩多余空行（3+ → 2）                              │
│ · 压缩多余空格/Tab                                    │
│ · 长度校验（默认最大 50000 字符）                      │
│ · 大代码智能分片（按行边界，不截断语句）                │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 2: 基础规则校验 (RuleChecker)                    │
│ · 从 DB 查询所有启用状态的 BASIC 规则（默认 17 条）     │
│ · 逐条正则匹配代码                                    │
│ · 计算匹配位置对应行号                                │
│ · 输出标准化问题 JSON                                 │
│                                                      │
│ 覆盖维度：                                            │
│ · STYLE: 类名大驼峰、方法名小驼峰、常量全大写、魔法值   │
│ · BUG: 空指针风险、异常空捕获、资源未关闭              │
│ · PERFORMANCE: 循环查库、字符串拼接、集合容量          │
│ · SECURITY: SQL注入、硬编码密钥、日志脱敏             │
│ · DESIGN: 方法过长、参数过多、if嵌套过深              │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 3: RAG 知识检索增强 (RetrievalService)           │
│ · 代码 → Embedding 向量化                             │
│ · 向量语义召回 Top-20                                 │
│ · 关键词 BM25 召回 Top-10                             │
│ · 多路融合加权排序                                    │
│ · 相似度过滤（阈值 0.65）                             │
│ · 输出 Top-5 注入 LLM Prompt                         │
│                                                      │
│ 上下文组装（按知识类型分类）：                         │
│ · STANDARD → 企业规范上下文 → 注入审查 Prompt        │
│ · CASE/PATTERN → 历史案例上下文 → 注入审查 Prompt    │
│ · PRACTICE → 最佳实践 → 注入优化 Prompt              │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 4: LLM 语义深度审查 (LLMCodeReviewer)            │
│ · 构建含 RAG 上下文的完整 Prompt                      │
│ · 调用 LLM API（OkHttp + 重试机制）                   │
│ · 清洗 LLM 返回（去除 ```json 标记）                  │
│ · 解析评分、总结、问题列表                            │
│                                                      │
│ 审查维度：                                            │
│ · 代码规范 (STYLE)                                    │
│ · 逻辑 BUG (BUG)                                      │
│ · 性能隐患 (PERFORMANCE)                              │
│ · 安全漏洞 (SECURITY)                                 │
│ · 设计质量 (DESIGN)                                   │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 5: 问题聚合去重 (AgentOrchestrator.mergeProblems)│
│ · 合并规则校验结果 + LLM 审查结果                     │
│ · 精确去重：同行号 + 同类型 → 合并                    │
│ · 模糊去重：描述文本 70% 以上相似 → 合并              │
│ · 规则问题优先保留，LLM 优化信息合并到已有问题          │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 6: 代码优化生成 (CodeOptimizer)                  │
│ · 规则命中有 fixExample → 直接使用（零 LLM 调用）      │
│ · LLM 发现问题 → 调用优化 Prompt 生成修复方案          │
│ · 注入 RAG 最佳实践作为优化参考                        │
│ · 输出：优化思路 + 重构代码 + 最佳实践引用             │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 7: 审查报告组装 (ReportGenerator)                │
│ · 代码质量综合评分 (0-100)                            │
│ · 风险分级统计 (HIGH/MEDIUM/LOW)                     │
│ · 问题清单（含行号、描述、修复代码）                   │
│ · RAG 知识引用列表                                    │
│ · LLM 生成的整体质量评估总结                           │
└──────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────┐
│ Step 8: 数据持久化 (AgentOrchestrator.persistResults) │
│ · 问题明细批量入库（code_review_detail）              │
│ · 任务状态更新为"已完成"                               │
│ · 写入评分、风险统计、报告 JSON                       │
│ · RAG 检索日志记录                                    │
└──────────────────────────────────────────────────────┘
```

### Step 8 的关键代码逻辑

```java
// AgentOrchestrator.java - executeReview() 方法核心流程
@Async("reviewExecutor")
public CompletableFuture<ReviewReportVO> executeReview(CodeReviewTask task) {
    // Step 1: 预处理
    String processed = preprocessor.preprocess(task.getOriginalCode());

    // Step 2: 规则校验
    List<JSONObject> ruleProblems = ruleChecker.check(processed);

    // Step 3: RAG 检索（失败降级，不中断流程）
    List<JSONObject> ragContexts = new ArrayList<>();
    try {
        JSONObject ragResult = retrievalService.hybridSearch(processed, ragTopK);
        ragContexts = retrievalService.buildRagContextList(ragResult);
    } catch (Exception e) {
        log.warn("RAG 检索失败，降级为纯 LLM 审查");
    }

    // Step 4: LLM 审查（注入 RAG 上下文）
    LLMReviewResult llmResult = llmReviewer.review(processed, ragSpecContext, ragCaseContext);

    // Step 5: 问题聚合去重
    List<JSONObject> allProblems = mergeProblems(ruleProblems, llmResult.problems());

    // Step 6: 代码优化
    List<JSONObject> optimized = codeOptimizer.generateOptimizations(allProblems, ...);

    // Step 7: 报告生成
    ReviewReportVO report = reportGenerator.generateReport(...);

    // Step 8: 持久化
    persistResults(task, allProblems, report, reportJson);
}
```

---

## 7. RAG 知识库体系

### 7.1 什么是 RAG？

RAG（Retrieval-Augmented Generation）= 检索增强生成。在 LLM 生成回答前，先从知识库中检索相关信息，作为 Prompt 的上下文注入，使 LLM 输出更准确、更贴合特定领域。

```
传统 LLM 审查:
  代码 → LLM → 审查结果（通用的，不懂企业规范）

RAG 增强审查:
  代码 → 向量检索 → 找到相关企业规范 + 历史案例
              ↓
  代码 + 规范 + 案例 → LLM → 审查结果（贴合企业的专属审查）
```

### 7.2 知识库完整生命周期

```
┌─────────────────────────────────────────────────────────┐
│                    1. 知识入库                           │
│                                                         │
│  上传文档(.md/.pdf/.docx/.txt)                          │
│      │                                                  │
│      ▼                                                  │
│  DocumentParser 解析                                    │
│      │  · Markdown → CommonMark 转纯文本                │
│      │  · PDF → PDFBox 提取文本                         │
│      │  · Word → POI 提取段落                           │
│      │  · 代码文件 → UTF-8 直接读取                     │
│      ▼                                                  │
│  TextChunker 智能分块                                   │
│      │  · 按段落边界切分（非固定字数）                   │
│      │  · Chunk 大小: 500 tokens                        │
│      │  · Overlap: 80 tokens（保证语义连续性）           │
│      ▼                                                  │
│  EmbeddingService 向量化                                │
│      │  · 调用 BGE-large-zh 模型                        │
│      │  · 生成 1024 维向量                              │
│      ▼                                                  │
│  写入 DB (rag_knowledge_doc + rag_knowledge_chunk)      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    2. 知识检索                           │
│                                                         │
│  待审查代码                                             │
│      │                                                  │
│      ├──→ Embedding 向量化                              │
│      │       │                                          │
│      │       ▼                                          │
│      │   向量语义召回 (Top-20)                           │
│      │   · 余弦相似度计算                                │
│      │   · 小顶堆 Top-K 算法 (O(N log K))               │
│      │                                                  │
│      └──→ 关键词提取                                    │
│              │                                          │
│              ▼                                          │
│           关键词 BM25 匹配 (Top-10)                      │
│                                                         │
│      ───────┬───────                                    │
│             ▼                                           │
│        多路融合加权排序                                  │
│        · 向量权重: 0.5                                  │
│        · 关键词权重: 0.3                                │
│        · 标签权重: 0.2                                  │
│             │                                           │
│             ▼                                           │
│        相似度过滤 (threshold: 0.65)                     │
│             │                                           │
│             ▼                                           │
│        Top-5 结果注入 LLM Prompt                        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    3. 知识回流（自学习）                  │
│                                                         │
│  审查完成（评分 ≥ 80）                                  │
│      │                                                  │
│      ▼                                                  │
│  自动提取问题代码 + 修复代码                             │
│      │                                                  │
│      ▼                                                  │
│  结构化 → 向量化 → 入库 (is_verified=0)                 │
│      │                                                  │
│      ▼                                                  │
│  人工审核 → is_verified=1 → 正式生效                    │
│                                                         │
│  效果：知识库随使用持续进化，越用越聪明                   │
└─────────────────────────────────────────────────────────┘
```

### 7.3 检索算法详解

#### 向量语义召回（Vector Search）

```java
// VectorStoreService.search() 的核心算法
public List<SearchResult> search(List<Float> queryVector, int topK) {
    // 小顶堆：堆顶是当前 K 个中最小的
    PriorityQueue<SearchResult> pq = new PriorityQueue<>(
        Comparator.comparingDouble(SearchResult::score)
    );

    for (RagKnowledgeChunk chunk : allChunks) {
        // 从缓存或 DB 加载向量
        List<Float> chunkVec = getOrLoadEmbedding(chunk);
        // 计算余弦相似度
        double similarity = cosineSimilarity(queryVector, chunkVec);

        if (pq.size() < topK) {
            pq.offer(new SearchResult(chunk, similarity));
        } else if (similarity > pq.peek().score()) {
            pq.poll();        // 移除堆顶（最小的）
            pq.offer(new SearchResult(chunk, similarity));
        }
    }
    // 结果按相似度降序排列
}
```

**时间复杂度分析**：遍历 N 个 chunk，每次堆操作 O(log K)，总复杂度 **O(N log K)**。

#### 余弦相似度计算

```java
// EmbeddingClient.cosineSimilarity()
public double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
    double dot = 0, norm1 = 0, norm2 = 0;
    for (int i = 0; i < vec1.size(); i++) {
        dot += vec1.get(i) * vec2.get(i);    // 点积
        norm1 += vec1.get(i) * vec1.get(i);   // 模平方
        norm2 += vec2.get(i) * vec2.get(i);
    }
    return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
}
```

#### 多路融合加权

```
融合得分 = 向量归一化得分 × 0.5
         + 关键词排名归一化得分 × 0.3
         + 标签匹配得分 × 0.2

如果同一 chunk 被多路命中 → 得分累加（boost 效果）
```

### 7.4 智能文本分块

```java
// TextChunker.chunk() - 基于段落边界的语义分块
public List<String> chunk(String text) {
    // 以 \n\n 为段落边界
    String[] paragraphs = text.split("\n\n");

    for (String para : paragraphs) {
        if (currentTokenEstimate + paraTokens > chunkSize) {
            // 当前 chunk 已满，输出
            chunks.add(current);
            // 保留 overlap（上一 chunk 末尾内容）
            String overlap = current.substring(current.length() - chunkOverlap);
            current = new StringBuilder(overlap);
        }
        current.append(para);
    }
}
```

---

## 8. 数据库设计

### 8.1 ER 图

```
┌──────────────────────┐       ┌──────────────────────┐
│   code_review_task   │ 1───N │  code_review_detail   │
│──────────────────────│       │──────────────────────│
│ id (PK)              │       │ id (PK)              │
│ task_no (UK)         │       │ task_id (FK)         │
│ original_code        │       │ risk_level           │
│ preprocessed_code    │       │ problem_type         │
│ code_score           │       │ line_num             │
│ high/mid/low_risk_cnt│       │ problem_desc         │
│ rag_hit_count        │       │ fix_code             │
│ report_json          │       │ rag_ref_id (FK)      │
│ task_status          │       │ is_from_rule/llm     │
│ submit_by            │       └──────────────────────┘
└──────────────────────┘
                                    ┌──────────────────────┐
┌──────────────────────┐            │  rag_knowledge_chunk  │
│   code_review_rule   │            │──────────────────────│
│──────────────────────│            │ id (PK)              │
│ id (PK)              │            │ doc_id (FK)          │
│ rule_name            │            │ chunk_index          │
│ rule_type            │            │ chunk_content        │
│ rule_category        │            │ embedding (向量)     │
│ check_pattern (正则) │            │ hit_count            │
│ severity             │            │ quality_score        │
│ status               │            │ is_verified          │
└──────────────────────┘            └──────────────────────┘
                                               │
┌──────────────────────┐                      N│
│   rag_knowledge_doc  │ 1─────────────────────┘
│──────────────────────│
│ id (PK)              │       ┌──────────────────────┐
│ doc_name             │       │  rag_retrieval_log    │
│ doc_type             │       │──────────────────────│
│ doc_content          │       │ id (PK)              │
│ chunk_count          │       │ task_id              │
│ doc_status           │       │ retrieval_method     │
└──────────────────────┘       │ result_chunk_ids     │
                               │ retrieval_cost_ms    │
                               └──────────────────────┘
```

### 8.2 关键设计点

**1. 任务状态流转**：`0(处理中) → 1(已完成) / 2(失败)`

**2. JSON 字段存储**：`rule_check_json`、`llm_review_json`、`rag_context_json`、`report_json` 使用 JSON 类型存储，保留原始数据便于回溯。

**3. 向量存储兼容**：`rag_knowledge_chunk.embedding` 在 MySQL 中为 LONGTEXT（存 JSON 数组），Pgvector 下为 vector(1024)，Milvus 下为 NULL（向量由外部管理）。

**4. 默认 17 条审查规则**：

| 编号 | 规则名 | 类型 | 风险 |
|------|--------|------|------|
| 1 | 类名大驼峰校验 | STYLE | MEDIUM |
| 2 | 方法名小驼峰校验 | STYLE | MEDIUM |
| 3 | 常量全大写校验 | STYLE | LOW |
| 4 | 魔法值检查 | STYLE | MEDIUM |
| 5 | 未使用导入检查 | STYLE | LOW |
| 6 | 空指针风险检查 | BUG | HIGH |
| 7 | 异常空捕获检查 | BUG | HIGH |
| 8 | 资源未关闭检查 | BUG | HIGH |
| 9 | 循环内数据库查询 | PERFORMANCE | HIGH |
| 10 | 字符串循环拼接 | PERFORMANCE | MEDIUM |
| 11 | 集合初始化容量 | PERFORMANCE | LOW |
| 12 | SQL注入风险检查 | SECURITY | HIGH |
| 13 | 敏感信息硬编码 | SECURITY | HIGH |
| 14 | 日志脱敏检查 | SECURITY | MEDIUM |
| 15 | 方法过长检查 | DESIGN | MEDIUM |
| 16 | 参数过多检查 | DESIGN | MEDIUM |
| 17 | if嵌套过深 | DESIGN | LOW |

---

## 9. API 接口设计

### 9.1 审查相关接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/code/review/submit` | 提交代码审查（异步） |
| GET | `/agent/code/review/task/list` | 分页查询任务列表 |
| GET | `/agent/code/review/task/{taskId}` | 查询任务详情 |
| GET | `/agent/code/review/detail/{taskId}` | 查询问题明细 |
| GET | `/agent/code/review/report/{taskId}` | 获取审查报告 |
| POST | `/agent/code/review/rule/save` | 新增/更新规则 |
| GET | `/agent/code/review/rule/list` | 查询规则列表 |
| DELETE | `/agent/code/review/rule/{ruleId}` | 禁用规则（软删除） |
| GET | `/agent/code/review/stat` | 代码质量统计 |

### 9.2 RAG 知识库接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/rag/doc/upload` | 上传知识文档 |
| GET | `/agent/rag/doc/list` | 文档列表 |
| GET | `/agent/rag/doc/chunks/{docId}` | 查看文档分块 |
| DELETE | `/agent/rag/doc/{docId}` | 删除文档（级联） |
| POST | `/agent/rag/doc/{docId}/reindex` | 重建向量索引 |
| POST | `/agent/rag/search/test` | 检索测试 |
| POST | `/agent/rag/feedback` | 知识反馈（点赞/点踩） |
| POST | `/agent/rag/feedback/submit` | 审查案例回流入库 |

### 9.3 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 9.4 审查报告响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1,
    "taskNo": "CR-1234567890-abc12345",
    "codeName": "UserService.java",
    "codeType": "Java",
    "codeScore": 78,
    "reviewSummary": "代码整体质量中等，存在SQL注入风险和空指针隐患...",
    "riskSummary": { "high": 2, "medium": 5, "low": 3, "total": 10 },
    "problems": [
      {
        "detailId": 1,
        "riskLevel": "HIGH",
        "problemType": "SECURITY",
        "lineNum": "45",
        "problemCode": "String sql = \"select * from user where id=\" + id;",
        "problemDesc": "存在SQL注入风险，使用字符串拼接构造SQL",
        "riskEffect": "攻击者可注入恶意SQL语句获取敏感数据",
        "optimizeSuggest": "使用PreparedStatement预编译语句替代字符串拼接",
        "fixCode": "String sql = \"select * from user where id=?\";\nPreparedStatement ps = conn.prepareStatement(sql);",
        "ragRefTitle": "企业SQL安全编码规范 V2.3"
      }
    ],
    "ragReferences": [
      {
        "title": "企业SQL安全编码规范 V2.3",
        "type": "STANDARD",
        "content": "所有数据库操作必须使用预编译语句，禁止字符串拼接SQL..."
      }
    ]
  }
}
```

---

## 10. 关键代码深度剖析

### 10.1 LLMClient：OkHttp 原生 HTTP 调用

```java
// 为什么用 OkHttp 而不用 Spring RestTemplate？
// 1. 更灵活的连接池和超时控制
// 2. 支持同步/异步切换
// 3. 更好的流式响应支持（后续接流式LLM）

@Component
public class LLMClient {
    private final OkHttpClient httpClient;

    public LLMClient(LLMConfig llmConfig) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)     // 连接超时
            .readTimeout(llmConfig.getTimeoutSeconds(), TimeUnit.SECONDS)  // 读取超时（大代码审查需更长）
            .writeTimeout(120, TimeUnit.SECONDS)       // 写入超时
            .build();
    }

    public String chat(String systemPrompt, String userMessage) {
        // 构建 OpenAI 兼容格式的请求体
        JSONObject body = new JSONObject();
        body.put("model", llmConfig.getModel());           // deepseek-v4-flash
        body.put("messages", msgArray);                    // [system, user]
        body.put("temperature", 0.1);                      // 低温度 = 更确定性输出
        body.put("max_tokens", 4096);

        // 带退避的重试逻辑（最多重试 2 次）
        for (int attempt = 0; attempt <= llmConfig.getMaxRetry(); attempt++) {
            try {
                return doChat(body);
            } catch (Exception e) {
                if (attempt == llmConfig.getMaxRetry()) throw e;
                Thread.sleep(2000L * (attempt + 1));  // 指数退避: 2s, 4s
            }
        }
    }
}
```

**设计要点**：
- Temperature=0.1：代码审查需要**确定性**输出，低温度减少随机性
- 指数退避重试：2s → 4s，避免瞬时故障导致审查失败
- JSON 响应清洗：LLM 经常在 JSON 外加 ```json ... ``` 标记，需要 strip 处理

### 10.2 RuleChecker：正则引擎实现原理

```java
@Component
public class RuleChecker {
    // 每次审查实时从 DB 查询启用规则 → 规则变更无需重启
    public List<JSONObject> check(String code) {
        List<CodeReviewRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<CodeReviewRule>()
                .eq(CodeReviewRule::getStatus, 1)         // 仅启用状态
                .eq(CodeReviewRule::getRuleCategory, "BASIC") // 仅基础规则
                .orderByAsc(CodeReviewRule::getSortOrder)
        );

        for (CodeReviewRule rule : rules) {
            Pattern pattern = Pattern.compile(rule.getCheckPattern());
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                // 根据字符位置反算行号
                int lineNum = findLineNumber(code, matcher.start());
                // 构建标准问题 JSON
                JSONObject problem = new JSONObject();
                problem.put("lineNum", String.valueOf(lineNum));
                problem.put("problemType", rule.getRuleType());
                problem.put("riskLevel", rule.getSeverity());
                // ...
            }
        }
    }

    // 字符位置 → 行号（统计前面的 \n 数量）
    private int findLineNumber(String code, int pos) {
        int lineNum = 1;
        for (int i = 0; i < pos && i < code.length(); i++) {
            if (code.charAt(i) == '\n') lineNum++;
        }
        return lineNum;
    }
}
```

**设计要点**：
- 规则从 DB 实时查询 → 无需重启即可增删启停规则
- 单条规则异常不中断整体校验
- 正则匹配后反算行号 → 精确定位问题位置

### 10.3 AgentOrchestrator：问题去重算法

```java
// 合并规则校验和 LLM 审查的结果，去重
private List<JSONObject> mergeProblems(
        List<JSONObject> ruleProblems,
        List<JSONObject> llmProblems) {

    List<JSONObject> merged = new ArrayList<>(ruleProblems);

    for (JSONObject llm : llmProblems) {
        boolean duplicate = false;
        for (JSONObject existing : merged) {
            if (isSimilarProblem(existing, llm)) {
                duplicate = true;
                enrichExisting(existing, llm);  // LLM 优化信息并入规则问题
                break;
            }
        }
        if (!duplicate) merged.add(llm);
    }
    return merged;
}

private boolean isSimilarProblem(JSONObject a, JSONObject b) {
    // 策略 1: 同行号 + 同类型 → 一定重复
    if (sameLine && sameType) return true;
    // 策略 2: 描述文本 70% 以上字符相同 → 认为重复
    if (matchChars / minLen > 0.7) return true;
    return false;
}
```

**设计要点**：
- 规则问题优先保留（更可靠），LLM 优化信息并入
- 双重去重策略：精确定位 + 语义相似

### 10.4 VectorStoreService：小顶堆 Top-K 算法

这是项目中最值得学习的算法实现：

```java
public List<SearchResult> search(List<Float> queryVector, int topK) {
    // 小顶堆: 堆顶永远是当前 K 个中最小的
    PriorityQueue<SearchResult> pq = new PriorityQueue<>(
        Comparator.comparingDouble(SearchResult::score)  // 按得分升序
    );

    for (RagKnowledgeChunk chunk : allChunks) {
        double similarity = cosineSimilarity(queryVector, chunkVec);

        if (pq.size() < topK) {
            pq.offer(new SearchResult(chunk, similarity));  // 未满 K 个，直接加入
        } else if (similarity > pq.peek().score()) {
            pq.poll();                                       // 移除最小的
            pq.offer(new SearchResult(chunk, similarity));   // 加入更大的
        }
    }

    // 从小顶堆取出，按相似度降序排列
    List<SearchResult> results = new ArrayList<>(pq);
    results.sort((a, b) -> Double.compare(b.score(), a.score()));
    return results;
}
```

**为什么用小顶堆而不是全排序？**

假设有 10000 个 chunk，需要 Top-20：
- 全排序：O(N log N) = 10000 × log(10000) ≈ 132,877 次比较
- 小顶堆：O(N log K) = 10000 × log(20) ≈ 43,219 次比较

**3 倍的性能差异**。K 越小，优势越明显。

### 10.5 向量内存缓存设计

```java
@Component
public class VectorStoreService {
    // 使用 ConcurrentHashMap 缓存已加载的向量
    // 避免每次检索都从 JSON 字符串反序列化 1024 维向量
    private final Map<Long, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    private List<Float> getOrLoadEmbedding(RagKnowledgeChunk chunk) {
        return embeddingCache.computeIfAbsent(chunk.getId(), id -> {
            // 首次访问: 从 JSON 字符串反序列化并缓存
            return JsonParser.parseEmbedding(chunk.getEmbedding());
        });
    }
}
```

**性能影响**：1024 维向量，每个 Float 4 字节，JSON 字符串约 8KB。10000 个 chunk 的向量数据约 80MB，ConcurrentHashMap 缓存完全可行。

---

## 11. 线程池与异步任务机制

### 11.1 为什么需要异步？

大代码审查 + LLM 调用可能耗时 10-30 秒。如果同步处理：
- 前端请求超时
- Tomcat 工作线程被长时间占用
- 并发能力极差

### 11.2 线程池配置

```java
@Bean("reviewExecutor")
public Executor reviewExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);          // 核心线程数
    executor.setMaxPoolSize(8);           // 最大线程数
    executor.setQueueCapacity(200);       // 阻塞队列容量
    executor.setThreadNamePrefix("code-review-");  // 线程名前缀
    // 拒绝策略: 调用者运行（防止任务丢失）
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // 优雅关闭: 等待进行中任务完成
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    return executor;
}
```

### 11.3 异步执行流程

```
用户提交代码
    │
    ▼
Controller.submitReview()
    │
    ├── orchestrator.createTask()    // 1. 同步创建任务（快速）
    │       → 生成 taskNo
    │       → 写入 DB (status=0 处理中)
    │       → 立即返回 taskNo 给前端
    │
    └── orchestrator.executeReview() // 2. 异步执行审查
            → @Async("reviewExecutor")
            → 在 code-review-1 线程中执行 8 步审查
            → 完成后更新 DB (status=1 已完成)

前端拿到 taskNo 后：
    → 每 2 秒轮询 GET /agent/code/review/task/{taskId}
    → 当 taskStatus=1 时获取报告
```

---

## 12. 容错降级策略

### 12.1 三层降级

```
┌─────────────────────────────────────────────────────┐
│ Level 1: LLM 调用失败                                │
│ → 降级为基础规则审查结果 + 错误提示                   │
│ → 用户至少能获得正则匹配发现的问题                    │
├─────────────────────────────────────────────────────┤
│ Level 2: RAG 检索失败                                │
│ → 降级为纯 LLM 审查（无知识上下文）                   │
│ → 审查继续进行，不影响基础流程                        │
├─────────────────────────────────────────────────────┤
│ Level 3: 单步异常处理                                │
│ → 单条规则正则异常 → 跳过该规则，继续下一条            │
│ → 单个问题优化失败 → 保持原问题，不丢失审查结果       │
│ → 顶层兜底 → 任务标记为失败，保留错误信息             │
└─────────────────────────────────────────────────────┘
```

### 12.2 关键容错代码

```java
// AgentOrchestrator.executeReview() 中的容错设计

// RAG 检索失败 → 降级
try {
    ragResult = retrievalService.hybridSearch(processed, ragTopK);
} catch (Exception e) {
    log.warn("RAG 检索失败，降级为纯 LLM 审查");
    task.setRagHitCount(0);
}

// LLM 调用失败 → 降级
// LLMCodeReviewer.review() 内部:
catch (Exception e) {
    // 返回降级结果，不抛出异常
    return new LLMReviewResult(0, "LLM 审查失败，已降级为基础规则审查", fallbackProblems, "{}");
}

// 顶层兜底
catch (Exception e) {
    task.setTaskStatus(2);  // 标记失败
    task.setErrorMsg(e.getMessage());
    // 不丢失已保存的预处理代码和规则校验结果
}
```

---

## 13. Prompt 工程

### 13.1 审查 Prompt 结构

```
┌──────────────────────────────────────┐
│ System Prompt（角色 + 规则 + 输出格式）│
│                                      │
│ "你是一个资深的Java代码审查专家..."    │
│                                      │
│ 1. 审查维度定义（STYLE/BUG/PERF/     │
│    SECURITY/DESIGN）                  │
│ 2. 企业编码规范（RAG 检索注入）       │
│ 3. 历史相似缺陷案例（RAG 检索注入）   │
│ 4. JSON 输出格式约束                  │
├──────────────────────────────────────┤
│ User Message（待审查代码）            │
│                                      │
│ ```java                             │
│ {用户提交的代码}                      │
│ ```                                  │
└──────────────────────────────────────┘
```

### 13.2 Prompt 设计要点

1. **低 Temperature (0.1)**：代码审查需要确定性，低温度减少 LLM "创造性发挥"
2. **严格 JSON 输出**：在 Prompt 中明确要求 `不要包含markdown代码块标记`，避免解析失败
3. **RAG 上下文占位**：通过 `String.format()` 动态注入检索结果
4. **兜底文本**：RAG 未命中时填充 `"暂无匹配的企业规范条文"`

### 13.3 优化 Prompt

```java
public static final String OPTIMIZE_PROMPT = """
    你是一个Java代码优化专家。请针对以下问题生成详细的优化方案。

    ## 原始代码
    ```java
    %s
    ```

    ## 发现的问题
    %s

    ## 企业最佳实践（RAG检索）
    %s

    ## 输出要求
    请按JSON格式输出优化方案:
    {
      "optimizePlan": "整体优化思路",
      "refactoredCode": "重构后的完整代码",
      "changes": [...],
      "bestPracticeRef": "引用的最佳实践"
    }
    """;
```

---

## 14. 部署与运行

### 14.1 快速启动步骤

```bash
# 1. 创建数据库
mysql -u root -p < sql/init.sql

# 2. 修改配置
vim src/main/resources/application.yml
# 重点修改:
#   - spring.datasource.url / username / password
#   - llm.api-key (大模型 API Key)
#   - llm.api-url (大模型 API 地址)
#   - embedding.api-endpoint (Embedding 服务地址)

# 3. 编译
mvn clean package -DskipTests

# 4. 启动
java -jar target/code-review-agent-1.0.0.jar

# 5. 或者开发模式
mvn spring-boot:run

# 6. 验证
curl -X POST http://localhost:8080/agent/code/review/submit \
  -H "Content-Type: application/json" \
  -d '{"codeContent":"public class Test { ... }", "codeType":"Java", "submitBy":"admin"}'
```

### 14.2 配置项说明

```yaml
# 大模型配置
llm:
  provider: deepseek         # 兼容 OpenAI API 格式的均可
  api-url: https://api.deepseek.com/v1/chat/completions
  api-key: sk-xxx            # 替换为实际 key
  model: deepseek-v4-flash
  temperature: 0.1           # 代码审查推荐低温度
  max-tokens: 4096
  max-retry: 2               # 失败重试次数

# RAG 配置
rag:
  chunk-size: 500            # 分块 token 数
  chunk-overlap: 80          # 相邻 chunk 重叠
  vector-recall-topk: 20     # 向量粗排召回数
  final-topk: 5              # 最终注入 LLM 的知识片段数
  similarity-threshold: 0.65 # 相似度阈值

# 线程池
async:
  core-pool-size: 4
  max-pool-size: 8
  queue-capacity: 200
  task-timeout-minutes: 10   # 单任务超时
```

---

## 15. 扩展学习路径

### 15.1 理解架构

- 阅读 `README.md` + `AgentOrchestrator.java`，理解 8 步审查闭环
- 阅读 `RuleChecker.java` + `LLMCodeReviewer.java`，理解双引擎审查
- 阅读 `RetrievalService.java` + `VectorStoreService.java`，理解 RAG 检索

### 15.2 深入实现

- `PromptTemplate.java` - 学习 Prompt 工程
- `LLMClient.java` + `EmbeddingClient.java` - 学习 OkHttp 客户端设计
- `TextChunker.java` + `DocumentParser.java` - 学习文档处理

### 15.3 动手实践

- **练习 1**：新增一条审查规则（如"禁止使用 `new Thread()` 创建线程"）
- **练习 2**：上传一份企业编码规范文档，验证 RAG 检索效果
- **练习 3**：修改 Prompt 模板，让 LLM 输出中文问题描述
- **练习 4**：接入 GitLab/GitHub Webhook，实现 PR 自动触发审查
- **练习 5**：添加审查结果缓存（Redis），相同代码不重复审查

### 15.4 进阶方向

- **向量库升级**：从 MySQL JSON 存储切换到 Pgvector 或 Milvus，获得真正的向量索引加速
- **多语言支持**：扩展审查规则和 Prompt 支持 Python、Go 等语言
- **流式响应**：OkHttp 改为 SSE 流式输出，实现审查进度实时展示
- **CI/CD 集成**：作为 GitHub Action 或 GitLab CI 插件运行
- **知识图谱**：将规范、案例、缺陷模式关联为知识图谱，实现多跳推理

---

## 附录 A：项目中使用的设计模式

| 设计模式 | 应用位置 | 说明 |
|---------|---------|------|
| **Builder 模式** | 所有 Entity、VO 类（Lombok @Builder） | 构建复杂对象 |
| **策略模式** | DocumentParser（按文件扩展名路由到不同解析器） | 不同格式文档的不同处理策略 |
| **模板方法** | AgentOrchestrator（8 步固定流程，每步可替换） | 审查流程固定，组件可插拔 |
| **单例模式** | Spring Bean 默认单例 | 服务组件全局唯一 |
| **责任链模式** | 审查流程 Step1→Step2→...→Step8 | 代码按顺序流经各处理节点 |
| **工厂模式** | PromptTemplate 的 buildXxxPrompt() 方法 | 根据不同场景构建不同 Prompt |

## 附录 B：核心流程图（文字版）

```
审查全流程：

  [用户提交代码]
       │
       ▼
  ┌─────────────┐
  │ Controller   │  POST /submit → 创建任务 → 立即返回 taskNo
  └──────┬──────┘
         │ @Async("reviewExecutor")
         ▼
  ┌──────────────────────────────────────────────────────┐
  │              AgentOrchestrator                        │
  │                                                      │
  │  ┌─────────┐  ┌──────────┐  ┌───────────┐           │
  │  │ 预处理   │→│ 规则校验  │→│ RAG 检索  │            │
  │  │(清洗格式)│  │(17条规则) │  │(向量+关键词)│          │
  │  └─────────┘  └──────────┘  └─────┬─────┘           │
  │                                   │                  │
  │         ┌─────────────────────────┘                  │
  │         ▼                                            │
  │  ┌───────────┐  ┌──────────┐  ┌──────────┐          │
  │  │ LLM 审查  │→│ 问题聚合  │→│ 代码优化  │          │
  │  │(含RAG上下文)│ │(去重合并) │  │(含最佳实践)│         │
  │  └───────────┘  └──────────┘  └────┬─────┘          │
  │                                    │                 │
  │         ┌──────────────────────────┘                 │
  │         ▼                                            │
  │  ┌───────────┐  ┌──────────┐                        │
  │  │ 报告生成  │→│ 数据持久化 │                       │
  │  │(评分+统计) │  │(DB入库)   │                        │
  │  └───────────┘  └──────────┘                        │
  └──────────────────────────────────────────────────────┘
       │
       ▼
  [前端轮询] → GET /report/{taskId} → 展示审查报告
```

---

> **学习建议**：建议按照"架构理解 → 核心代码阅读 → 动手修改 → 扩展功能"的顺序学习。重点关注 `AgentOrchestrator`（总调度）、`RetrievalService`（混合检索）、`LLMClient`（HTTP 客户端）三个核心类。

遇到任何问题，欢迎提 Issue 或 PR。
