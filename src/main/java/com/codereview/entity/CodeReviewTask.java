package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

/**
 * 代码审查任务主表实体
 *
 * <p>每次代码提交审查生成一条任务记录，包含原始代码、预处理后代码、
 * 审查评分、风险统计、RAG命中数、审查结果JSON等完整信息。
 * 任务状态流转: 0(处理中) → 1(已完成) / 2(失败)</p>
 *
 * <p>JSON字段说明:
 * <ul>
 *   <li>ruleCheckJson: 基础规则校验的原始结果</li>
 *   <li>llmReviewJson: LLM语义审查的原始返回</li>
 *   <li>ragContextJson: RAG检索命中的知识上下文</li>
 *   <li>reportJson: 最终生成的完整审查报告</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("code_review_task")
public class CodeReviewTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审查任务唯一编号，格式: CR-{timestamp}-{uuid8} */
    private String taskNo;

    /** 上传代码的文件名或自定义名称 */
    private String codeName;

    /** 代码语言类型，默认Java，可扩展Python/Go/JS等 */
    @Builder.Default
    private String codeType = "Java";

    /** 用户提交的原始代码内容 */
    private String originalCode;

    /** 经过预处理(清洗/格式化)后的代码 */
    private String preprocessedCode;

    /** 代码质量综合评分(0-100)，由LLM打分或规则评分兜底 */
    private Integer codeScore;

    /** 高风险问题数量 */
    @Builder.Default
    private Integer highRiskCount = 0;

    /** 中风险问题数量 */
    @Builder.Default
    private Integer midRiskCount = 0;

    /** 低风险问题数量 */
    @Builder.Default
    private Integer lowRiskCount = 0;

    /** RAG知识库检索命中条数，0表示未命中或检索失败降级 */
    @Builder.Default
    private Integer ragHitCount = 0;

    /** LLM生成的审查总体总结(200字内) */
    private String reviewSummary;

    /** 基础规则校验结果JSON */
    private String ruleCheckJson;

    /** LLM语义审查的原始返回JSON */
    private String llmReviewJson;

    /** RAG检索上下文JSON */
    private String ragContextJson;

    /** 最终审查报告JSON */
    private String reportJson;

    /** 任务状态: 0-处理中, 1-已完成, 2-失败 */
    @Builder.Default
    private Integer taskStatus = 0;

    /** 任务失败时的错误原因 */
    private String errorMsg;

    /** 提交人标识 */
    private String submitBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间(自动更新) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
