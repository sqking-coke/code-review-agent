package com.codereview.dto.vo;

import lombok.*;

import java.util.*;

/**
 * 代码质量统计响应VO
 *
 * <p>聚合团队代码质量多维统计数据:
 * <ul>
 *   <li>总览: 任务总数、平均分、总问题数</li>
 *   <li>维度: 各问题类型分布统计</li>
 *   <li>趋势: 每日审查评分变化</li>
 *   <li>排名: 个人代码质量排行</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatisticsVO {

    /** 总审查任务数 */
    private long totalTasks;

    /** 平均代码评分 */
    private double averageScore;

    /** 发现的问题总数 */
    private long totalProblems;

    /** 问题类型分布: [{problemType: "BUG", cnt: 15}, ...] */
    private List<Map<String, Object>> problemsByType;

    /** 每日审查趋势: [{reviewDate: "2026-05-01", taskCount: 8, avgScore: 82.5}, ...] */
    private List<Map<String, Object>> dailyStats;

    /** 个人质量排名(降序): [{submitBy: "张三", taskCount: 10, avgScore: 88.0}, ...] */
    private List<Map<String, Object>> reviewerRanking;
}
