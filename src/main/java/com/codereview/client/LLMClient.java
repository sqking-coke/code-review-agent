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
 * 大模型(LLM) HTTP客户端
 *
 * <p>基于OkHttp原生HTTP调用，不依赖任何AI框架SDK。
 * 兼容 DeepSeek / 通义千问 / OpenAI / 智谱 等兼容 OpenAI Chat Completions API 的大模型。</p>
 *
 * <p>核心特性:
 * <ul>
 *   <li>自动重试: 支持可配置的重试次数和退避策略</li>
 *   <li>JSON清洗: LLM返回的内容自动去除markdown代码块标记</li>
 *   <li>超时控制: 连接/读取/写入独立超时配置</li>
 *   <li>请求日志: 关键节点debug日志便于排障</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class LLMClient {

    private final LLMConfig llmConfig;
    private final OkHttpClient httpClient;

    public LLMClient(LLMConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(llmConfig.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 单轮对话调用LLM
     *
     * @param systemPrompt 系统提示词(定义角色身份和输出规范)
     * @param userMessage  用户消息(提交审查的代码和问题)
     * @return LLM返回的文本内容
     */
    public String chat(String systemPrompt, String userMessage) {
        return chatWithHistory(systemPrompt, List.of(Map.of("role", "user", "content", userMessage)));
    }

    /**
     * 多轮对话调用LLM
     *
     * @param systemPrompt 系统提示词
     * @param messages     对话历史 [{role: "user"/"assistant", content: "..."}]
     * @return LLM返回的文本内容
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> messages) {
        JSONArray msgArray = new JSONArray();
        // 系统消息放在最前面
        msgArray.add(createMessage("system", systemPrompt));
        for (Map<String, String> msg : messages) {
            msgArray.add(createMessage(msg.get("role"), msg.get("content")));
        }

        JSONObject body = new JSONObject();
        body.put("model", llmConfig.getModel());
        body.put("messages", msgArray);
        body.put("temperature", llmConfig.getTemperature());
        body.put("max_tokens", llmConfig.getMaxTokens());

        // 带退避的重试逻辑
        for (int attempt = 0; attempt <= llmConfig.getMaxRetry(); attempt++) {
            try {
                return doChat(body);
            } catch (Exception e) {
                if (attempt == llmConfig.getMaxRetry()) {
                    log.error("LLM调用失败, 已重试{}次", attempt, e);
                    throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
                }
                log.warn("LLM调用失败, 第{}次重试: {}", attempt + 1, e.getMessage());
                sleep(2000L * (attempt + 1)); // 指数退避: 2s, 4s, 6s...
            }
        }
        throw new RuntimeException("LLM调用失败");
    }

    /**
     * 执行单次HTTP调用
     */
    private String doChat(JSONObject body) throws IOException {
        Request request = new Request.Builder()
                .url(llmConfig.getApiUrl())
                .header("Authorization", "Bearer " + llmConfig.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new IOException("HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JSONObject respJson = JSON.parseObject(respBody);
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("LLM返回空choices: " + respBody);
            }
            // 提取 message.content
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            return message.getString("content");
        }
    }

    /**
     * 单轮对话并要求LLM返回JSON格式
     *
     * <p>自动在system prompt末尾追加JSON输出约束，
     * 并对返回内容做markdown代码块清洗后再解析。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 解析后的JSONObject
     */
    public JSONObject chatForJson(String systemPrompt, String userMessage) {
        String fullSystemPrompt = systemPrompt +
                "\n\n请严格输出JSON格式，不要包含markdown代码块标记(```json)，直接输出纯JSON。";
        String content = chat(fullSystemPrompt, userMessage);
        String cleaned = cleanJsonResponse(content);
        return JSON.parseObject(cleaned);
    }

    /**
     * 清洗LLM返回内容中的markdown代码块标记
     *
     * <p>处理常见格式:
     * <ul>
     *   <li>```json {...} ``` → 去除首尾标记</li>
     *   <li>``` {...} ``` → 去除首尾标记</li>
     * </ul>
     * </p>
     */
    private String cleanJsonResponse(String content) {
        if (content == null) return "{}";
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        }
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /** 构建标准OpenAI格式的消息对象 */
    private JSONObject createMessage(String role, String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    /** 线程安全休眠 */
    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
