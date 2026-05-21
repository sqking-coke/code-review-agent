package com.codereview.controller;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.codereview.dto.request.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.mapper.*;
import com.codereview.service.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

/**
 * 代码审查Controller
 *
 * <p>提供代码审查相关的全部REST API接口，包括:
 * <ul>
 *   <li>审查任务: 提交、查询、详情、报告</li>
 *   <li>审查规则: 增删改查、启用/禁用</li>
 *   <li>质量统计: 多维度代码质量数据分析</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/agent/code/review")
@RequiredArgsConstructor
public class CodeReviewController {

    private final CodeReviewTaskService taskService;
    private final CodeReviewRuleMapper ruleMapper;
    private final ReviewStatisticsService statisticsService;

    /**
     * 提交代码智能审查
     *
     * <p>接收代码内容，创建审查任务，异步执行审查链路。
     * 立即返回任务信息(状态: 0-处理中)，前端通过任务编号轮询结果。</p>
     *
     * @param request 审查请求(代码内容、文件名、语言类型)
     * @return 创建的任务信息
     */
    @PostMapping("/submit")
    public Result<ReviewTaskVO> submitReview(@Valid @RequestBody ReviewSubmitRequest request) {
        ReviewTaskVO task = taskService.submitReview(request);
        return Result.ok(task);
    }

    /**
     * 分页查询审查任务列表
     *
     * @param pageNum  页码(默认1)
     * @param pageSize 每页数量(默认20)
     * @param status   任务状态筛选(可选): 0-处理中, 1-已完成, 2-失败
     * @param submitBy 提交人筛选(可选)
     */
    @GetMapping("/task/list")
    public Result<Page<ReviewTaskVO>> listTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String submitBy) {
        Page<ReviewTaskVO> page = taskService.listTasks(pageNum, pageSize, status, submitBy);
        return Result.ok(page);
    }

    /**
     * 查询审查任务详情
     *
     * @param taskId 任务ID
     * @return 任务元信息(不含原始代码)
     */
    @GetMapping("/task/{taskId}")
    public Result<ReviewTaskVO> getTask(@PathVariable Long taskId) {
        ReviewTaskVO task = taskService.getTaskById(taskId);
        return Result.ok(task);
    }

    /**
     * 查询审查问题明细列表
     *
     * @param taskId 任务ID
     * @return 该任务发现的所有问题明细(按排序序号升序)
     */
    @GetMapping("/detail/{taskId}")
    public Result<List<CodeReviewDetail>> getDetails(@PathVariable Long taskId) {
        List<CodeReviewDetail> details = taskService.getTaskDetails(taskId);
        return Result.ok(details);
    }

    /**
     * 获取审查报告
     *
     * <p>返回完整的结构化审查报告，包含评分、风险统计、
     * 问题清单(含修复代码)、RAG规范引用等。</p>
     *
     * @param taskId 任务ID
     * @return 审查报告(需任务已完成)
     */
    @GetMapping("/report/{taskId}")
    public Result<ReviewReportVO> getReport(@PathVariable Long taskId) {
        ReviewReportVO report = taskService.getReport(taskId);
        return Result.ok(report);
    }

    /**
     * 新增或更新审查规则
     *
     * <p>id为null时新增，不为null时更新已有规则。规则修改即时生效。</p>
     *
     * @param request 规则信息
     * @return 保存后的规则实体
     */
    @PostMapping("/rule/save")
    public Result<CodeReviewRule> saveRule(@Valid @RequestBody RuleSaveRequest request) {
        CodeReviewRule rule = CodeReviewRule.builder()
                .ruleName(request.getRuleName())
                .ruleType(request.getRuleType())
                .ruleCategory(request.getRuleCategory() != null ? request.getRuleCategory() : "BASIC")
                .ruleContent(request.getRuleContent())
                .checkPattern(request.getCheckPattern())
                .severity(request.getSeverity() != null ? request.getSeverity() : "MEDIUM")
                .language(request.getLanguage() != null ? request.getLanguage() : "Java")
                .exampleCode(request.getExampleCode())
                .fixExample(request.getFixExample())
                .sortOrder(request.getSortOrder())
                .status(request.getStatus())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        if (request.getId() != null) {
            // 更新: 保留原创建时间
            rule.setId(request.getId());
            rule.setCreateTime(null);
            ruleMapper.updateById(rule);
        } else {
            ruleMapper.insert(rule);
        }
        return Result.ok(rule);
    }

    /**
     * 查询审查规则列表
     *
     * @param ruleType 规则类型筛选(可选): STYLE/BUG/PERFORMANCE/SECURITY/DESIGN
     * @param status   启用状态筛选(可选): 0-禁用, 1-启用
     */
    @GetMapping("/rule/list")
    public Result<List<CodeReviewRule>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<CodeReviewRule> wrapper = new LambdaQueryWrapper<>();
        if (ruleType != null) {
            wrapper.eq(CodeReviewRule::getRuleType, ruleType);
        }
        if (status != null) {
            wrapper.eq(CodeReviewRule::getStatus, status);
        }
        wrapper.orderByAsc(CodeReviewRule::getSortOrder);
        List<CodeReviewRule> rules = ruleMapper.selectList(wrapper);
        return Result.ok(rules);
    }

    /**
     * 禁用审查规则(软删除)
     *
     * <p>将规则的status置为0(禁用)，而非物理删除，保留历史引用。</p>
     *
     * @param ruleId 规则ID
     */
    @DeleteMapping("/rule/{ruleId}")
    public Result<Void> deleteRule(@PathVariable Long ruleId) {
        CodeReviewRule rule = ruleMapper.selectById(ruleId);
        if (rule != null) {
            rule.setStatus(0);
            ruleMapper.updateById(rule);
        }
        return Result.ok();
    }

    /**
     * 代码质量数据统计
     *
     * <p>返回指定时间范围内的多维度质量数据:
     * 任务总数、平均分、问题分布、每日趋势、个人排名等。</p>
     *
     * @param days 统计天数(默认30天)
     * @return 统计数据VO
     */
    @GetMapping("/stat")
    public Result<ReviewStatisticsVO> getStatistics(@RequestParam(defaultValue = "30") int days) {
        ReviewStatisticsVO stats = statisticsService.getStatistics(days);
        return Result.ok(stats);
    }
}
