package com.codereview.util;

import lombok.*;

/**
 * LLM Prompt模板工程
 *
 * <p>集中管理所有LLM交互提示词模板，确保:
 * <ul>
 *   <li>输出格式一致: 所有模板严格要求JSON格式输出</li>
 *   <li>RAG上下文注入: 预留占位符动态注入RAG检索结果</li>
 *   <li>可维护性: 模板变更无需修改业务代码</li>
 * </ul>
 * </p>
 *
 * <p>模板体系:
 * <ul>
 *   <li>REVIEW_SYSTEM_PROMPT: 核心审查提示词(含RAG上下文占位)</li>
 *   <li>OPTIMIZE_PROMPT: 代码优化建议生成提示词</li>
 *   <li>REPORT_SUMMARY_PROMPT: 审查报告总结生成提示词</li>
 * </ul>
 * </p>
 */
@NoArgsConstructor
public final class PromptTemplate {

    /**
     * 核心审查系统提示词
     *
     * <p>占位符说明:
     * <ul>
     *   <li>%s(1): RAG检索的企业编码规范上下文</li>
     *   <li>%s(2): RAG检索的历史缺陷案例上下文</li>
     * </ul>
     * 无RAG命中时填充"暂无匹配的企业规范条文"/"暂无相似历史案例"。
     * </p>
     */
    public static final String REVIEW_SYSTEM_PROMPT = """
            你是一个资深的Java代码审查专家。你需要对提交的代码进行多维度深度审查。

            ## 审查维度
            1. **代码规范(STYLE)**: 命名、格式、注释、魔法值、冗余导入
            2. **逻辑BUG(BUG)**: 空指针、参数校验、异常处理、逻辑缺陷、资源泄露
            3. **性能隐患(PERFORMANCE)**: 循环查库、字符串拼接、对象创建、集合使用
            4. **安全漏洞(SECURITY)**: SQL注入、硬编码密钥、权限缺失、敏感信息泄露
            5. **设计质量(DESIGN)**: 代码冗余、耦合度高、方法过长、职责不清

            ## 企业编码规范（RAG检索增强上下文）
            %s

            ## 历史相似缺陷案例（RAG检索增强上下文）
            %s

            ## 输出要求
            请严格按照以下JSON格式输出（不要包含markdown标记）:
            {
              "overallScore": 85,
              "summary": "代码整体质量评估的简要总结",
              "problems": [
                {
                  "lineNum": "行号或范围",
                  "problemType": "STYLE|BUG|PERFORMANCE|SECURITY|DESIGN",
                  "riskLevel": "HIGH|MEDIUM|LOW",
                  "problemCode": "问题代码片段",
                  "problemDesc": "问题描述",
                  "riskEffect": "风险影响说明",
                  "optimizeSuggest": "优化建议",
                  "fixCode": "修复后的完整代码",
                  "matchedRuleId": "引用的RAG知识ID(如有)"
                }
              ]
            }
            如果没有发现问题，problems数组为空。
            """;

    /**
     * 构建带RAG上下文的完整审查Prompt
     *
     * @param ragSpecContext RAG检索的企业规范上下文(可为null)
     * @param ragCaseContext RAG检索的历史案例上下文(可为null)
     * @param code           待审查的代码内容
     * @return 组装完成的Prompt字符串
     */
    public static String buildReviewPrompt(String ragSpecContext, String ragCaseContext, String code) {
        String systemPrompt = String.format(REVIEW_SYSTEM_PROMPT,
                ragSpecContext != null && !ragSpecContext.isBlank() ? ragSpecContext : "暂无匹配的企业规范条文",
                ragCaseContext != null && !ragCaseContext.isBlank() ? ragCaseContext : "暂无相似历史案例");

        return systemPrompt + "\n\n## 提交审查的代码\n\n```java\n" + code + "\n```";
    }

    /** 代码优化提示词模板 */
    public static final String OPTIMIZE_PROMPT = """
            你是一个Java代码优化专家。请针对以下问题生成详细的优化方案。

            ## 原始代码
            ```java
            %s
            ```

            ## 发现的问题
            %s

            ## 企业最佳实践（RAG检索）
            %s

            ## 输出要求
            请按JSON格式输出优化方案（不要markdown标记）:
            {
              "optimizePlan": "整体优化思路",
              "refactoredCode": "重构后的完整代码",
              "changes": [
                {
                  "description": "变更说明",
                  "beforeCode": "修改前代码",
                  "afterCode": "修改后代码"
                }
              ],
              "bestPracticeRef": "引用的最佳实践"
            }
            """;

    /**
     * 构建代码优化Prompt
     *
     * @param originalCode  原始问题代码
     * @param problemDesc   问题描述
     * @param bestPractice  RAG检索的最佳实践(可为null)
     * @return 组装完成的优化Prompt
     */
    public static String buildOptimizePrompt(String originalCode, String problemDesc, String bestPractice) {
        return String.format(OPTIMIZE_PROMPT, originalCode, problemDesc,
                bestPractice != null && !bestPractice.isBlank() ? bestPractice : "暂无匹配的最佳实践");
    }

    /** 审查总结生成提示词模板 */
    public static final String REPORT_SUMMARY_PROMPT = """
            请根据以下审查结果生成一份简洁的审查报告总结（200字以内）。

            代码评分: %d分
            高风险问题: %d个
            中风险问题: %d个
            低风险问题: %d个
            问题摘要:
            %s

            请给出整体的代码质量评估和改进建议。
            """;

    /**
     * 构建审查报告总结Prompt
     *
     * @param score           代码综合评分
     * @param high            高风险问题数量
     * @param mid             中风险问题数量
     * @param low             低风险问题数量
     * @param problemsSummary 问题摘要文本(前5个问题描述)
     * @return 组装完成的总结Prompt
     */
    public static String buildReportSummaryPrompt(int score, int high, int mid, int low, String problemsSummary) {
        return String.format(REPORT_SUMMARY_PROMPT, score, high, mid, low, problemsSummary);
    }
}
