package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG知识文档Mapper
 *
 * <p>管理知识文档的增删改查，配合 {@link RagKnowledgeChunkMapper}
 * 实现文档+分块的级联管理。</p>
 *
 * @author code-review-agent
 */
@Mapper
public interface RagKnowledgeDocMapper extends BaseMapper<RagKnowledgeDoc> {
}
