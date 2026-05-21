package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

/**
 * RAG检索记录表实体
 *
 * <p>记录每次审查任务中的RAG检索行为，用于:
 * <ul>
 *   <li>检索效果监控: 跟踪命中率、检索耗时</li>
 *   <li>检索策略优化: 对比向量/关键词/混合检索的效果差异</li>
 *   <li>问题追溯: 当审查结果不理想时，回溯RAG检索是否有效</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_retrieval_log")
public class RagRetrievalLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的审查任务ID，0表示检索测试请求 */
    private Long taskId;

    /** 检索查询文本(提交审查的代码内容) */
    private String queryText;

    /** 检索方式: VECTOR(仅向量) / KEYWORD(仅关键词) / HYBRID(混合) */
    private String retrievalMethod;

    /** 请求返回的Top-K数量 */
    @Builder.Default
    private Integer topK = 5;

    /** 命中chunk的ID列表(逗号分隔) */
    private String resultChunkIds;

    /** 各命中结果的相似度得分列表(逗号分隔) */
    private String similarityScores;

    /** 检索总耗时(毫秒) */
    @Builder.Default
    private Integer retrievalCostMs = 0;

    /** 是否有命中结果: 0-无, 1-有 */
    @Builder.Default
    private Integer isHit = 0;

    /** 实际命中数量(经相似度阈值过滤后) */
    @Builder.Default
    private Integer hitCount = 0;

    /** 检索时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
