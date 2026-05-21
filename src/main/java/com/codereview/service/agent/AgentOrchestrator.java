package com.codereview.service.agent;

import com.alibaba.fastjson2.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import com.codereview.service.rag.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Agent核心调度器 - 代码审查全流程编排
 *
 * <p>这是整个系统的核心调度模块，实现完整的Agent审查闭环链路。
 * 采用「接收 → 校验 → RAG检索 → AI分析 → 优化 → 报告」的经典Agent模式，
 * 全程后端主控，LLM仅负责语义分析，基础规则校验、数据持久化、
 * 任务调度全部由Java工程代码掌控。</p>
 *
 * <p><b>8步审查闭环:</b>
 * <ol>
 *   <li>代码预处理 - 清洗、格式化、分片</li>
 *   <li>基础规则校验 - 后端硬规则正则匹配</li>
 *   <li>RAG知识检索 - 向量+关键词混合检索增强</li>
 *   <li>LLM语义审查 - 大模型深度分析(含RAG上下文)</li>
 *   <li>问题聚合去重 - 合并规则/LLM结果，去重加权</li>
 *   <li>代码优化生成 - AI+企业最佳实践优化方案</li>
 *   <li>审查报告组装 - 评分、分类、规范引用</li>
 *   <li>数据持久化 - 任务/明细/统计入库</li>
 * </ol>
 * </p>
 *
 * <p><b>容错降级策略:</b>
 * <ul>
 *   <li>RAG检索失败 → 降级为纯LLM审查(无知识上下文)</li>
 *   <li>LLM调用失败 → 返回基础规则审查结果 + 错误提示</li>
 *   <li>单步异常 → 不影响前置步骤已产生的审查结果</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final CodeReviewTaskMapper taskMapper;
    private final CodeReviewDetailMapper detailMapper;
    private final CodePreprocessor preprocessor;
    private final RuleChecker ruleChecker;
    private final LLMCodeReviewer llmReviewer;
    private final CodeOptimizer codeOptimizer;
    private final ReportGenerator reportGenerator;
    private final RetrievalService retrievalService;

    /** 最终注入LLM Prompt的RAG知识片段数量 */
    @Value("${rag.final-topk:5}")
    private int ragTopK;

    public AgentOrchestrator(CodeReviewTaskMapper taskMapper, CodeReviewDetailMapper detailMapper,
                             CodePreprocessor preprocessor, RuleChecker ruleChecker,
                             LLMCodeReviewer llmReviewer, CodeOptimizer codeOptimizer,
                             ReportGenerator reportGenerator, RetrievalService retrievalService) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.preprocessor = preprocessor;
        this.ruleChecker = ruleChecker;
        this.llmReviewer = llmReviewer;
        this.codeOptimizer = codeOptimizer;
        this.reportGenerator = reportGenerator;
        this.retrievalService = retrievalService;
    }

    /**
     * 异步执行完整审查链路
     *
     * <p>通过 @Async("reviewExecutor") 在线程池中异步执行，
     * 不阻塞前端请求线程。前端提交后获得任务编号，通过轮询获取结果。</p>
     *
     * @param task 已创建的审查任务实体(状态为0-处理中)
     * @return 审查报告Future
     */
    @Async("reviewExecutor")
    public CompletableFuture<ReviewReportVO> executeReview(CodeReviewTask task) {
        try {
            log.info("===== Agent审查链路启动: taskNo={} =====", task.getTaskNo());

            // === Step 1: 代码预处理 ===
            String processed = preprocessor.preprocess(task.getOriginalCode());
            task.setPreprocessedCode(processed);
            taskMapper.updateById(task);

            // === Step 2: 基础规则校验 ===
            // 纯后端硬规则，不依赖LLM，保障兜底能力
            List<JSONObject> ruleProblems = ruleChecker.check(processed);
            log.info("[Step2] 基础规则校验完成: 发现{}个问题", ruleProblems.size());

            // === Step 3: RAG知识检索增强 ===
            // 混合检索: 向量语义召回 + 关键词匹配，为LLM注入企业专属上下文
            List<JSONObject> ragContexts = new ArrayList<>();
            String ragSpecContext = null;   // 企业规范上下文
            String ragCaseContext = null;   // 历史案例上下文
            try {
                JSONObject ragResult = retrievalService.hybridSearch(processed, ragTopK);
                ragContexts = retrievalService.buildRagContextList(ragResult);
                ragSpecContext = retrievalService.buildSpecContext(ragContexts);
                ragCaseContext = retrievalService.buildCaseContext(ragContexts);
                task.setRagHitCount(ragContexts.size());
                task.setRagContextJson(ragContexts.toString());
                log.info("[Step3] RAG检索完成: 命中{}条知识", ragContexts.size());
            } catch (Exception e) {
                // RAG检索失败不影响后续审查，降级为纯LLM审查
                log.warn("[Step3] RAG检索失败，降级为纯LLM审查: {}", e.getMessage());
                task.setRagHitCount(0);
            }

            // === Step 4: LLM语义深度审查 ===
            // 注入RAG检索的企业规范和案例作为上下文，实现「懂企业规范」的专属审查
            LLMCodeReviewer.LLMReviewResult llmResult = llmReviewer.review(processed, ragSpecContext, ragCaseContext);
            task.setLlmReviewJson(llmResult.rawResponse());
            log.info("[Step4] LLM语义审查完成: 评分={}, 问题数={}", llmResult.score(), llmResult.problems().size());

            // === Step 5: 问题聚合去重 ===
            // 合并规则校验和LLM审查结果，基于行号+描述相似度去重
            List<JSONObject> allProblems = mergeProblems(ruleProblems, llmResult.problems());
            log.info("[Step5] 问题聚合完成: 规则{}个 + LLM{}个 → 合并后{}个",
                    ruleProblems.size(), llmResult.problems().size(), allProblems.size());

            // === Step 6: 代码优化生成 ===
            // 针对每条问题，结合RAG最佳实践生成修复代码和优化建议
            String bestPracticeContext = retrievalService.buildBestPracticeContext(ragContexts);
            List<JSONObject> optimizedProblems = codeOptimizer.generateOptimizations(allProblems, processed, bestPracticeContext);
            log.info("[Step6] 代码优化完成");

            // === Step 7: 审查报告生成 ===
            // 聚合所有结果，生成标准化报告: 评分、分类统计、规范引用
            ReviewReportVO report = reportGenerator.generateReport(
                    task.getId(), task.getTaskNo(), task.getCodeName(), task.getCodeType(),
                    llmResult.score() > 0 ? llmResult.score() : calculateRuleScore(ruleProblems),
                    llmResult.summary(), optimizedProblems, ragContexts);
            String reportJson = reportGenerator.toJson(report).toJSONString();
            log.info("[Step7] 报告生成完成");

            // === Step 8: 数据持久化 ===
            // 问题明细入库 + 任务状态更新为已完成
            persistResults(task, allProblems, report, reportJson);
            log.info("[Step8] 数据持久化完成");

            updateTaskStatus(task, report, reportJson);
            log.info("===== Agent审查链路完成: taskNo={}, 评分={} =====", task.getTaskNo(), report.getCodeScore());

            return CompletableFuture.completedFuture(report);
        } catch (Exception e) {
            // 顶层异常兜底: 任务标记为失败，保留错误信息便于排查
            log.error("审查任务执行失败: taskNo={}", task.getTaskNo(), e);
            task.setTaskStatus(2);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new RuntimeException("审查任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并规则校验和LLM审查的问题列表
     *
     * <p>去重策略:
     * <ol>
     *   <li>完全匹配: 相同行号 + 相同问题类型 → 去重，保留LLM的优化建议</li>
     *   <li>相似匹配: 问题描述文本相似度 > 70% → 去重</li>
     * </ol>
     * 规则问题优先保留，LLM的优化信息合并到已有问题中。
     * </p>
     */
    private List<JSONObject> mergeProblems(List<JSONObject> ruleProblems, List<JSONObject> llmProblems) {
        List<JSONObject> merged = new ArrayList<>(ruleProblems);
        for (JSONObject llm : llmProblems) {
            llm.put("isFromLlm", true);
            if (llm.get("isFromRule") == null) {
                llm.put("isFromRule", false);
            }
            // 检查是否与已有问题重复
            boolean duplicate = false;
            for (JSONObject existing : merged) {
                if (isSimilarProblem(existing, llm)) {
                    duplicate = true;
                    // 将LLM的优化建议合并到已有问题
                    enrichExisting(existing, llm);
                    break;
                }
            }
            if (!duplicate) {
                merged.add(llm);
            }
        }
        return merged;
    }

    /**
     * 判断两个问题是否相似(同一行号+类型 或 描述文本70%以上相似)
     */
    private boolean isSimilarProblem(JSONObject a, JSONObject b) {
        String lineA = a.getString("lineNum");
        String lineB = b.getString("lineNum");
        String typeA = a.getString("problemType");
        String typeB = b.getString("problemType");
        // 精确匹配: 同行号 + 同类型
        if (lineA != null && lineA.equals(lineB) && typeA != null && typeA.equals(typeB)) {
            return true;
        }
        // 模糊匹配: 描述文本70%以上字符相同
        String descA = a.getString("problemDesc");
        String descB = b.getString("problemDesc");
        if (descA != null && descB != null && descA.length() > 10) {
            int minLen = Math.min(descA.length(), descB.length());
            int matchChars = 0;
            for (int i = 0; i < minLen; i++) {
                if (descA.charAt(i) == descB.charAt(i)) matchChars++;
            }
            return (double) matchChars / minLen > 0.7;
        }
        return false;
    }

    /**
     * 用LLM问题中的优化信息丰富已有规则问题
     */
    private void enrichExisting(JSONObject existing, JSONObject llm) {
        if (existing.getString("fixCode") == null || existing.getString("fixCode").isBlank()) {
            existing.put("fixCode", llm.getString("fixCode"));
        }
        if (existing.getString("optimizeSuggest") == null || existing.getString("optimizeSuggest").isBlank()) {
            existing.put("optimizeSuggest", llm.getString("optimizeSuggest"));
        }
    }

    /**
     * 当LLM审查失败时，基于规则问题计算兜底评分
     *
     * <p>扣分策略: HIGH -10, MEDIUM -5, LOW -2</p>
     */
    private int calculateRuleScore(List<JSONObject> ruleProblems) {
        int score = 100;
        for (JSONObject p : ruleProblems) {
            String level = p.getString("riskLevel");
            if ("HIGH".equalsIgnoreCase(level)) score -= 10;
            else if ("MEDIUM".equalsIgnoreCase(level)) score -= 5;
            else score -= 2;
        }
        return Math.max(score, 0);
    }

    /**
     * 批量持久化问题明细
     */
    @Transactional
    private void persistResults(CodeReviewTask task, List<JSONObject> problems,
                                 ReviewReportVO report, String reportJson) {
        int sortOrder = 0;
        for (JSONObject p : problems) {
            CodeReviewDetail detail = CodeReviewDetail.builder()
                    .taskId(task.getId())
                    .riskLevel(p.getString("riskLevel"))
                    .problemType(p.getString("problemType"))
                    .lineNum(p.getString("lineNum"))
                    .problemCode(p.getString("problemCode"))
                    .problemDesc(p.getString("problemDesc"))
                    .riskEffect(p.getString("riskEffect"))
                    .optimizeSuggest(p.getString("optimizeSuggest"))
                    .fixCode(p.getString("fixCode"))
                    .ragRefId(p.getLong("matchedRuleId"))
                    .ragRefTitle(p.getString("ragRefTitle"))
                    .ragRefType(p.getString("ragRefType"))
                    .isFromRule(p.getBooleanValue("isFromRule", false) ? 1 : 0)
                    .isFromLlm(p.getBooleanValue("isFromLlm", false) ? 1 : 0)
                    .sortOrder(sortOrder++)
                    .createTime(LocalDateTime.now())
                    .build();
            detailMapper.insert(detail);
        }
    }

    /**
     * 更新任务状态为已完成，写入评分和风险统计
     */
    private void updateTaskStatus(CodeReviewTask task, ReviewReportVO report, String reportJson) {
        task.setCodeScore(report.getCodeScore());
        task.setHighRiskCount(report.getRiskSummary().getHigh());
        task.setMidRiskCount(report.getRiskSummary().getMedium());
        task.setLowRiskCount(report.getRiskSummary().getLow());
        task.setReviewSummary(report.getReviewSummary());
        task.setReportJson(reportJson);
        task.setTaskStatus(1); // 标记为已完成
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 创建审查任务记录
     *
     * <p>生成唯一任务编号 CR-{timestamp}-{uuid8}，状态初始为0(处理中)</p>
     */
    public CodeReviewTask createTask(String codeContent, String codeName, String codeType, String submitBy) {
        CodeReviewTask task = CodeReviewTask.builder()
                .taskNo("CR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .codeName(codeName)
                .codeType(codeType != null ? codeType : "Java")
                .originalCode(codeContent)
                .taskStatus(0) // 处理中
                .submitBy(submitBy)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);
        return task;
    }
}
