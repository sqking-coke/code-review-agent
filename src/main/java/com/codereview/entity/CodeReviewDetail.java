package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

/**
 * 代码审查问题明细表实体
 *
 * <p>关联审查任务({@link CodeReviewTask})，每条记录对应一个被发现的代码问题。
 * 问题可来自基础规则校验(isFromRule=1)和/或LLM语义审查(isFromLlm=1)，
 * 相同问题会进行去重合并。</p>
 *
 * <p>风险等级:
 * <ul>
 *   <li>HIGH: 高风险 - SQL注入、空指针、资源泄露等必须修复</li>
 *   <li>MEDIUM: 中风险 - 命名不规范、性能隐患等建议修复</li>
 *   <li>LOW: 低风险 - 代码格式、魔法值等优化建议</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("code_review_detail")
public class CodeReviewDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的审查任务ID */
    private Long taskId;

    /** 风险等级: HIGH / MEDIUM / LOW */
    private String riskLevel;

    /** 问题类型: STYLE(规范) / BUG(缺陷) / PERFORMANCE(性能) / SECURITY(安全) / DESIGN(设计) */
    private String problemType;

    /** 问题代码所在行号，可为单个行号或范围如 "15-20" */
    private String lineNum;

    /** 问题代码片段 */
    private String problemCode;

    /** 问题详细描述 */
    private String problemDesc;

    /** 风险影响范围说明 */
    private String riskEffect;

    /** 优化建议(自然语言描述) */
    private String optimizeSuggest;

    /** AI生成的修复后代码片段 */
    private String fixCode;

    /** 关联的RAG知识点ID(规范条文或历史案例) */
    private Long ragRefId;

    /** 引用的RAG知识标题 */
    private String ragRefTitle;

    /** RAG知识类型: STANDARD(规范) / CASE(案例) / PRACTICE(实践) / PATTERN(缺陷模式) */
    private String ragRefType;

    /** 是否来自基础规则校验: 0-否, 1-是 */
    @Builder.Default
    private Integer isFromRule = 0;

    /** 是否来自LLM语义审查: 0-否, 1-是 */
    @Builder.Default
    private Integer isFromLlm = 0;

    /** 问题排序序号 */
    @Builder.Default
    private Integer sortOrder = 0;

    /** 问题发现时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
