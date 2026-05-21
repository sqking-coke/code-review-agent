package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审查问题明细Mapper
 *
 * <p>继承MyBatis-Plus BaseMapper，提供基础CRUD。
 * 复杂查询通过LambdaQueryWrapper动态构建。</p>
 *
 * @author code-review-agent
 */
@Mapper
public interface CodeReviewDetailMapper extends BaseMapper<CodeReviewDetail> {
}
