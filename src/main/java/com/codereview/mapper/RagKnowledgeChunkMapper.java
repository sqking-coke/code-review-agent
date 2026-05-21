package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

/**
 * RAG知识块Mapper
 *
 * <p>知识块(chunk)是向量检索的最小单元。提供:
 * <ul>
 *   <li>基础CRUD(继承BaseMapper)</li>
 *   <li>命中计数更新(每次检索命中+1)</li>
 *   <li>按文档ID查询所有分块</li>
 *   <li>获取已人工验证的高质量chunk</li>
 * </ul>
 * </p>
 *
 * @author code-review-agent
 */
@Mapper
public interface RagKnowledgeChunkMapper extends BaseMapper<RagKnowledgeChunk> {

    /**
     * 检索命中计数+1，同时更新最后命中时间
     *
     * @param id chunk ID
     */
    @Update("UPDATE rag_knowledge_chunk SET hit_count = hit_count + 1, " +
            "last_hit_time = NOW() WHERE id = #{id}")
    void incrementHitCount(@Param("id") Long id);

    /**
     * 查询指定文档的所有分块(按序号排序)
     *
     * @param docId 文档ID
     * @return 该文档所有chunk列表
     */
    @Select("SELECT * FROM rag_knowledge_chunk WHERE doc_id = #{docId} ORDER BY chunk_index")
    List<RagKnowledgeChunk> selectByDocId(@Param("docId") Long docId);

    /**
     * 查询所有已人工验证的高质量chunk(按质量评分降序)
     *
     * @return 已验证chunk列表
     */
    @Select("SELECT * FROM rag_knowledge_chunk WHERE is_verified = 1 ORDER BY quality_score DESC")
    List<RagKnowledgeChunk> selectVerifiedChunks();
}
