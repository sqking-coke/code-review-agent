package com.codereview.service.agent;

import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.regex.*;

/**
 * 基础硬规则校验组件
 *
 * <p>纯后端正则匹配实现，不依赖LLM，是整个审查体系的兜底保障层。
 * 即使LLM服务完全不可用，也能输出基础规则审查结果。</p>
 *
 * <p>工作原理:
 * <ol>
 *   <li>从数据库查询所有启用状态的BASIC类型规则</li>
 *   <li>对每条规则的checkPattern正则编译匹配代码</li>
 *   <li>匹配到的位置自动计算对应行号</li>
 *   <li>输出标准化问题JSON(与LLM输出格式兼容)</li>
 * </ol>
 * </p>
 *
 * <p>规则可动态增删启停，无需重启服务。</p>
 */
@Slf4j
@Component
public class RuleChecker {

    private final CodeReviewRuleMapper ruleMapper;

    public RuleChecker(CodeReviewRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 执行所有启用规则的全量校验
     *
     * @param code 预处理后的代码
     * @return 发现的问题列表(JSONObject标准格式)
     */
    public List<JSONObject> check(String code) {
        List<JSONObject> problems = new ArrayList<>();
        // 查询所有启用的BASIC类型规则(按排序序号升序)
        List<CodeReviewRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<CodeReviewRule>()
                        .eq(CodeReviewRule::getStatus, 1)
                        .eq(CodeReviewRule::getRuleCategory, "BASIC")
                        .orderByAsc(CodeReviewRule::getSortOrder)
        );

        for (CodeReviewRule rule : rules) {
            try {
                List<JSONObject> matched = checkRule(code, rule);
                problems.addAll(matched);
            } catch (Exception e) {
                // 单条规则异常不中断整体校验
                log.warn("规则校验异常: rule={}, error={}", rule.getRuleName(), e.getMessage());
            }
        }
        log.info("基础规则校验完成: 检查{}条规则, 发现{}个问题", rules.size(), problems.size());
        return problems;
    }

    /**
     * 执行单条规则的正则匹配
     *
     * <p>对每条匹配命中生成一个标准问题对象，包含:
     * 行号、问题类型、风险等级、问题描述、修复建议等。</p>
     *
     * @param code 代码文本
     * @param rule 规则配置
     * @return 该规则命中的所有问题
     */
    private List<JSONObject> checkRule(String code, CodeReviewRule rule) {
        List<JSONObject> problems = new ArrayList<>();
        // 无正则表达式的规则跳过(语义规则由LLM处理)
        if (rule.getCheckPattern() == null || rule.getCheckPattern().isBlank()) {
            return problems;
        }

        try {
            Pattern pattern = Pattern.compile(rule.getCheckPattern());
            Matcher matcher = pattern.matcher(code);
            String[] lines = code.split("\n");

            while (matcher.find()) {
                // 计算匹配位置对应的行号
                int lineNum = findLineNumber(code, matcher.start());
                String problemLine = (lineNum > 0 && lineNum <= lines.length)
                        ? lines[lineNum - 1].trim() : "";

                // 构建标准化问题对象
                JSONObject problem = new JSONObject();
                problem.put("lineNum", String.valueOf(lineNum));
                problem.put("problemType", rule.getRuleType());
                problem.put("riskLevel", rule.getSeverity());
                problem.put("problemCode", problemLine);
                problem.put("problemDesc", rule.getRuleContent());
                problem.put("riskEffect", "违反" + rule.getRuleName() + "规则");
                problem.put("optimizeSuggest", rule.getFixExample() != null ? rule.getFixExample() : "");
                problem.put("fixCode", rule.getFixExample() != null ? rule.getFixExample() : "");
                problem.put("matchedRuleId", rule.getId());
                problem.put("matchedRuleName", rule.getRuleName());
                problem.put("isFromRule", true);

                problems.add(problem);
            }
        } catch (Exception e) {
            log.warn("规则[{}]正则匹配异常: {}", rule.getRuleName(), e.getMessage());
        }
        return problems;
    }

    /**
     * 根据字符位置计算行号(从1开始)
     */
    private int findLineNumber(String code, int pos) {
        int lineNum = 1;
        for (int i = 0; i < pos && i < code.length(); i++) {
            if (code.charAt(i) == '\n') lineNum++;
        }
        return lineNum;
    }
}
