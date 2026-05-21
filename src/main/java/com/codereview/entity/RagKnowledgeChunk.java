package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.*;
import java.time.*;

/**
 * RAG知识块表实体
 *
 * <p>每个知识文档({@link RagKnowledgeDoc})被切分为多个知识块(chunk)，
 * 每个chunk独立向量化存储，是向量检索的最小检索单元。</p>
 *
 * <p>向量存储:
 * <ul>
 *   <li>Pgvector模式: embedding字段存 vector(1024) 类型</li>
 *   <li>MySQL兼容模式: embedding字段存 JSON数组字符串 LONGTEXT</li>
 *   <li>Milvus模式: embedding字段为null，向量由外部Milvus管理</li>
 * </ul>
 * </p>
 *
 * <p>质量评分机制:
 * qualityScore 初始3.0, 根据用户反馈(feedbackScore)动态调整:
 * 正向反馈 +0.1, 负向反馈 -0.1, 区间 [0.00, 5.00]
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_knowledge_chunk")
public class RagKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的文档ID */
    private Long docId;

    /** 分块序号(从0开始递增) */
    private Integer chunkIndex;

    /** 分块文本内容 */
    private String chunkContent;

    /** 分块内容摘要(前300字符) */
    private String chunkSummary;

    /** 估算的Token数量(中文: 1字≈1token, 英文: 4字≈1token) */
    @Builder.Default
    private Integer tokenCount = 0;

    /** 向量数据: Pgvector为vector(1024)，MySQL为JSON数组字符串 */
    private String embedding;

    /** Embedding模型名称(如 bge-large-zh)，用于模型切换后识别需重建的向量 */
    private String embeddingModel;

    /** 元数据标签(JSON格式): 语言、框架、知识类型等 */
    private String metaTags;

    /** 累计被检索命中的次数(热度指标) */
    @Builder.Default
    private Integer hitCount = 0;

    /** 最近一次被检索命中的时间 */
    private LocalDateTime lastHitTime;

    /** 知识质量评分(0.00-5.00)，初始3.0，根据用户反馈动态调整 */
    @Builder.Default
    private BigDecimal qualityScore = BigDecimal.ZERO;

    /** 是否经过人工验证: 0-未验证, 1-已验证 */
    @Builder.Default
    private Integer isVerified = 0;

    /** 用户反馈评分: 1-正向(有用), -1-负向(无用), null-未反馈 */
    private Integer feedbackScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
