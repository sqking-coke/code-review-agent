package com.codereview.service.rag;

import com.codereview.client.*;
import com.codereview.util.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * Embedding向量化服务
 *
 * <p>封装 {@link EmbeddingClient} 提供文本向量化相关的业务方法:
 * <ul>
 *   <li>单文本向量化(返回原始向量和JSON格式)</li>
 *   <li>批量文本向量化(用于文档分块后批量入库)</li>
 *   <li>余弦相似度计算(向量检索核心算法)</li>
 *   <li>向量JSON序列化/反序列化</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;

    public EmbeddingService(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    /**
     * 单文本向量化(返回原始向量)
     */
    public List<Float> embedRaw(String text) {
        return embeddingClient.embed(text);
    }

    /**
     * 单文本向量化(返回JSON字符串，用于MySQL存储)
     */
    public String embedAsJson(String text) {
        List<Float> vec = embeddingClient.embed(text);
        return JsonParser.toEmbeddingJson(vec);
    }

    /**
     * 批量文本向量化(返回JSON字符串列表)
     *
     * <p>一次API调用处理多条文本，大幅提升文档chunk批量入库效率。</p>
     */
    public List<String> embedBatchAsJson(List<String> texts) {
        List<List<Float>> vectors = embeddingClient.embedBatch(texts);
        List<String> result = new ArrayList<>();
        for (List<Float> vec : vectors) {
            result.add(JsonParser.toEmbeddingJson(vec));
        }
        return result;
    }

    /**
     * JSON格式向量 → 余弦相似度
     */
    public double cosineSimilarity(String embeddingJson1, String embeddingJson2) {
        List<Float> vec1 = JsonParser.parseEmbedding(embeddingJson1);
        List<Float> vec2 = JsonParser.parseEmbedding(embeddingJson2);
        return embeddingClient.cosineSimilarity(vec1, vec2);
    }

    /**
     * JSON字符串 → 向量List
     */
    public List<Float> parseEmbedding(String embeddingJson) {
        return JsonParser.parseEmbedding(embeddingJson);
    }

    /**
     * 原始向量 → 余弦相似度
     */
    public double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        return embeddingClient.cosineSimilarity(vec1, vec2);
    }
}
