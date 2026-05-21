package com.codereview.service.agent;

import com.alibaba.fastjson2.*;
import com.codereview.util.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * 代码智能优化与重构组件
 *
 * <p>针对审查发现的每条问题，调用LLM生成:
 * <ul>
 *   <li>优化思路(optimizeSuggest): 自然语言描述优化策略</li>
 *   <li>修复代码(fixCode): 可直接替换的重构代码</li>
 *   <li>最佳实践引用(bestPracticeRef): RAG匹配的企业规范</li>
 * </ul>
 * </p>
 *
 * <p>优化策略:
 * <ul>
 *   <li>规则命中问题: 已有fixExample时直接使用，无需LLM</li>
 *   <li>LLM发现问题: 调用优化Prompt生成修复方案</li>
 *   <li>优化失败: 保持原问题不变(不丢失审查结果)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class CodeOptimizer {

    private final LLMCodeReviewer llmReviewer;

    public CodeOptimizer(LLMCodeReviewer llmReviewer) {
        this.llmReviewer = llmReviewer;
    }

    /**
     * 批量生成优化方案
     *
     * @param problems            所有审查问题列表
     * @param originalCode        原始代码
     * @param bestPracticeContext RAG检索的企业最佳实践上下文
     * @return 附带优化方案的问题列表
     */
    public List<JSONObject> generateOptimizations(List<JSONObject> problems, String originalCode,
                                                   String bestPracticeContext) {
        List<JSONObject> optimized = new ArrayList<>();
        for (JSONObject problem : problems) {
            try {
                JSONObject enriched = optimizeOne(problem, originalCode, bestPracticeContext);
                optimized.add(enriched);
            } catch (Exception e) {
                // 单个问题优化失败不影响整体
                log.warn("单个问题优化失败: {}", problem.getString("problemDesc"), e);
                optimized.add(problem);
            }
        }
        log.info("代码优化完成: 处理{}条问题", optimized.size());
        return optimized;
    }

    /**
     * 优化单个问题
     *
     * <p>策略:
     * <ol>
     *   <li>规则命中有fixExample → 直接使用</li>
     *   <li>LLM发现问题 → 调用大模型生成个性化优化方案</li>
     * </ol>
     * </p>
     */
    private JSONObject optimizeOne(JSONObject problem, String originalCode, String bestPractice) {
        // 规则命中且已有修复示例，直接使用(无需额外LLM调用)
        if (problem.getBooleanValue("isFromRule", false)
                && problem.getString("fixCode") != null
                && !problem.getString("fixCode").isBlank()) {
            return problem;
        }

        String problemDesc = problem.getString("problemDesc");
        String problemCode = problem.getString("problemCode");

        try {
            String optimizeResp = llmReviewer.generateOptimizeCode(
                    problemCode != null ? problemCode : originalCode,
                    problemDesc,
                    bestPractice
            );
            JSONObject optimizeJson = JSONObject.parseObject(JsonParser.cleanResponse(optimizeResp));

            if (optimizeJson != null) {
                // 将LLM返回的优化信息写入问题对象
                if (optimizeJson.containsKey("refactoredCode")) {
                    problem.put("fixCode", optimizeJson.getString("refactoredCode"));
                }
                if (optimizeJson.containsKey("optimizePlan")) {
                    problem.put("optimizeSuggest", optimizeJson.getString("optimizePlan"));
                }
                if (optimizeJson.containsKey("bestPracticeRef")) {
                    problem.put("bestPracticeRef", optimizeJson.getString("bestPracticeRef"));
                }
            }
        } catch (Exception e) {
            log.warn("LLM优化代码生成失败: {}", e.getMessage());
        }
        return problem;
    }
}
