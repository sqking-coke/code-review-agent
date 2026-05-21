package com.codereview.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * RAG知识检索测试请求DTO
 *
 * <p>用于知识库检索效果测试: 输入查询文本，返回Top-K匹配片段及相似度。
 * 支持三种检索方式切换，方便对比调优检索参数。</p>
 */
@Data
public class RagSearchRequest {

    /** 检索查询文本(粘贴代码片段或自然语言描述) */
    @NotBlank(message = "查询文本不能为空")
    private String queryText;

    /** 检索方式: VECTOR(仅向量) / KEYWORD(仅关键词) / HYBRID(混合，推荐) */
    private String retrievalMethod = "HYBRID";

    /** 期望返回的Top-K结果数量 */
    private int topK = 5;
}
