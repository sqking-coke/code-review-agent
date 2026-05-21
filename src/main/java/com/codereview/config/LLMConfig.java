package com.codereview.config;

import lombok.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;

/**
 * 大模型(LLM)配置类
 *
 * <p>通过 {@code application.yml} 中 {@code llm.*} 前缀注入配置值，
 * 兼容 DeepSeek / 通义千问 / OpenAI / 智谱 等主流大模型，
 * 通过修改 provider 和 apiUrl 即可切换。</p>
 *
 * <p>配置示例:
 * <pre>{@code
 * llm:
 *   provider: deepseek
 *   api-key: sk-xxx
 *   apiUrl: https://api.deepseek.com/v1/chat/completions
 *   model: deepseek-v4-flash
 *   temperature: 0.1
 *   max-tokens: 4096
 * }</pre>
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    /** LLM服务商标识: deepseek / qwen / openai / zhipu */
    private String provider = "deepseek";

    /** OpenAI 兼容格式的 API 地址 */
    private String apiUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 生成温度 0-2，代码审查推荐低温度以获得确定性输出 */
    private double temperature = 0.1;

    /** 最大生成token数 */
    private int maxTokens = 4096;

    /** HTTP读取超时(秒)，大代码审查可适当调大 */
    private int timeoutSeconds = 120;

    /** 调用失败重试次数 */
    private int maxRetry = 2;

}
