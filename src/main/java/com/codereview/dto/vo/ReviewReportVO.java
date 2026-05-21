package com.codereview.dto.vo;

import lombok.*;

import java.util.*;

/**
 * 审查报告响应VO
 *
 * <p>完整的审查报告视图，聚合任务基本信息、风险摘要、问题明细列表、
 * RAG知识引用和总体优化建议。前端可直接渲染为报告页面。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReportVO {

    /** 审查任务ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 代码文件名 */
    private String codeName;

    /** 代码语言类型 */
    private String codeType;

    /** 代码质量综合评分(0-100) */
    private Integer codeScore;

    /** LLM生成的审查总体总结(200字内) */
    private String reviewSummary;

    /** 风险分级统计 */
    private RiskSummary riskSummary;

    /** 问题清单(按风险等级排序) */
    private List<ProblemItem> problems;

    /** RAG知识库匹配的规范/案例引用列表 */
    private List<RagReference> ragReferences;

    /** 整体优化建议摘要 */
    private String optimizeSummary;

    /**
     * 风险分级统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskSummary {
        /** 高风险问题数 */
        private int high;
        /** 中风险问题数 */
        private int medium;
        /** 低风险问题数 */
        private int low;
        /** 问题总数 */
        private int total;
    }

    /**
     * 单个问题明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemItem {
        /** 问题明细ID */
        private Long detailId;
        /** 风险等级 */
        private String riskLevel;
        /** 问题类型 */
        private String problemType;
        /** 问题行号 */
        private String lineNum;
        /** 问题代码片段 */
        private String problemCode;
        /** 问题描述 */
        private String problemDesc;
        /** 风险影响说明 */
        private String riskEffect;
        /** 优化建议 */
        private String optimizeSuggest;
        /** AI生成的修复代码 */
        private String fixCode;
        /** 引用的RAG知识标题 */
        private String ragRefTitle;
    }

    /**
     * RAG知识库引用
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReference {
        /** 知识文档标题 */
        private String title;
        /** 知识类型 */
        private String type;
        /** 知识内容摘要 */
        private String content;
    }
}
