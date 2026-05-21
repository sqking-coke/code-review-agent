package com.codereview.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 知识回流提交请求DTO
 *
 * <p>将高质量的审查案例提交至RAG知识库审核流程。
 * 需指定审查任务和具体问题明细，系统自动提取问题代码+修复代码，
 * 结构化处理后进入待审核队列。</p>
 */
@Data
public class RagFeedbackSubmitRequest {

    /** 审查任务ID */
    @NotNull(message = "审查任务ID不能为空")
    private Long taskId;

    /** 问题明细ID(指定要回流的某个具体问题) */
    @NotNull(message = "问题明细ID不能为空")
    private Long detailId;

    /** 审核备注(可选) */
    private String reviewComment;
}
