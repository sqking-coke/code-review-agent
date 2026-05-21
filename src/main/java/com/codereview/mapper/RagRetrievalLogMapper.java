package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG检索日志Mapper
 *
 * <p>记录每次检索行为的完整信息，用于监控检索效果和调优参数。</p>
 *
 * @author code-review-agent
 */
@Mapper
public interface RagRetrievalLogMapper extends BaseMapper<RagRetrievalLog> {
}
