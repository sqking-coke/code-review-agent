package com.codereview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

/**
 * 代码审查规则配置表实体
 *
 * <p>支持两类规则:
 * <ul>
 *   <li>BASIC(基础规则): 纯正则匹配，无LLM依赖，作为兜底审查</li>
 *   <li>AI(语义规则): 交由LLM进行语义级判断</li>
 * </ul>
 * </p>
 *
 * <p>规则通过 checkPattern 正则表达式匹配代码，支持动态增删启停，
 * 无需重启服务即可生效。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("code_review_rule")
public class CodeReviewRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称，如"类名大驼峰校验" */
    private String ruleName;

    /** 规则分类: STYLE / BUG / PERFORMANCE / SECURITY / DESIGN */
    private String ruleType;

    /** 规则类别: BASIC(基础规则-正则匹配) / AI(语义规则-LLM判断) */
    @Builder.Default
    private String ruleCategory = "BASIC";

    /** 规则详细描述，用于报告中的问题说明 */
    private String ruleContent;

    /** Java正则表达式，用于匹配违规代码 */
    private String checkPattern;

    /** 违规严重程度: HIGH / MEDIUM / LOW */
    @Builder.Default
    private String severity = "MEDIUM";

    /** 适用编程语言，默认Java */
    @Builder.Default
    private String language = "Java";

    /** 错误示例代码(展示违规写法) */
    private String exampleCode;

    /** 修复示例代码(展示正确写法) */
    private String fixExample;

    /** 排序序号，数值越小优先级越高 */
    @Builder.Default
    private Integer sortOrder = 0;

    /** 启用状态: 0-禁用, 1-启用 */
    @Builder.Default
    private Integer status = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
