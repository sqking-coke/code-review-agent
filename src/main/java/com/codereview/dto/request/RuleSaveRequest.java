package com.codereview.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 审查规则保存/更新请求DTO
 *
 * <p>支持新增规则(id=null)和更新已有规则(id!=null)两种模式。
 * 规则修改后即时生效(RuleChecker每次查询启用状态的规则)，无需重启服务。</p>
 */
@Data
public class RuleSaveRequest {

    /** 规则ID: 为null时新增，不为null时更新对应规则 */
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /** 规则分类: STYLE / BUG / PERFORMANCE / SECURITY / DESIGN */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /** 规则类别: BASIC(基础正则匹配) / AI(语义LLM审查) */
    private String ruleCategory = "BASIC";

    /** 规则详细说明 */
    @NotBlank(message = "规则内容不能为空")
    private String ruleContent;

    /** 匹配正则表达式(仅BASIC类规则需要) */
    private String checkPattern;

    /** 严重程度: HIGH / MEDIUM / LOW */
    private String severity = "MEDIUM";

    /** 适用语言 */
    private String language = "Java";

    /** 错误示例代码 */
    private String exampleCode;

    /** 修复示例代码 */
    private String fixExample;

    /** 排序序号 */
    private Integer sortOrder = 0;

    /** 启用状态: 0-禁用, 1-启用 */
    private Integer status = 1;
}
