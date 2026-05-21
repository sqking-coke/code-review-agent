package com.codereview.util;

import com.alibaba.fastjson2.*;
import lombok.extern.slf4j.*;

import java.util.*;

/**
 * JSON解析与向量序列化工具类
 *
 * <p>基于FastJSON2提供:
 * <ul>
 *   <li>LLM返回的JSON结果安全解析(含异常兜底)</li>
 *   <li>向量数据与JSON字符串的双向转换</li>
 *   <li>LLM响应内容清洗(去除markdown代码块标记)</li>
 * </ul>
 * </p>
 */
@Slf4j
public final class JsonParser {

    private JsonParser() {}

    /**
     * 解析LLM返回的审查问题列表
     *
     * <p>安全解析: LLM返回格式异常时返回空列表而非抛异常，
     * 保证审查流程不因解析问题中断。</p>
     *
     * @param llmJsonResponse LLM原始返回内容
     * @return 问题列表JSONArray(可能为空列表)
     */
    public static List<JSONObject> parseProblems(String llmJsonResponse) {
        try {
            JSONObject json = JSON.parseObject(llmJsonResponse);
            JSONArray problems = json.getJSONArray("problems");
            if (problems == null || problems.isEmpty()) {
                return Collections.emptyList();
            }
            List<JSONObject> result = new ArrayList<>();
            for (int i = 0; i < problems.size(); i++) {
                result.add(problems.getJSONObject(i));
            }
            return result;
        } catch (Exception e) {
            log.error("解析LLM审查结果失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析LLM返回的整体评分
     *
     * @param llmJsonResponse LLM原始返回内容
     * @return 评分值(0-100)，解析失败返回60
     */
    public static int parseScore(String llmJsonResponse) {
        try {
            JSONObject json = JSON.parseObject(llmJsonResponse);
            Integer score = json.getInteger("overallScore");
            return score != null ? score : 60;
        } catch (Exception e) {
            log.error("解析评分失败", e);
            return 60;
        }
    }

    /**
     * 解析LLM返回的审查总结
     *
     * @param llmJsonResponse LLM原始返回内容
     * @return 总结文本，解析失败返回默认提示
     */
    public static String parseSummary(String llmJsonResponse) {
        try {
            JSONObject json = JSON.parseObject(llmJsonResponse);
            return json.getString("summary");
        } catch (Exception e) {
            log.error("解析总结失败", e);
            return "审查完成，解析总结失败";
        }
    }

    /**
     * JSON数组字符串 → 向量List
     *
     * @param embeddingJson JSON数组格式的向量 "[0.123, -0.456, ...]"
     * @return 浮点数向量列表
     */
    public static List<Float> parseEmbedding(String embeddingJson) {
        return JSON.parseArray(embeddingJson, Float.class);
    }

    /**
     * 向量List → JSON数组字符串
     *
     * @param vector 浮点数向量列表
     * @return JSON数组字符串(用于MySQL LONGTEXT存储)
     */
    public static String toEmbeddingJson(List<Float> vector) {
        return JSON.toJSONString(vector);
    }

    /** 对象转JSON字符串 */
    public static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    /** JSON字符串转指定类型对象 */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    /**
     * 清洗LLM返回内容中的markdown代码块标记
     *
     * <p>LLM时常在JSON外加 ```json ... ``` 标记，需要去除后才能解析。
     * 处理逻辑:
     * <ol>
     *   <li>去除开头的 ```json 或 ```</li>
     *   <li>去除结尾的 ```</li>
     *   <li>trim空白字符</li>
     * </ol>
     * </p>
     *
     * @param content LLM原始返回内容
     * @return 清洗后的纯JSON字符串
     */
    public static String cleanResponse(String content) {
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
}
