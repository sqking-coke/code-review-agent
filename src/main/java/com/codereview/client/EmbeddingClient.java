package com.codereview.client;

import com.alibaba.fastjson2.*;
import com.codereview.config.*;
import lombok.extern.slf4j.*;
import okhttp3.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Embedding(文本向量化) HTTP客户端
 *
 * <p>基于OkHttp调用Embedding API，将文本转换为稠密向量。
 * 支持:
 * <ul>
 *   <li>本地部署模型: BGE-large-zh / text2vec-large-chinese (隐私安全)</li>
 *   <li>云端API服务: 通义千问Embedding / 智谱Embedding 等</li>
 * </ul>
 * </p>
 *
 * <p>向量用途:
 * <ul>
 *   <li>RAG知识库: 文档chunk向量化存储</li>
 *   <li>相似检索: 查询代码向量与知识库向量计算余弦相似度</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final EmbeddingConfig config;
    private final OkHttpClient httpClient;

    public EmbeddingClient(EmbeddingConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 单文本向量化
     *
     * @param text 待向量化的文本
     * @return 浮点数向量列表，维度由模型决定(如1024)
     */
    public List<Float> embed(String text) {
        List<List<Float>> result = embedBatch(List.of(text));
        return result.isEmpty() ? List.of() : result.getFirst();
    }

    /**
     * 批量文本向量化
     *
     * <p>一次API调用处理多条文本，显著提升批量向量化效率。
     * 用于文档分块后的批量入库场景。</p>
     *
     * @param texts 待向量化的文本列表
     * @return 每条文本对应的向量列表
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("input", texts);

        Request.Builder builder = new Request.Builder()
                .url(config.getApiEndpoint() + "/embeddings")
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")));

        // 本地部署可能不需要API Key
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try {
            Request request = builder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    throw new IOException("Embedding HTTP " + response.code() + ": " + errBody);
                }
                String respBody = response.body() != null ? response.body().string() : "";
                JSONObject respJson = JSON.parseObject(respBody);
                JSONArray data = respJson.getJSONArray("data");
                List<List<Float>> result = new ArrayList<>();
                if (data != null) {
                    for (int i = 0; i < data.size(); i++) {
                        JSONArray embedding = data.getJSONObject(i).getJSONArray("embedding");
                        List<Float> vec = new ArrayList<>();
                        for (int j = 0; j < embedding.size(); j++) {
                            vec.add(embedding.getFloat(j));
                        }
                        result.add(vec);
                    }
                }
                return result;
            }
        } catch (IOException e) {
            log.error("Embedding调用失败", e);
            throw new RuntimeException("Embedding调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * <p>公式: cosθ = (A·B) / (|A|·|B|)
     * 返回值范围 [0, 1]，越接近1表示语义越相似。</p>
     *
     * @param vec1 向量1
     * @param vec2 向量2
     * @return 余弦相似度(0.0-1.0)
     * @throws IllegalArgumentException 向量维度不匹配
     */
    public double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1.size() != vec2.size()) {
            throw new IllegalArgumentException("向量维度不匹配: " + vec1.size() + " vs " + vec2.size());
        }
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int i = 0; i < vec1.size(); i++) {
            dot += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        // 防止除零
        if (norm1 == 0 || norm2 == 0) return 0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
