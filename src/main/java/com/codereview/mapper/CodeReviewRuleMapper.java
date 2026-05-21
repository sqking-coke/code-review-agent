package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审查规则Mapper
 *
 * <p>提供规则配置的增删改查，规则每次审查时实时查询启用状态的规则，
 * 无需缓存，确保规则变更即时生效。</p>
 *
 * @author code-review-agent
 */
@Mapper
public interface CodeReviewRuleMapper extends BaseMapper<CodeReviewRule> {
}
