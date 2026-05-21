package com.codereview.service;

import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.codereview.dto.request.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.exception.*;
import com.codereview.mapper.*;
import com.codereview.service.rag.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.*;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/**
 * RAG知识库管理业务服务
 *
 * <p>管理RAG知识库的完整生命周期:
 * <ul>
 *   <li><b>文档上传</b>: 文件解析 → 分块 → 向量化 → 入库(全自动)</li>
 *   <li><b>文档管理</b>: 列表查询、状态筛选、级联删除</li>
 *   <li><b>向量重建</b>: Embedding模型切换后的批量重向量化</li>
 *   <li><b>检索测试</b>: 输入查询文本查看检索效果，用于调参</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class RagKnowledgeService {

    private final RagKnowledgeDocMapper docMapper;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;

    public RagKnowledgeService(RagKnowledgeDocMapper docMapper, RagKnowledgeChunkMapper chunkMapper,
                                DocumentParser documentParser, TextChunker textChunker,
                                EmbeddingService embeddingService, RetrievalService retrievalService) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
    }

    /**
     * 上传知识文档并自动完成向量化入库
     *
     * <p>全自动流程:
     * <ol>
     *   <li>解析文档格式(MD/PDF/DOCX/TXT)</li>
     *   <li>智能分块(语义边界切分)</li>
     *   <li>批量向量化(调用Embedding API)</li>
     *   <li>写入文档+chunk表</li>
     * </ol>
     * </p>
     *
     * @param file        上传文件
     * @param docType     知识类型: STANDARD/CASE/PRACTICE/PATTERN
     * @param docLanguage 适用语言
     * @param createBy    创建人
     * @return 创建的文档实体
     */
    @Transactional
    public RagKnowledgeDoc uploadDoc(MultipartFile file, String docType, String docLanguage,
                                      String createBy) throws Exception {
        // ① 文档解析
        String content = documentParser.parse(file);
        String filename = file.getOriginalFilename();
        String format = filename != null && filename.contains(".") ?
                filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() : "txt";

        // ② 创建文档记录
        RagKnowledgeDoc doc = RagKnowledgeDoc.builder()
                .docName(filename != null ? filename : "unknown")
                .docType(docType != null ? docType : "STANDARD")
                .docLanguage(docLanguage != null ? docLanguage : "通用")
                .docFormat(format)
                .docContent(content)
                .docSummary(content.length() > 200 ? content.substring(0, 200) + "..." : content)
                .chunkCount(0)
                .docStatus(0) // 处理中
                .createBy(createBy)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        docMapper.insert(doc);

        // ③ 智能分块 + 批量向量化
        List<String> chunks = textChunker.chunk(content);
        List<String> embeddingJsons = embeddingService.embedBatchAsJson(chunks);

        // ④ 写入chunk表
        for (int i = 0; i < chunks.size(); i++) {
            RagKnowledgeChunk chunk = RagKnowledgeChunk.builder()
                    .docId(doc.getId())
                    .chunkIndex(i)
                    .chunkContent(chunks.get(i))
                    .chunkSummary(chunks.get(i).length() > 300
                            ? chunks.get(i).substring(0, 300) : chunks.get(i))
                    .tokenCount(chunks.get(i).length() / 2)
                    .embedding(i < embeddingJsons.size() ? embeddingJsons.get(i) : null)
                    .embeddingModel("bge-large-zh")
                    .qualityScore(BigDecimal.valueOf(3.0))
                    .isVerified(0)
                    .createTime(LocalDateTime.now())
                    .build();
            chunkMapper.insert(chunk);
        }

        // ⑤ 更新文档状态为已向量化
        doc.setChunkCount(chunks.size());
        doc.setDocStatus(1);
        docMapper.updateById(doc);

        log.info("知识文档上传完成: docId={}, chunks={}", doc.getId(), chunks.size());
        return doc;
    }

    /** 分页查询知识文档列表(支持类型、语言、状态筛选) */
    public Page<RagKnowledgeDoc> listDocs(int pageNum, int pageSize,
                                           String docType, String docLanguage, Integer docStatus) {
        LambdaQueryWrapper<RagKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        if (docType != null && !docType.isBlank()) {
            wrapper.eq(RagKnowledgeDoc::getDocType, docType);
        }
        if (docLanguage != null && !docLanguage.isBlank()) {
            wrapper.eq(RagKnowledgeDoc::getDocLanguage, docLanguage);
        }
        if (docStatus != null) {
            wrapper.eq(RagKnowledgeDoc::getDocStatus, docStatus);
        }
        wrapper.orderByDesc(RagKnowledgeDoc::getCreateTime);
        return docMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 查询文档的所有分块(用于可视化分块效果) */
    public List<RagKnowledgeChunk> getChunks(Long docId) {
        return chunkMapper.selectByDocId(docId);
    }

    /** 级联删除文档及其所有分块 */
    @Transactional
    public void deleteDoc(Long docId) {
        List<RagKnowledgeChunk> chunks = chunkMapper.selectByDocId(docId);
        for (RagKnowledgeChunk chunk : chunks) {
            chunkMapper.deleteById(chunk.getId());
        }
        docMapper.deleteById(docId);
        log.info("知识文档已删除: docId={}, chunks={}", docId, chunks.size());
    }

    /**
     * 重建向量索引
     *
     * <p>适用于: Embedding模型切换后批量重建。
     * 先删除旧chunk，重新分块+向量化写入新chunk。</p>
     */
    @Transactional
    public void reindexDoc(Long docId) {
        RagKnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "知识文档不存在");
        }

        // 删除旧chunk
        List<RagKnowledgeChunk> oldChunks = chunkMapper.selectByDocId(docId);
        for (RagKnowledgeChunk c : oldChunks) {
            chunkMapper.deleteById(c.getId());
        }

        // 重新分块+向量化
        List<String> chunks = textChunker.chunk(doc.getDocContent());
        List<String> embeddingJsons = embeddingService.embedBatchAsJson(chunks);

        for (int i = 0; i < chunks.size(); i++) {
            RagKnowledgeChunk chunk = RagKnowledgeChunk.builder()
                    .docId(doc.getId())
                    .chunkIndex(i)
                    .chunkContent(chunks.get(i))
                    .chunkSummary(chunks.get(i).length() > 300
                            ? chunks.get(i).substring(0, 300) : chunks.get(i))
                    .tokenCount(chunks.get(i).length() / 2)
                    .embedding(i < embeddingJsons.size() ? embeddingJsons.get(i) : null)
                    .embeddingModel("bge-large-zh")
                    .qualityScore(BigDecimal.valueOf(3.0))
                    .isVerified(0)
                    .createTime(LocalDateTime.now())
                    .build();
            chunkMapper.insert(chunk);
        }

        doc.setChunkCount(chunks.size());
        doc.setDocStatus(1);
        doc.setUpdateTime(LocalDateTime.now());
        docMapper.updateById(doc);

        log.info("向量索引重建完成: docId={}, newChunks={}", docId, chunks.size());
    }

    /** 检索测试: 输入查询文本查看检索效果 */
    public RagSearchResultVO search(RagSearchRequest request) {
        JSONObject result = retrievalService.hybridSearch(request.getQueryText(), request.getTopK());

        var hits = result.getJSONArray("hits");
        List<RagSearchResultVO.HitItem> hitItems = hits.stream()
                .map(h -> {
                    JSONObject hit = (JSONObject) h;
                    return RagSearchResultVO.HitItem.builder()
                            .chunkId(hit.getLong("chunkId"))
                            .docId(hit.getLong("docId"))
                            .docName(hit.getString("docName"))
                            .docType(hit.getString("docType"))
                            .chunkContent(hit.getString("chunkContent"))
                            .chunkSummary(hit.getString("chunkSummary"))
                            .similarityScore(hit.getDouble("similarityScore"))
                            .qualityScore(hit.getBigDecimal("qualityScore"))
                            .isVerified(hit.getBooleanValue("isVerified"))
                            .build();
                }).collect(Collectors.toList());

        return RagSearchResultVO.builder()
                .queryText(request.getQueryText())
                .totalHits(hitItems.size())
                .retrievalCostMs(result.getIntValue("retrievalCostMs"))
                .hits(hitItems)
                .build();
    }
}
