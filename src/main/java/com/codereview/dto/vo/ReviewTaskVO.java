package com.codereview.dto.vo;

import lombok.*;

import java.time.*;

/**
 * 审查任务列表/详情响应VO
 *
 * <p>对 {@link com.codereview.entity.CodeReviewTask} 的视图层投影，
 * 不暴露原始代码内容，仅返回审查元信息和统计数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTaskVO {

    /** 任务ID */
    private Long id;

    /** 任务编号(格式: CR-{timestamp}-{uuid}) */
    private String taskNo;

    /** 代码文件名 */
    private String codeName;

    /** 代码语言类型 */
    private String codeType;

    /** 代码质量评分(0-100) */
    private Integer codeScore;

    /** 高风险问题数 */
    private Integer highRiskCount;

    /** 中风险问题数 */
    private Integer midRiskCount;

    /** 低风险问题数 */
    private Integer lowRiskCount;

    /** RAG知识命中数 */
    private Integer ragHitCount;

    /** 审查总结 */
    private String reviewSummary;

    /** 任务状态: 0-处理中, 1-已完成, 2-失败 */
    private Integer taskStatus;

    /** 失败原因(taskStatus=2时有值) */
    private String errorMsg;

    /** 提交人 */
    private String submitBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
