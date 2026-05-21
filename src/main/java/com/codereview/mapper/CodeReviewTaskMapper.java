package com.codereview.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.codereview.entity.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

/**
 * 审查任务Mapper
 *
 * <p>继承MyBatis-Plus BaseMapper获得基础CRUD能力，
 * 扩展自定义统计查询方法用于代码质量数据统计分析。</p>
 *
 * @author code-review-agent
 */
@Mapper
public interface CodeReviewTaskMapper extends BaseMapper<CodeReviewTask> {

    /**
     * 按问题类型统计数量(用于饼图/柱状图展示)
     *
     * @param startTime 统计起始时间
     * @return 问题类型及对应数量列表 [{problemType: "BUG", cnt: 15}, ...]
     */
    @Select("SELECT problem_type, COUNT(*) as cnt FROM code_review_detail d " +
            "INNER JOIN code_review_task t ON d.task_id = t.id " +
            "WHERE t.task_status = 1 AND t.create_time >= #{startTime} " +
            "GROUP BY problem_type ORDER BY cnt DESC")
    List<Map<String, Object>> countProblemsByType(String startTime);

    /**
     * 每日审查统计(用于趋势折线图)
     *
     * @param startTime 统计起始时间
     * @return 每日审查数据 [{reviewDate, taskCount, avgScore}, ...]
     */
    @Select("SELECT DATE(create_time) as review_date, COUNT(*) as task_count, " +
            "AVG(code_score) as avg_score FROM code_review_task " +
            "WHERE task_status = 1 AND create_time >= #{startTime} " +
            "GROUP BY DATE(create_time) ORDER BY review_date")
    List<Map<String, Object>> getDailyReviewStats(String startTime);

    /**
     * 个人代码质量排名(按平均分降序)
     *
     * @param startTime 统计起始时间
     * @param limit     返回前N名
     * @return 个人排名数据 [{submitBy, taskCount, avgScore}, ...]
     */
    @Select("SELECT submit_by, COUNT(*) as task_count, AVG(code_score) as avg_score " +
            "FROM code_review_task WHERE task_status = 1 AND create_time >= #{startTime} " +
            "GROUP BY submit_by ORDER BY task_count DESC LIMIT #{limit}")
    List<Map<String, Object>> getReviewRanking(String startTime, int limit);
}
