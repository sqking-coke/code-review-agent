-- =============================================
-- 代码智能审查Agent - 数据库初始化脚本
-- 适用数据库: MySQL 8.0 + Pgvector (或独立使用MySQL)
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS code_review_agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE code_review_agent;

-- =============================================
-- 1. 代码审查任务主表
-- =============================================
DROP TABLE IF EXISTS code_review_task;
CREATE TABLE code_review_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    task_no         VARCHAR(64)     NOT NULL                 COMMENT '审查任务编号',
    code_name       VARCHAR(255)    DEFAULT NULL             COMMENT '代码文件名称',
    code_type       VARCHAR(32)     NOT NULL DEFAULT 'Java'  COMMENT '代码语言类型',
    original_code   LONGTEXT        NOT NULL                 COMMENT '原始提交代码',
    preprocessed_code LONGTEXT      DEFAULT NULL             COMMENT '预处理后代码',
    code_score      INT             DEFAULT NULL             COMMENT '代码质量评分 0-100',
    high_risk_count INT             NOT NULL DEFAULT 0       COMMENT '高风险问题数量',
    mid_risk_count  INT             NOT NULL DEFAULT 0       COMMENT '中风险问题数量',
    low_risk_count  INT             NOT NULL DEFAULT 0       COMMENT '低风险问题数量',
    rag_hit_count   INT             NOT NULL DEFAULT 0       COMMENT 'RAG知识命中数量',
    review_summary  LONGTEXT        DEFAULT NULL             COMMENT '审查总体总结',
    rule_check_json JSON            DEFAULT NULL             COMMENT '基础规则校验结果JSON',
    llm_review_json JSON            DEFAULT NULL             COMMENT 'LLM语义审查原始结果JSON',
    rag_context_json JSON           DEFAULT NULL             COMMENT 'RAG检索上下文JSON',
    report_json     JSON            DEFAULT NULL             COMMENT '完整审查报告JSON',
    task_status     TINYINT         NOT NULL DEFAULT 0       COMMENT '任务状态: 0处理中 1已完成 2失败',
    error_msg       TEXT            DEFAULT NULL             COMMENT '失败原因',
    submit_by       VARCHAR(64)     DEFAULT NULL             COMMENT '提交人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_task_status (task_status),
    KEY idx_create_time (create_time),
    KEY idx_submit_by (submit_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码审查任务主表';


-- =============================================
-- 2. 代码审查问题明细表
-- =============================================
DROP TABLE IF EXISTS code_review_detail;
CREATE TABLE code_review_detail (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    task_id         BIGINT          NOT NULL                 COMMENT '关联审查任务ID',
    risk_level      VARCHAR(16)     NOT NULL                 COMMENT '风险等级: HIGH/MEDIUM/LOW',
    problem_type    VARCHAR(32)     NOT NULL                 COMMENT '问题类型: STYLE/BUG/PERFORMANCE/SECURITY/DESIGN',
    line_num        VARCHAR(64)     DEFAULT NULL             COMMENT '问题代码行号',
    problem_code    TEXT            DEFAULT NULL             COMMENT '问题代码片段',
    problem_desc    TEXT            NOT NULL                 COMMENT '问题详细描述',
    risk_effect     TEXT            DEFAULT NULL             COMMENT '风险影响说明',
    optimize_suggest TEXT           DEFAULT NULL             COMMENT '优化建议',
    fix_code        LONGTEXT        DEFAULT NULL             COMMENT '修复后代码',
    rag_ref_id      BIGINT          DEFAULT NULL             COMMENT '关联的RAG知识点ID',
    rag_ref_title   VARCHAR(255)    DEFAULT NULL             COMMENT '引用的RAG知识标题',
    rag_ref_type    VARCHAR(32)     DEFAULT NULL             COMMENT 'RAG知识类型',
    is_from_rule    TINYINT         NOT NULL DEFAULT 0       COMMENT '是否来自基础规则: 0否 1是',
    is_from_llm     TINYINT         NOT NULL DEFAULT 0       COMMENT '是否来自LLM审查: 0否 1是',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '排序序号',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_risk_level (risk_level),
    KEY idx_problem_type (problem_type),
    KEY idx_rag_ref_id (rag_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码审查问题明细表';


-- =============================================
-- 3. 代码审查规则配置表
-- =============================================
DROP TABLE IF EXISTS code_review_rule;
CREATE TABLE code_review_rule (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    rule_name       VARCHAR(128)    NOT NULL                 COMMENT '规则名称',
    rule_type       VARCHAR(32)     NOT NULL                 COMMENT '规则分类: STYLE/BUG/PERFORMANCE/SECURITY/DESIGN',
    rule_category   VARCHAR(32)     NOT NULL DEFAULT 'BASIC' COMMENT '规则类别: BASIC基础规则/AI语义规则',
    rule_content    TEXT            NOT NULL                 COMMENT '规则详情描述',
    check_pattern   VARCHAR(512)    DEFAULT NULL             COMMENT '校验正则表达式',
    severity        VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度: HIGH/MEDIUM/LOW',
    language        VARCHAR(32)     NOT NULL DEFAULT 'Java'  COMMENT '适用语言',
    example_code    TEXT            DEFAULT NULL             COMMENT '示例代码(错误示例)',
    fix_example     TEXT            DEFAULT NULL             COMMENT '修复示例代码',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '排序序号',
    status          TINYINT         NOT NULL DEFAULT 1       COMMENT '状态: 0禁用 1启用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_rule_type (rule_type),
    KEY idx_status (status),
    KEY idx_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码审查规则配置表';


-- =============================================
-- 4. RAG知识文档表
-- =============================================
DROP TABLE IF EXISTS rag_knowledge_doc;
CREATE TABLE rag_knowledge_doc (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    doc_name        VARCHAR(255)    NOT NULL                 COMMENT '文档名称',
    doc_type        VARCHAR(32)     NOT NULL                 COMMENT '知识类型: STANDARD规范/CASE案例/PRACTICE实践/PATTERN缺陷模式',
    doc_language    VARCHAR(32)     NOT NULL DEFAULT '通用'   COMMENT '适用语言',
    doc_format      VARCHAR(16)     NOT NULL                 COMMENT '原始格式: md/pdf/txt/docx',
    doc_content     LONGTEXT        NOT NULL                 COMMENT '原始文档内容',
    doc_summary     VARCHAR(512)    DEFAULT NULL             COMMENT '文档摘要',
    chunk_count     INT             NOT NULL DEFAULT 0       COMMENT '分块数量',
    source          VARCHAR(255)    DEFAULT NULL             COMMENT '文档来源',
    doc_tags        VARCHAR(512)    DEFAULT NULL             COMMENT '文档标签JSON数组',
    doc_status      TINYINT         NOT NULL DEFAULT 0       COMMENT '状态: 0处理中 1已向量化 2已废弃',
    vector_store    VARCHAR(32)     NOT NULL DEFAULT 'PGVECTOR' COMMENT '向量存储类型: PGVECTOR/MILVUS',
    create_by       VARCHAR(64)     DEFAULT NULL             COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_doc_type (doc_type),
    KEY idx_doc_language (doc_language),
    KEY idx_doc_status (doc_status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG知识文档表';


-- =============================================
-- 5. RAG知识块表
-- 说明: embedding字段使用Pgvector的vector(1024)类型。
--       若仅使用MySQL，可改为LONGTEXT存储向量JSON；
--       若使用Milvus，此表仅存文本元数据，向量由Milvus管理。
-- =============================================
DROP TABLE IF EXISTS rag_knowledge_chunk;
CREATE TABLE rag_knowledge_chunk (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    doc_id          BIGINT          NOT NULL                 COMMENT '关联文档ID',
    chunk_index     INT             NOT NULL                 COMMENT '分块序号(从0开始)',
    chunk_content   TEXT            NOT NULL                 COMMENT '分块文本内容',
    chunk_summary   VARCHAR(512)    DEFAULT NULL             COMMENT '分块内容摘要',
    token_count     INT             NOT NULL DEFAULT 0       COMMENT 'Token数量估算',
    -- embedding字段: Pgvector环境下为 vector(1024), 纯MySQL环境改为 LONGTEXT COMMENT '向量数据JSON'
    embedding       LONGTEXT        DEFAULT NULL             COMMENT '向量数据(JSON数组格式，兼容MySQL); Pgvector下为vector(1024)',
    embedding_model VARCHAR(64)     DEFAULT NULL             COMMENT 'Embedding模型名称',
    meta_tags       VARCHAR(512)    DEFAULT NULL             COMMENT '元数据标签JSON',
    hit_count       INT             NOT NULL DEFAULT 0       COMMENT '检索命中次数',
    last_hit_time   DATETIME        DEFAULT NULL             COMMENT '最近命中时间',
    quality_score   DECIMAL(3,2)    NOT NULL DEFAULT 0.00    COMMENT '知识质量评分 0.00-5.00',
    is_verified     TINYINT         NOT NULL DEFAULT 0       COMMENT '是否人工验证: 0否 1是',
    feedback_score  INT             DEFAULT NULL             COMMENT '用户反馈评分: 1正向/-1负向',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_doc_id (doc_id),
    KEY idx_chunk_index (doc_id, chunk_index),
    KEY idx_hit_count (hit_count),
    KEY idx_quality_score (quality_score),
    KEY idx_is_verified (is_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG知识块表';


-- =============================================
-- 6. RAG检索记录表
-- =============================================
DROP TABLE IF EXISTS rag_retrieval_log;
CREATE TABLE rag_retrieval_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    task_id         BIGINT          NOT NULL                 COMMENT '关联审查任务ID',
    query_text      TEXT            NOT NULL                 COMMENT '检索查询文本',
    retrieval_method VARCHAR(32)    NOT NULL                 COMMENT '检索方式: VECTOR向量/KEYWORD关键词/HYBRID混合',
    top_k           INT             NOT NULL DEFAULT 5       COMMENT '检索返回数量',
    result_chunk_ids VARCHAR(1024)  DEFAULT NULL             COMMENT '命中chunk ID列表(逗号分隔)',
    similarity_scores VARCHAR(512)  DEFAULT NULL             COMMENT '相似度得分列表(逗号分隔)',
    retrieval_cost_ms INT           NOT NULL DEFAULT 0       COMMENT '检索耗时(毫秒)',
    is_hit          TINYINT         NOT NULL DEFAULT 0       COMMENT '是否有命中: 0否 1是',
    hit_count       INT             NOT NULL DEFAULT 0       COMMENT '实际命中数量',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检索时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_retrieval_method (retrieval_method),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG检索记录表';


-- =============================================
-- 初始化默认审查规则
-- =============================================
INSERT INTO code_review_rule (rule_name, rule_type, rule_category, rule_content, check_pattern, severity, language, example_code, fix_example, sort_order, status) VALUES
-- 命名规范
('类名大驼峰校验', 'STYLE', 'BASIC', '类名必须使用大驼峰命名法(UpperCamelCase)，每个单词首字母大写', 'class\\s+([a-z][a-zA-Z0-9]*)', 'MEDIUM', 'Java', 'class userService { }', 'class UserService { }', 1, 1),
('方法名小驼峰校验', 'STYLE', 'BASIC', '方法名必须使用小驼峰命名法(lowerCamelCase)，首单词小写', '\\s+(void|int|String|boolean|long|double|List|Map|Set)\\s+([A-Z][a-zA-Z0-9]*)\\s*\\(', 'MEDIUM', 'Java', 'public void GetUser() { }', 'public void getUser() { }', 2, 1),
('常量全大写校验', 'STYLE', 'BASIC', '常量必须全部大写，单词间用下划线分隔', 'static\\s+final\\s+(String|int|long)\\s+([a-z][a-zA-Z0-9]*)', 'LOW', 'Java', 'static final String defaultPath = "/tmp";', 'static final String DEFAULT_PATH = "/tmp";', 3, 1),

-- 代码格式
('魔法值检查', 'STYLE', 'BASIC', '代码中不允许出现未定义的魔法值，数字和字符串应定义为常量', '\\b(?!0|1|-1|null|true|false|0\\.0)(\\d{2,}|\"[^\"]{20,}\")\\b', 'MEDIUM', 'Java', 'if (count > 100) { }', 'private static final int MAX_COUNT = 100;\nif (count > MAX_COUNT) { }', 4, 1),
('未使用导入检查', 'STYLE', 'BASIC', '不应存在未使用的import语句', '^import\\s+(?!.*used).*;$', 'LOW', 'Java', 'import java.util.List; // 未使用', '移除未使用的import', 5, 1),

-- BUG风险
('空指针风险检查', 'BUG', 'BASIC', '方法返回值直接调用方法前应进行null判断', '\\.(get|stream|getString|getInt|getLong|getBoolean)\\s*\\(.*\\)\\s*\\.\\s*[a-zA-Z]', 'HIGH', 'Java', 'user.getAddress().getCity();', 'Address addr = user.getAddress();\nif (addr != null) {\n    String city = addr.getCity();\n}', 6, 1),
('异常空捕获检查', 'BUG', 'BASIC', '不应存在空的catch块，异常必须被处理或记录', 'catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}', 'HIGH', 'Java', 'try { ... } catch(Exception e) { }', 'try { ... } catch(Exception e) {\n    log.error("error", e);\n    throw new BusinessException("xxx");\n}', 7, 1),
('资源未关闭检查', 'BUG', 'BASIC', 'IO流、连接等资源必须在finally或try-with-resources中关闭', '(new\\s+(FileInputStream|FileOutputStream|BufferedReader|Connection|Statement|ResultSet)\\s*\\([^)]*\\))', 'HIGH', 'Java', 'FileInputStream fis = new FileInputStream(file);', 'try (FileInputStream fis = new FileInputStream(file)) { ... }', 8, 1),

-- 性能隐患
('循环内数据库查询', 'PERFORMANCE', 'BASIC', '循环体内不应执行数据库查询操作', 'for\\s*\\([^)]*\\)\\s*\\{[^}]*\\.(select|query|find|get)\\s*\\(', 'HIGH', 'Java', 'for (User u : list) { dao.getOrders(u.getId()); }', 'List<Long> ids = list.stream().map(User::getId).collect(toList());\nList<Order> orders = dao.getOrdersByIds(ids);', 9, 1),
('字符串循环拼接', 'PERFORMANCE', 'BASIC', '循环内字符串拼接应使用StringBuilder', 'for\\s*\\([^)]*\\)\\s*\\{[^}]*\\+=\\s*\"', 'MEDIUM', 'Java', 'for (String s : list) { result += s; }', 'StringBuilder sb = new StringBuilder();\nfor (String s : list) { sb.append(s); }', 10, 1),
('集合初始化容量', 'PERFORMANCE', 'BASIC', '已知大小的ArrayList/HashMap建议指定初始容量', 'new\\s+(ArrayList|HashMap)\\s*\\(\\s*\\)\\s*;(?=[^}]*\\.(add|put)\\s*\\()', 'LOW', 'Java', 'List<String> list = new ArrayList<>();\nfor (...) list.add(...);', 'List<String> list = new ArrayList<>(expectedSize);', 11, 1),

-- 安全漏洞
('SQL注入风险检查', 'SECURITY', 'BASIC', '应使用预编译语句，禁止SQL字符串拼接', '(\"\\s*select.*\\+\\s*\"|\"\\s*delete.*\\+\\s*\"|\"\\s*update.*\\+\\s*\")', 'HIGH', 'Java', 'String sql = "select * from user where id=" + id;', 'String sql = "select * from user where id=?";\nPreparedStatement ps = conn.prepareStatement(sql);', 12, 1),
('敏感信息硬编码', 'SECURITY', 'BASIC', '密码、密钥、Token等敏感信息不得硬编码在代码中', '(password|secret|token|apikey|api_key)\\s*=\\s*\"[^\"]{6,}\"', 'HIGH', 'Java', 'String password = "admin123";', 'String password = System.getenv("DB_PASSWORD");', 13, 1),
('日志脱敏检查', 'SECURITY', 'BASIC', '日志中不应打印手机号、身份证等敏感信息', 'log\\.[a-z]+\\([^)]*(phone|idCard|password|secret)', 'MEDIUM', 'Java', 'log.info("user mobile:{}", phone);', 'log.info("user mobile:{}", maskPhone(phone));', 14, 1),

-- 代码设计
('方法过长检查', 'DESIGN', 'BASIC', '单个方法不应超过80行（复杂度提示）', NULL, 'MEDIUM', 'Java', '单方法超过80行', '按职责拆分为多个小方法', 15, 1),
('参数过多检查', 'DESIGN', 'BASIC', '方法参数不应超过5个', '\\w+\\s+\\w+\\s*\\([^)]{100,}\\)', 'MEDIUM', 'Java', 'void save(String a, String b, String c, String d, String e, String f) {}', '封装为DTO对象传递', 16, 1),
('if嵌套过深', 'DESIGN', 'BASIC', 'if嵌套层级不应超过3层（圈复杂度提示）', NULL, 'LOW', 'Java', '3层以上if嵌套', '使用卫语句(Guard Clauses)提前return', 17, 1);

-- =============================================
-- Pgvector 扩展启用（如使用PostgreSQL + Pgvector）
-- =============================================
-- CREATE EXTENSION IF NOT EXISTS vector;
--
-- 若使用 Pgvector, 将 rag_knowledge_chunk 表的 embedding 字段改为:
-- ALTER TABLE rag_knowledge_chunk MODIFY COLUMN embedding vector(1024);
--
-- 并创建向量索引:
-- CREATE INDEX idx_chunk_embedding ON rag_knowledge_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
