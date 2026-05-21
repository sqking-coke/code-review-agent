package com.codereview.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.codereview.dto.request.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.service.*;
import com.codereview.service.rag.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

import java.util.*;

/**
 * RAG知识库管理Controller
 *
 * <p>提供知识库的全生命周期管理REST API，包括:
 * <ul>
 *   <li><b>文档管理</b>: 上传、列表查询、分块查看、删除</li>
 *   <li><b>向量管理</b>: 重建向量索引(Embedding模型切换)</li>
 *   <li><b>检索测试</b>: 输入查询文本查看检索效果和相似度</li>
 *   <li><b>反馈闭环</b>: 检索结果反馈、高质量案例回流入库</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/agent/rag")
@RequiredArgsConstructor
public class RagKnowledgeController {

    private final RagKnowledgeService ragKnowledgeService;
    private final KnowledgeFeedbackService feedbackService;

    /**
     * 上传知识文档
     *
     * <p>支持格式: Markdown(.md)、纯文本(.txt)、PDF(.pdf)、Word(.docx)、
     * 以及代码文件(.java/.py/.go/.js/.ts/.sql)。
     * 上传后自动触发: 文档解析 → 智能分块 → 向量化 → 入库 全链路。</p>
     *
     * @param file        上传文件(multipart/form-data)
     * @param docType     知识类型: STANDARD(规范)/CASE(案例)/PRACTICE(实践)/PATTERN(缺陷模式)
     * @param docLanguage 适用语言: Java/Python/Go/通用
     * @param createBy    创建人标识
     * @return 创建的文档元信息
     */
    @PostMapping("/doc/upload")
    public Result<RagKnowledgeDoc> uploadDoc(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "STANDARD") String docType,
            @RequestParam(required = false, defaultValue = "通用") String docLanguage,
            @RequestParam(required = false) String createBy) {
        try {
            RagKnowledgeDoc doc = ragKnowledgeService.uploadDoc(file, docType, docLanguage, createBy);
            return Result.ok(doc);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.fail("文档上传处理失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询知识文档列表
     *
     * @param pageNum     页码(默认1)
     * @param pageSize    每页数量(默认20)
     * @param docType     知识类型筛选(可选)
     * @param docLanguage 适用语言筛选(可选)
     * @param docStatus   文档状态筛选(可选): 0-处理中, 1-已向量化, 2-已废弃
     */
    @GetMapping("/doc/list")
    public Result<Page<RagKnowledgeDoc>> listDocs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String docLanguage,
            @RequestParam(required = false) Integer docStatus) {
        Page<RagKnowledgeDoc> page = ragKnowledgeService.listDocs(
                pageNum, pageSize, docType, docLanguage, docStatus);
        return Result.ok(page);
    }

    /**
     * 获取文档的所有分块详情
     *
     * <p>用于可视化查看文档的分块效果，包括:
     * 分块内容、token数、向量化状态、检索命中统计等。</p>
     *
     * @param docId 文档ID
     * @return 该文档的所有chunk列表(按chunkIndex升序)
     */
    @GetMapping("/doc/chunks/{docId}")
    public Result<List<RagKnowledgeChunk>> getChunks(@PathVariable Long docId) {
        List<RagKnowledgeChunk> chunks = ragKnowledgeService.getChunks(docId);
        return Result.ok(chunks);
    }

    /**
     * 删除知识文档
     *
     * <p>级联删除: 文档 + 该文档的所有分块(含向量数据)。</p>
     *
     * @param docId 文档ID
     */
    @DeleteMapping("/doc/{docId}")
    public Result<Void> deleteDoc(@PathVariable Long docId) {
        ragKnowledgeService.deleteDoc(docId);
        return Result.ok();
    }

    /**
     * 重建向量索引
     *
     * <p>适用场景: Embedding模型切换后，批量删除旧向量并重新分块+向量化。
     * 原文档内容保持不变，仅重新生成chunk和embedding。</p>
     *
     * @param docId 文档ID
     */
    @PostMapping("/doc/{docId}/reindex")
    public Result<Void> reindexDoc(@PathVariable Long docId) {
        ragKnowledgeService.reindexDoc(docId);
        return Result.ok();
    }

    /**
     * 检索知识测试
     *
     * <p>用于测试和调优检索参数。输入查询文本(代码片段或自然语言描述)，
     * 返回Top-K匹配知识片段及其相似度得分。
     * 支持选择检索方式: VECTOR(仅向量)/KEYWORD(仅关键词)/HYBRID(混合，推荐)。</p>
     *
     * @param request 检索请求(查询文本、检索方式、Top-K数量)
     * @return 检索结果(命中列表、相似度得分、耗时)
     */
    @PostMapping("/search/test")
    public Result<RagSearchResultVO> searchTest(@Valid @RequestBody RagSearchRequest request) {
        RagSearchResultVO result = ragKnowledgeService.search(request);
        return Result.ok(result);
    }

    /**
     * 知识检索结果反馈
     *
     * <p>对检索命中的知识块进行点赞(+1)或点踩(-1)，
     * 驱动知识质量评分动态调整，影响后续检索排序。
     * 高质量(>4.0)的知识块在检索结果中优先展示。</p>
     *
     * @param chunkId       知识块ID
     * @param feedbackScore 反馈评分: 1-有用, -1-无用
     */
    @PostMapping("/feedback")
    public Result<Void> submitFeedback(
            @RequestParam Long chunkId,
            @RequestParam int feedbackScore) {
        feedbackService.submitFeedback(chunkId, feedbackScore);
        return Result.ok();
    }

    /**
     * 高质量审查案例回流入知识库
     *
     * <p>将审查评分≥80的优质案例提交至知识库，
     * 系统自动提取问题代码+修复代码，结构化后向量化入库。
     * 入库案例标记为「待审核」(is_verified=0)，需人工审核后正式启用。</p>
     *
     * @param request 回流请求(任务ID、问题明细ID)
     */
    @PostMapping("/feedback/submit")
    public Result<Void> submitKnowledgeFeedback(@Valid @RequestBody RagFeedbackSubmitRequest request) {
        feedbackService.submitReviewCaseToKnowledge(request.getTaskId(), request.getDetailId());
        return Result.ok();
    }
}
