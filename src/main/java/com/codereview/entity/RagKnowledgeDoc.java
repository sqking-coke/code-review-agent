package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

/**
 * RAG知识文档表实体
 *
 * <p>RAG知识库的核心入口，存储上传的原始知识文档元信息。
 * 一个文档对应多个分块({@link RagKnowledgeChunk})，上传后自动触发
 * 文档解析 → 分块 → 向量化 → 入库流程。</p>
 *
 * <p>知识类型:
 * <ul>
 *   <li>STANDARD: 企业编码规范文档</li>
 *   <li>CASE: 历史BUG案例(含修复方案)</li>
 *   <li>PRACTICE: 最佳实践代码片段</li>
 *   <li>PATTERN: 常见缺陷模式</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_knowledge_doc")
public class RagKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档名称(文件名或自定义标题) */
    private String docName;

    /** 知识类型: STANDARD / CASE / PRACTICE / PATTERN */
    private String docType;

    /** 适用编程语言: Java / Python / Go / 通用 */
    @Builder.Default
    private String docLanguage = "通用";

    /** 原始文件格式: md / pdf / txt / docx */
    private String docFormat;

    /** 解析后的纯文本内容 */
    private String docContent;

    /** 文档内容摘要(前200字符) */
    private String docSummary;

    /** 分块数量 */
    @Builder.Default
    private Integer chunkCount = 0;

    /** 文档来源: 手动上传 / Git同步 / 审查回流 */
    private String source;

    /** 文档标签(JSON数组格式)，用于标签过滤检索 */
    private String docTags;

    /** 文档状态: 0-处理中, 1-已向量化(可用), 2-已废弃 */
    @Builder.Default
    private Integer docStatus = 0;

    /** 向量存储类型: PGVECTOR / MILVUS */
    @Builder.Default
    private String vectorStore = "PGVECTOR";

    /** 创建人标识 */
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
