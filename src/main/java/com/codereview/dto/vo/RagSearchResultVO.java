package com.codereview.dto.vo;

import lombok.*;

import java.math.*;
import java.util.*;

/**
 * RAG检索结果响应VO
 *
 * <p>返回检索命中的知识片段列表，每个片段包含来源文档、内容、
 * 相似度得分和质量评分。用于检索效果测试和管理员调优检索参数。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchResultVO {

    /** 原始查询文本 */
    private String queryText;

    /** 命中总数(经相似度阈值过滤后) */
    private int totalHits;

    /** 检索耗时(毫秒) */
    private int retrievalCostMs;

    /** 命中知识片段列表(按相似度降序) */
    private List<HitItem> hits;

    /**
     * 命中知识片段明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HitItem {
        /** 知识块ID */
        private Long chunkId;
        /** 来源文档ID */
        private Long docId;
        /** 来源文档名称 */
        private String docName;
        /** 知识类型: STANDARD/CASE/PRACTICE/PATTERN */
        private String docType;
        /** 匹配的文本内容 */
        private String chunkContent;
        /** 内容摘要 */
        private String chunkSummary;
        /** 向量相似度得分(0.0-1.0) */
        private double similarityScore;
        /** 知识质量评分(0.00-5.00) */
        private BigDecimal qualityScore;
        /** 是否经过人工验证 */
        private boolean isVerified;
    }
}
