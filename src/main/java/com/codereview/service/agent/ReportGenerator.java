package com.codereview.service.agent;

import com.alibaba.fastjson2.*;
import com.codereview.dto.vo.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.stream.*;

/**
 * 审查报告生成组件
 *
 * <p>聚合所有审查数据，生成结构化、可视化的标准化审查报告。
 * 报告包含:
 * <ul>
 *   <li>代码质量综合评分(0-100)</li>
 *   <li>风险分级统计(HIGH/MEDIUM/LOW)</li>
 *   <li>问题明细列表(含行号、描述、修复代码)</li>
 *   <li>RAG知识库匹配的规范条文引用</li>
 *   <li>LLM生成的整体质量评估总结</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class ReportGenerator {

    private final LLMCodeReviewer llmReviewer;

    public ReportGenerator(LLMCodeReviewer llmReviewer) {
        this.llmReviewer = llmReviewer;
    }

    /**
     * 生成完整审查报告
     *
     * @param taskId      审查任务ID
     * @param taskNo      任务编号
     * @param codeName    代码文件名
     * @param codeType    代码语言类型
     * @param score       综合评分
     * @param summary     审查总结(LLM生成)
     * @param problems    所有问题列表(含优化方案)
     * @param ragContexts RAG检索上下文列表
     * @return 完整审查报告VO
     */
    public ReviewReportVO generateReport(Long taskId, String taskNo, String codeName, String codeType,
                                          int score, String summary, List<JSONObject> problems,
                                          List<JSONObject> ragContexts) {
        // === 风险统计 ===
        int high = 0, mid = 0, low = 0;
        List<ReviewReportVO.ProblemItem> items = new ArrayList<>();

        for (JSONObject p : problems) {
            String riskLevel = p.getString("riskLevel");
            if ("HIGH".equalsIgnoreCase(riskLevel)) high++;
            else if ("MEDIUM".equalsIgnoreCase(riskLevel)) mid++;
            else low++;

            items.add(ReviewReportVO.ProblemItem.builder()
                    .detailId(p.getLong("detailId"))
                    .riskLevel(riskLevel)
                    .problemType(p.getString("problemType"))
                    .lineNum(p.getString("lineNum"))
                    .problemCode(p.getString("problemCode"))
                    .problemDesc(p.getString("problemDesc"))
                    .riskEffect(p.getString("riskEffect"))
                    .optimizeSuggest(p.getString("optimizeSuggest"))
                    .fixCode(p.getString("fixCode"))
                    .ragRefTitle(p.getString("ragRefTitle"))
                    .build());
        }

        // === RAG规范引用 ===
        List<ReviewReportVO.RagReference> ragRefs = new ArrayList<>();
        if (ragContexts != null) {
            for (JSONObject ctx : ragContexts) {
                ragRefs.add(ReviewReportVO.RagReference.builder()
                        .title(ctx.getString("title"))
                        .type(ctx.getString("type"))
                        .content(ctx.getString("content"))
                        .build());
            }
        }

        // === 审查总结 ===
        String reportSummary = summary;
        if (reportSummary == null || reportSummary.isBlank()) {
            // LLM未返回总结时生成兜底总结
            String problemsSummary = items.stream()
                    .limit(5)
                    .map(i -> "[" + i.getRiskLevel() + "][" + i.getProblemType() + "] " + i.getProblemDesc())
                    .collect(Collectors.joining("\n"));
            reportSummary = llmReviewer.generateReviewSummary(score, high, mid, low, problemsSummary);
        }

        return ReviewReportVO.builder()
                .taskId(taskId)
                .taskNo(taskNo)
                .codeName(codeName)
                .codeType(codeType)
                .codeScore(score)
                .reviewSummary(reportSummary)
                .riskSummary(ReviewReportVO.RiskSummary.builder()
                        .high(high).medium(mid).low(low).total(high + mid + low)
                        .build())
                .problems(items)
                .ragReferences(ragRefs)
                .optimizeSummary("详见各问题优化方案")
                .build();
    }

    /** VO → JSONObject (用于存储到reportJson字段) */
    public JSONObject toJson(ReviewReportVO report) {
        return (JSONObject) JSON.toJSON(report);
    }

    /** 构建精简版报告JSON */
    public JSONObject buildReportJson(ReviewReportVO report, List<JSONObject> rawProblems) {
        JSONObject json = new JSONObject();
        json.put("taskId", report.getTaskId());
        json.put("taskNo", report.getTaskNo());
        json.put("codeScore", report.getCodeScore());
        json.put("reviewSummary", report.getReviewSummary());
        json.put("riskSummary", JSON.toJSON(report.getRiskSummary()));
        JSONArray problemsJson = new JSONArray();
        for (ReviewReportVO.ProblemItem item : report.getProblems()) {
            problemsJson.add(JSON.toJSON(item));
        }
        json.put("problems", problemsJson);
        return json;
    }
}
