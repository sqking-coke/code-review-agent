package com.codereview.service.agent;

import com.alibaba.fastjson2.*;
import com.codereview.client.*;
import com.codereview.util.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * LLM语义深度审查组件
 *
 * <p>调用大模型进行语义级的代码深度分析，是区别于传统静态检测工具的核心能力。
 * 审查时注入RAG检索的企业规范和历史案例作为Prompt上下文，
 * 使AI从「通用代码审查」升级为「懂企业规范、知历史教训」的专属审查。</p>
 *
 * <p>容错降级:
 * <ul>
 *   <li>LLM超时/调用失败 → 返回降级结果(仅含错误提示，不阻塞审查流程)</li>
 *   <li>LLM返回格式异常 → 安全解析(parseProblems有兜底返回空列表)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class LLMCodeReviewer {

    private final LLMClient llmClient;

    public LLMCodeReviewer(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 执行LLM语义深度审查
     *
     * @param code            预处理后的代码
     * @param ragSpecContext  RAG检索的企业规范上下文(可为null，则填充默认文本)
     * @param ragCaseContext  RAG检索的历史案例上下文(可为null，则填充默认文本)
     * @return 审查结果(评分、总结、问题列表、原始响应)
     */
    public LLMReviewResult review(String code, String ragSpecContext, String ragCaseContext) {
        String systemPrompt = PromptTemplate.buildReviewPrompt(ragSpecContext, ragCaseContext, code);
        String userMessage = "请对以上代码进行全面的多维度审查，结合企业规范和历史案例，严格按照JSON格式输出审查结果。";

        try {
            String response = llmClient.chat(systemPrompt, userMessage);
            JSONObject result = JSONObject.parseObject(JsonParser.cleanResponse(response));

            int score = result.getIntValue("overallScore", 60);
            String summary = result.getString("summary");
            List<JSONObject> problems = JsonParser.parseProblems(response);

            log.info("LLM审查完成: 评分={}, 问题数={}, rag上下文长度={}",
                    score, problems.size(), (ragSpecContext != null ? ragSpecContext.length() : 0));

            return new LLMReviewResult(score, summary, problems, response);
        } catch (Exception e) {
            // LLM调用失败 → 返回降级结果，上层Agent继续处理
            log.error("LLM审查调用失败，返回降级结果", e);
            List<JSONObject> fallback = new ArrayList<>();
            JSONObject errorItem = new JSONObject();
            errorItem.put("lineNum", "N/A");
            errorItem.put("problemType", "BUG");
            errorItem.put("riskLevel", "MEDIUM");
            errorItem.put("problemDesc", "LLM审查超时，请检查大模型服务是否可用。基础规则审查结果见报告。");
            errorItem.put("riskEffect", "无法进行AI深度语义分析");
            errorItem.put("optimizeSuggest", "请确认LLM API配置正确后重试");
            errorItem.put("isFromLlm", false);
            fallback.add(errorItem);
            return new LLMReviewResult(0, "LLM审查失败，已降级为基础规则审查", fallback, "{}");
        }
    }

    /**
     * 生成审查报告总结
     *
     * @param score           代码综合评分
     * @param high            高风险数量
     * @param mid             中风险数量
     * @param low             低风险数量
     * @param problemsSummary 问题摘要(前5条)
     * @return LLM生成的报告总结(200字以内)
     */
    public String generateReviewSummary(int score, int high, int mid, int low, String problemsSummary) {
        String prompt = PromptTemplate.buildReportSummaryPrompt(score, high, mid, low, problemsSummary);
        try {
            return llmClient.chat("你是一个代码质量评估报告撰写专家。", prompt);
        } catch (Exception e) {
            log.warn("生成审查总结失败", e);
            return "代码质量评分: " + score + "分, 高风险" + high + "个, 中风险" + mid + "个, 低风险" + low + "个";
        }
    }

    /**
     * 生成单个问题的优化代码
     *
     * @param originalCode  问题代码片段
     * @param problemDesc   问题描述
     * @param bestPractice  RAG检索的最佳实践
     * @return LLM生成的优化方案JSON字符串
     */
    public String generateOptimizeCode(String originalCode, String problemDesc, String bestPractice) {
        String prompt = PromptTemplate.buildOptimizePrompt(originalCode, problemDesc, bestPractice);
        try {
            return llmClient.chat("你是一个Java代码优化专家，请严格按照JSON格式输出。", prompt);
        } catch (Exception e) {
            log.warn("生成优化代码失败", e);
            return "{}";
        }
    }

    /**
     * LLM审查结果记录
     *
     * @param score      代码质量评分(0-100)，LLM失败时为0
     * @param summary    审查总结
     * @param problems   发现的问题列表
     * @param rawResponse LLM原始返回文本(用于调试和回溯)
     */
    public record LLMReviewResult(int score, String summary, List<JSONObject> problems, String rawResponse) {}
}
