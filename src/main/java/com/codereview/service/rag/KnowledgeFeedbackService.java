package com.codereview.service.rag;

import com.codereview.entity.*;
import com.codereview.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.math.*;
import java.time.*;

/**
 * 知识回流与自学习服务
 *
 * <p>实现RAG知识库的「审查 → 学习 → 提升」正向循环:
 * <ol>
 *   <li><b>用户反馈</b>: 对检索结果点赞/点踩，驱动检索排序优化</li>
 *   <li><b>案例回流</b>: 高质量审查结果(评分≥80)自动提取为知识案例</li>
 *   <li><b>质量评分</b>: 每次反馈±0.1调整知识块质量评分(0.00-5.00)</li>
 * </ol>
 * </p>
 *
 * <p>回流案例需人工审核(is_verified=0)，避免低质量内容污染知识库。</p>
 */
@Slf4j
@Component
public class KnowledgeFeedbackService {

    private final CodeReviewTaskMapper taskMapper;
    private final CodeReviewDetailMapper detailMapper;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final RagKnowledgeDocMapper docMapper;
    private final EmbeddingService embeddingService;

    public KnowledgeFeedbackService(CodeReviewTaskMapper taskMapper, CodeReviewDetailMapper detailMapper,
                                     RagKnowledgeChunkMapper chunkMapper, RagKnowledgeDocMapper docMapper,
                                     EmbeddingService embeddingService) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 用户对知识检索结果提交反馈
     *
     * <p>反馈影响:
     * <ul>
     *   <li>正向(+1): qualityScore +0.1 (上限5.00)</li>
     *   <li>负向(-1): qualityScore -0.1 (下限0.00)</li>
     *   <li>高质量(>4.0)的chunk优先展示、优先召回</li>
     * </ul>
     * </p>
     *
     * @param chunkId       知识块ID
     * @param feedbackScore 1(有用) / -1(无用)
     */
    @Transactional
    public void submitFeedback(Long chunkId, int feedbackScore) {
        RagKnowledgeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk != null) {
            chunk.setFeedbackScore(feedbackScore);
            // 动态调整质量评分
            BigDecimal current = chunk.getQualityScore() != null ? chunk.getQualityScore() : BigDecimal.valueOf(3.0);
            if (feedbackScore > 0) {
                chunk.setQualityScore(current.add(BigDecimal.valueOf(0.1)).min(BigDecimal.valueOf(5.00)));
            } else {
                chunk.setQualityScore(current.subtract(BigDecimal.valueOf(0.1)).max(BigDecimal.ZERO));
            }
            chunkMapper.updateById(chunk);
            log.info("知识反馈已记录: chunkId={}, feedbackScore={}, newQualityScore={}",
                    chunkId, feedbackScore, chunk.getQualityScore());
        }
    }

    /**
     * 高质量审查案例回流入知识库
     *
     * <p>回流条件:
     * <ul>
     *   <li>审查评分 ≥ 80 (高质量审查结果)</li>
     *   <li>问题明细包含修复代码(有参考价值)</li>
     * </ul>
     * </p>
     *
     * <p>回流流程:
     * <ol>
     *   <li>创建CASE类型知识文档</li>
     *   <li>提取问题代码+修复代码生成chunk</li>
     *   <li>向量化入库</li>
     *   <li>标记is_verified=0(待人工审核)</li>
     * </ol>
     * </p>
     *
     * @param taskId   审查任务ID
     * @param detailId 问题明细ID
     */
    @Transactional
    public void submitReviewCaseToKnowledge(Long taskId, Long detailId) {
        CodeReviewTask task = taskMapper.selectById(taskId);
        CodeReviewDetail detail = detailMapper.selectById(detailId);
        if (task == null || detail == null) {
            log.warn("知识回流失败: task或detail不存在");
            return;
        }

        // 评分低于80的审查结果不自动回流(避免低质量案例)
        if (task.getCodeScore() != null && task.getCodeScore() < 80) {
            log.info("审查评分低于80，跳过自动回流: taskId={}, score={}", taskId, task.getCodeScore());
            return;
        }

        // 创建知识文档
        RagKnowledgeDoc doc = RagKnowledgeDoc.builder()
                .docName("审查案例回流-" + task.getTaskNo())
                .docType("CASE")
                .docLanguage(task.getCodeType())
                .docFormat("txt")
                .docContent(buildCaseContent(task, detail))
                .docSummary("自动回流: " + truncate(detail.getProblemDesc(), 200))
                .source("审查回流")
                .docStatus(0)
                .createBy("SYSTEM")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        docMapper.insert(doc);

        // 生成向量化chunk
        String chunkText = buildChunkText(detail);
        String embeddingJson = embeddingService.embedAsJson(chunkText);

        RagKnowledgeChunk chunk = RagKnowledgeChunk.builder()
                .docId(doc.getId())
                .chunkIndex(0)
                .chunkContent(chunkText)
                .chunkSummary(truncate(detail.getProblemDesc(), 300))
                .tokenCount(chunkText.length() / 2)
                .embedding(embeddingJson)
                .embeddingModel("bge-large-zh")
                .qualityScore(BigDecimal.valueOf(4.0)) // 回流案例初始评分较高
                .isVerified(0) // 待人工审核
                .createTime(LocalDateTime.now())
                .build();
        chunkMapper.insert(chunk);

        doc.setChunkCount(1);
        doc.setDocStatus(1);
        docMapper.updateById(doc);

        log.info("审查案例回流入库完成: taskId={}, docId={}, chunkId={}", taskId, doc.getId(), chunk.getId());
    }

    /** 构建回流案例的文档内容(含问题类型、风险等级、代码对比) */
    private String buildCaseContent(CodeReviewTask task, CodeReviewDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 问题类型\n").append(detail.getProblemType()).append("\n\n");
        sb.append("## 风险等级\n").append(detail.getRiskLevel()).append("\n\n");
        sb.append("## 问题描述\n").append(detail.getProblemDesc()).append("\n\n");
        if (detail.getProblemCode() != null) {
            sb.append("## 问题代码\n```java\n").append(detail.getProblemCode()).append("\n```\n\n");
        }
        if (detail.getFixCode() != null) {
            sb.append("## 修复代码\n```java\n").append(detail.getFixCode()).append("\n```\n\n");
        }
        if (detail.getOptimizeSuggest() != null) {
            sb.append("## 优化建议\n").append(detail.getOptimizeSuggest()).append("\n");
        }
        return sb.toString();
    }

    /** 构建向量化chunk的文本(精简版，适合检索匹配) */
    private String buildChunkText(CodeReviewDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题类型: ").append(detail.getProblemType()).append("\n");
        sb.append("风险等级: ").append(detail.getRiskLevel()).append("\n");
        sb.append("问题描述: ").append(detail.getProblemDesc()).append("\n");
        if (detail.getProblemCode() != null) {
            sb.append("问题代码: ").append(detail.getProblemCode()).append("\n");
        }
        if (detail.getFixCode() != null) {
            sb.append("修复代码: ").append(detail.getFixCode()).append("\n");
        }
        return sb.toString();
    }

    /** 文本截断(保留前maxLen字符) */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
