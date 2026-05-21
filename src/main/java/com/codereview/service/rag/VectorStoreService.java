package com.codereview.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import com.codereview.util.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * 向量存储与检索服务
 *
 * <p>管理知识块的向量存储、缓存和相似度搜索。
 * 当前为MySQL兼容实现(向量存JSON字符串)，大规模场景可切换至Pgvector或Milvus。</p>
 *
 * <p>核心特性:
 * <ul>
 *   <li><b>向量存储</b>: 单条/批量存储向量到数据库</li>
 *   <li><b>内存缓存</b>: ConcurrentHashMap缓存热点向量，避免重复JSON解析</li>
 *   <li><b>Top-K搜索</b>: 使用小顶堆实现(Top-K = O(N log K))，避免全排序</li>
 *   <li><b>可扩展</b>: 向量存储接口与检索逻辑分离，切换向量库只需替换store/search方法</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class VectorStoreService {

    private final RagKnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    /** 向量内存缓存: chunkId → 向量列表，避免反复从JSON反序列化 */
    private final Map<Long, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    public VectorStoreService(RagKnowledgeChunkMapper chunkMapper, EmbeddingService embeddingService) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 存储单条向量
     *
     * <p>将文本向量化结果写入数据库并更新内存缓存。</p>
     *
     * @param chunkId       知识块ID
     * @param text          原始文本(用于估算token数)
     * @param embeddingJson JSON格式的向量字符串
     */
    public void store(Long chunkId, String text, String embeddingJson) {
        RagKnowledgeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk != null) {
            chunk.setEmbedding(embeddingJson);
            chunk.setTokenCount(text.length() / 2);
            chunkMapper.updateById(chunk);
            // 更新内存缓存
            cacheEmbedding(chunkId, JsonParser.parseEmbedding(embeddingJson));
            log.debug("向量存储成功: chunkId={}", chunkId);
        }
    }

    /**
     * 批量存储向量(文档分块入库)
     */
    public void batchStore(Map<Long, String> chunkTexts, List<String> embeddingJsons) {
        int i = 0;
        for (Map.Entry<Long, String> entry : chunkTexts.entrySet()) {
            if (i < embeddingJsons.size()) {
                store(entry.getKey(), entry.getValue(), embeddingJsons.get(i));
                i++;
            }
        }
        log.info("批量向量存储完成: {}条", chunkTexts.size());
    }

    /**
     * 向量相似度搜索(Top-K)
     *
     * <p>使用小顶堆实现Top-K搜索:
     * <ol>
     *   <li>遍历所有已向量化的chunk(后续可优化为向量索引)</li>
     *   <li>计算查询向量与每个chunk向量的余弦相似度</li>
     *   <li>维护大小为K的小顶堆，保留相似度最高的K个结果</li>
     *   <li>结果按相似度降序排列返回</li>
     * </ol>
     * 时间复杂度: O(N log K)，N为总chunk数。
     * 大规模场景建议迁移至Milvus或Pgvector的ivfflat索引。
     * </p>
     *
     * @param queryVector 查询文本的向量
     * @param topK        返回Top-K个结果
     * @return 按相似度降序排列的搜索结果列表
     */
    public List<SearchResult> search(List<Float> queryVector, int topK) {
        // 查询所有已向量化的chunk
        List<RagKnowledgeChunk> allChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<RagKnowledgeChunk>()
                        .isNotNull(RagKnowledgeChunk::getEmbedding)
                        .ne(RagKnowledgeChunk::getEmbedding, "")
        );

        // 小顶堆: 堆顶是当前K个中相似度最低的
        PriorityQueue<SearchResult> pq = new PriorityQueue<>(
                Comparator.comparingDouble(SearchResult::score)
        );

        for (RagKnowledgeChunk chunk : allChunks) {
            try {
                List<Float> chunkVec = getOrLoadEmbedding(chunk);
                if (chunkVec.isEmpty()) continue;

                double similarity = embeddingService.cosineSimilarity(queryVector, chunkVec);
                // 维护堆大小 ≤ K
                if (pq.size() < topK) {
                    pq.offer(new SearchResult(chunk, similarity));
                } else if (similarity > pq.peek().score()) {
                    pq.poll();
                    pq.offer(new SearchResult(chunk, similarity));
                }
            } catch (Exception e) {
                log.warn("向量相似度计算异常: chunkId={}", chunk.getId(), e);
            }
        }

        // 从小顶堆取出，按相似度降序排列
        List<SearchResult> results = new ArrayList<>(pq);
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results;
    }

    /**
     * 从缓存或数据库获取chunk的向量
     *
     * <p>优先从内存缓存读取(避免JSON反序列化开销)，
     * 缓存未命中时从数据库加载并写入缓存。</p>
     */
    private List<Float> getOrLoadEmbedding(RagKnowledgeChunk chunk) {
        return embeddingCache.computeIfAbsent(chunk.getId(),
                id -> {
                    try {
                        return JsonParser.parseEmbedding(chunk.getEmbedding());
                    } catch (Exception e) {
                        log.warn("向量解析失败: chunkId={}", id);
                        return List.of();
                    }
                });
    }

    private void cacheEmbedding(Long id, List<Float> vec) {
        embeddingCache.put(id, vec);
    }

    /** 清除内存缓存(Embedding模型切换后调用) */
    public void clearCache() {
        embeddingCache.clear();
        log.info("向量缓存已清除");
    }

    /**
     * 搜索结果记录
     *
     * @param chunk 命中的知识块
     * @param score 余弦相似度得分(0.0-1.0)
     */
    public record SearchResult(RagKnowledgeChunk chunk, double score) {}
}
