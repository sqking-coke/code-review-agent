package com.codereview.service.rag;

import com.alibaba.fastjson2.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;

/**
 * RAG混合检索服务 - 核心检索引擎
 *
 * <p>实现多路召回 + 融合排序的混合检索策略，
 * 是RAG知识增强层的核心组件。</p>
 *
 * <p><b>检索流程:</b>
 * <ol>
 *   <li><b>向量语义召回</b>: 代码 → Embedding → 余弦相似度Top-K搜索</li>
 *   <li><b>关键词召回</b>: 提取代码标识符 → BM25级文本匹配</li>
 *   <li><b>多路融合</b>: 加权合并向量+关键词结果(可配置权重)</li>
 *   <li><b>相似度过滤</b>: 低于阈值(0.65)的片段丢弃</li>
 *   <li><b>Top-K输出</b>: 取Top-K最终结果注入LLM Prompt</li>
 * </ol>
 * </p>
 *
 * <p><b>上下文组装:</b>
 * 检索结果按知识类型分类:
 * <ul>
 *   <li>STANDARD(规范) → buildSpecContext → 注入审查Prompt规范部分</li>
 *   <li>CASE/PATTERN(案例/模式) → buildCaseContext → 注入审查Prompt案例部分</li>
 *   <li>PRACTICE(实践) → buildBestPracticeContext → 注入优化Prompt</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final RagKnowledgeDocMapper docMapper;
    private final RagRetrievalLogMapper retrievalLogMapper;

    /** 向量语义召回数量(粗排) */
    @Value("${rag.vector-recall-topk:20}")
    private int vectorRecallTopK;

    /** 关键词召回数量(粗排) */
    @Value("${rag.keyword-recall-topk:10}")
    private int keywordRecallTopK;

    /** 精排后注入LLM的最终数量 */
    @Value("${rag.final-topk:5}")
    private int finalTopK;

    /** 相似度阈值(低于此值丢弃) */
    @Value("${rag.similarity-threshold:0.65}")
    private double similarityThreshold;

    /** 向量语义召回权重 */
    @Value("${rag.fusion-weight-vector:0.5}")
    private double weightVector;

    /** 关键词召回权重 */
    @Value("${rag.fusion-weight-keyword:0.3}")
    private double weightKeyword;

    /** 标签过滤权重 */
    @Value("${rag.fusion-weight-tag:0.2}")
    private double weightTag;

    public RetrievalService(EmbeddingService embeddingService, VectorStoreService vectorStoreService,
                            RagKnowledgeChunkMapper chunkMapper, RagKnowledgeDocMapper docMapper,
                            RagRetrievalLogMapper retrievalLogMapper) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.retrievalLogMapper = retrievalLogMapper;
    }

    /**
     * 混合检索入口
     *
     * <p>对提交的代码执行向量+关键词多路召回，融合排序后返回Top-K结果。</p>
     *
     * @param code  提交审查的代码(作为查询文本)
     * @param topK  最终返回数量
     * @return 检索结果JSON(含hits列表、耗时等)
     */
    public JSONObject hybridSearch(String code, int topK) {
        long startTime = System.currentTimeMillis();

        // ① 查询代码向量化
        List<Float> queryVector = embeddingService.embedRaw(code);

        // ② 向量语义召回: Top-N相似chunk
        List<VectorStoreService.SearchResult> vectorResults =
                vectorStoreService.search(queryVector, vectorRecallTopK);

        // ③ 关键词召回: 提取代码标识符 → 文本匹配
        List<RagKnowledgeChunk> keywordResults = keywordSearch(code, keywordRecallTopK);

        // ④ 多路融合 + 加权排序
        List<ScoredChunk> fused = fusionResults(vectorResults, keywordResults);

        // ⑤ 相似度过滤 + Top-K截断
        List<ScoredChunk> finalResults = fused.stream()
                .filter(s -> s.score >= similarityThreshold)
                .limit(topK)
                .toList();

        // ⑥ 更新命中计数(热度指标)
        for (ScoredChunk sc : finalResults) {
            chunkMapper.incrementHitCount(sc.chunk.getId());
        }

        int costMs = (int) (System.currentTimeMillis() - startTime);
        // ⑦ 记录检索日志
        logRetrieval(null, code, "HYBRID", topK, finalResults, costMs);

        JSONObject result = new JSONObject();
        result.put("queryText", code);
        result.put("totalHits", finalResults.size());
        result.put("retrievalCostMs", costMs);
        result.put("hits", finalResults.stream().map(this::toHitJson).toList());

        log.info("RAG混合检索完成: 向量召回={}, 关键词召回={}, 融合后={}, 命中={}, 耗时={}ms",
                vectorResults.size(), keywordResults.size(), fused.size(), finalResults.size(), costMs);
        return result;
    }

    /**
     * 关键词检索: 提取代码关键标识符进行全文匹配
     *
     * <p>提取代码中的变量名、方法名、类名等标识符(长度≥3)，
     * 在知识库chunk中匹配，按命中关键词数量评分。</p>
     */
    private List<RagKnowledgeChunk> keywordSearch(String code, int topK) {
        List<String> keywords = extractKeywords(code);
        if (keywords.isEmpty()) return List.of();

        List<RagKnowledgeChunk> allChunks = chunkMapper.selectList(null);
        if (allChunks.isEmpty()) return List.of();

        return allChunks.stream()
                .map(chunk -> new AbstractMap.SimpleEntry<>(chunk,
                        keywordScore(chunk.getChunkContent(), keywords)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 计算chunk文本与关键词列表的匹配得分 */
    private double keywordScore(String text, List<String> keywords) {
        if (text == null) return 0;
        String lower = text.toLowerCase();
        double score = 0;
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                score += 1.0;
            }
        }
        return score / keywords.size();
    }

    /**
     * 代码关键词提取
     *
     * <p>从代码中提取单词级标识符: 按代码分隔符切分，
     * 过滤长度≥3的有意义单词，至多保留20个关键词。</p>
     */
    private List<String> extractKeywords(String code) {
        Set<String> keywords = new LinkedHashSet<>();
        String[] words = code.split("[\\s(){}\\[\\].,;=+\\-*/<>!&|]+");
        for (String word : words) {
            if (word.length() >= 3 && !word.isBlank()) {
                keywords.add(word);
            }
        }
        return keywords.stream().limit(20).toList();
    }

    /**
     * 多路召回结果融合排序
     *
     * <p>融合策略:
     * <ul>
     *   <li>向量结果: 按最大得分数归一化后 × 向量权重</li>
     *   <li>关键词结果: 按排名倒数归一化后 × 关键词权重</li>
     *   <li>相同chunk被多路命中 → 得分累加(boost效果)</li>
     * </ul>
     * </p>
     */
    private List<ScoredChunk> fusionResults(List<VectorStoreService.SearchResult> vectorResults,
                                             List<RagKnowledgeChunk> keywordResults) {
        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, RagKnowledgeChunk> chunkMap = new HashMap<>();

        // 向量得分归一化 + 加权
        double maxVectorScore = vectorResults.stream()
                .mapToDouble(VectorStoreService.SearchResult::score)
                .max().orElse(1.0);
        for (VectorStoreService.SearchResult vr : vectorResults) {
            double normalized = vr.score() / maxVectorScore;
            scoreMap.merge(vr.chunk().getId(), normalized * weightVector, Double::sum);
            chunkMap.putIfAbsent(vr.chunk().getId(), vr.chunk());
        }

        // 关键词得分归一化(按排名) + 加权
        int maxKeywordRank = Math.max(keywordResults.size(), 1);
        for (int i = 0; i < keywordResults.size(); i++) {
            RagKnowledgeChunk chunk = keywordResults.get(i);
            double keywordScore = 1.0 - (double) i / maxKeywordRank;
            scoreMap.merge(chunk.getId(), keywordScore * weightKeyword, Double::sum);
            chunkMap.putIfAbsent(chunk.getId(), chunk);
        }

        return scoreMap.entrySet().stream()
                .map(e -> new ScoredChunk(chunkMap.get(e.getKey()), e.getValue()))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());
    }

    /** 将检索命中结果转为前端可展示的JSON格式 */
    private JSONObject toHitJson(ScoredChunk sc) {
        RagKnowledgeDoc doc = docMapper.selectById(sc.chunk.getDocId());
        JSONObject hit = new JSONObject();
        hit.put("chunkId", sc.chunk.getId());
        hit.put("docId", sc.chunk.getDocId());
        hit.put("docName", doc != null ? doc.getDocName() : "未知");
        hit.put("docType", doc != null ? doc.getDocType() : "未知");
        hit.put("chunkContent", sc.chunk.getChunkContent());
        hit.put("chunkSummary", sc.chunk.getChunkSummary());
        hit.put("similarityScore", sc.score);
        hit.put("qualityScore", sc.chunk.getQualityScore());
        hit.put("isVerified", sc.chunk.getIsVerified() == 1);
        return hit;
    }

    /** 记录检索日志(用于监控和分析) */
    private void logRetrieval(Long taskId, String query, String method, int topK,
                               List<ScoredChunk> results, int costMs) {
        RagRetrievalLog logEntry = RagRetrievalLog.builder()
                .taskId(taskId != null ? taskId : 0L)
                .queryText(query)
                .retrievalMethod(method)
                .topK(topK)
                .resultChunkIds(results.stream().map(s -> String.valueOf(s.chunk.getId()))
                        .collect(Collectors.joining(",")))
                .similarityScores(results.stream().map(s -> String.format("%.4f", s.score))
                        .collect(Collectors.joining(",")))
                .retrievalCostMs(costMs)
                .isHit(results.isEmpty() ? 0 : 1)
                .hitCount(results.size())
                .createTime(LocalDateTime.now())
                .build();
        retrievalLogMapper.insert(logEntry);
    }

    // ==================== 上下文组装方法 ====================

    /** 构建完整的RAG上下文列表 */
    public List<JSONObject> buildRagContextList(JSONObject searchResult) {
        var hits = searchResult.getJSONArray("hits");
        if (hits == null) return List.of();
        List<JSONObject> contexts = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            JSONObject hit = hits.getJSONObject(i);
            JSONObject ctx = new JSONObject();
            ctx.put("title", hit.getString("docName"));
            ctx.put("type", hit.getString("docType"));
            ctx.put("content", hit.getString("chunkContent"));
            ctx.put("similarityScore", hit.getDouble("similarityScore"));
            ctx.put("chunkId", hit.getLong("chunkId"));
            ctx.put("isVerified", hit.getBoolean("isVerified"));
            contexts.add(ctx);
        }
        return contexts;
    }

    /** 构建企业规范上下文(过滤STANDARD类型) */
    public String buildSpecContext(List<JSONObject> contexts) {
        return contexts.stream()
                .filter(c -> "STANDARD".equals(c.getString("type")))
                .map(c -> "【规范】" + c.getString("title") + "\n" + c.getString("content"))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /** 构建历史案例上下文(过滤CASE和PATTERN类型) */
    public String buildCaseContext(List<JSONObject> contexts) {
        return contexts.stream()
                .filter(c -> "CASE".equals(c.getString("type")) || "PATTERN".equals(c.getString("type")))
                .map(c -> "【案例】" + c.getString("title") + "\n" + c.getString("content"))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /** 构建最佳实践上下文(过滤PRACTICE类型，用于代码优化) */
    public String buildBestPracticeContext(List<JSONObject> contexts) {
        return contexts.stream()
                .filter(c -> "PRACTICE".equals(c.getString("type")))
                .map(c -> "【最佳实践】" + c.getString("title") + "\n" + c.getString("content"))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /** 内部使用: 融合排序结果记录 */
    private record ScoredChunk(RagKnowledgeChunk chunk, double score) {}
}
