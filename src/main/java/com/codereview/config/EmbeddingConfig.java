package com.codereview.config;

import lombok.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;

/**
 * Embedding模型配置类
 *
 * <p>用于文本向量化，支持本地部署(BGE-large-zh)和云端API两种模式。
 * 本地部署保障企业代码隐私安全；云端API适合快速启动验证。</p>
 *
 * <p>配置示例:
 * <pre>{@code
 * embedding:
 *   provider: local
 *   api-endpoint: http://localhost:8000
 *   model: bge-large-zh
 *   dimension: 1024
 * }</pre>
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingConfig {

    /** 向量化服务提供方式: local(本地部署) / api(云端服务) */
    private String provider = "local";

    /** Embedding API地址 */
    private String apiEndpoint = "http://localhost:8000";

    /** API密钥，本地部署可留空 */
    private String apiKey;

    /** Embedding模型名称，推荐 BGE-large-zh 或 text2vec-large-chinese */
    private String model = "bge-large-zh";

    /** 向量输出维度，需与所选模型实际输出一致 */
    private int dimension = 1024;

    /** HTTP读取超时(秒) */
    private int timeoutSeconds = 60;
}
