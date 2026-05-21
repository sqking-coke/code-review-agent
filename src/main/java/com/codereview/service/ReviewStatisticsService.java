package com.codereview.service;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * 代码质量统计服务
 *
 * <p>提供团队/个人维度的代码质量多维度统计分析:
 * <ul>
 *   <li>总览: 任务总数、平均分、总问题数</li>
 *   <li>维度分布: 各问题类型(BUG/安全/性能等)数量分布</li>
 *   <li>时间趋势: 每日审查数量与评分变化</li>
 *   <li>个人排名: 按平均评分或审查数量排序</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class ReviewStatisticsService {

    private final CodeReviewTaskMapper taskMapper;

    public ReviewStatisticsService(CodeReviewTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 获取指定天数内的代码质量统计数据
     *
     * @param days 统计时间范围(天)，默认30天
     * @return 多维度统计数据VO
     */
    public ReviewStatisticsVO getStatistics(int days) {
        String startTime = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 查询时间范围内的所有已完成任务
        LambdaQueryWrapper<CodeReviewTask> wrapper = new LambdaQueryWrapper<CodeReviewTask>()
                .eq(CodeReviewTask::getTaskStatus, 1)
                .ge(CodeReviewTask::getCreateTime, startTime);

        long totalTasks = taskMapper.selectCount(wrapper);
        List<CodeReviewTask> tasks = taskMapper.selectList(wrapper);

        // 计算平均评分
        double avgScore = tasks.stream()
                .filter(t -> t.getCodeScore() != null)
                .mapToInt(CodeReviewTask::getCodeScore)
                .average()
                .orElse(0);

        // 计算总问题数
        long totalProblems = tasks.stream()
                .mapToInt(t -> t.getHighRiskCount() + t.getMidRiskCount() + t.getLowRiskCount())
                .sum();

        // 各维度统计查询
        List<Map<String, Object>> problemsByType = taskMapper.countProblemsByType(startTime);
        List<Map<String, Object>> dailyStats = taskMapper.getDailyReviewStats(startTime);
        List<Map<String, Object>> ranking = taskMapper.getReviewRanking(startTime, 10);

        return ReviewStatisticsVO.builder()
                .totalTasks(totalTasks)
                .averageScore(Math.round(avgScore * 100.0) / 100.0)
                .totalProblems(totalProblems)
                .problemsByType(problemsByType)
                .dailyStats(dailyStats)
                .reviewerRanking(ranking)
                .build();
    }
}
