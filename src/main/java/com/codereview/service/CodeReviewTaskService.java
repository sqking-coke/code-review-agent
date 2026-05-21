package com.codereview.service;

import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.codereview.dto.request.*;
import com.codereview.dto.vo.*;
import com.codereview.entity.*;
import com.codereview.exception.*;
import com.codereview.mapper.*;
import com.codereview.service.agent.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.stream.*;

/**
 * 代码审查业务服务
 *
 * <p>审查业务的核心服务层，负责:
 * <ul>
 *   <li>接收前端提交的审查请求</li>
 *   <li>创建审查任务并触发异步审查链路</li>
 *   <li>提供任务列表、详情、报告查询</li>
 *   <li>审查结果与报告的组装返回</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class CodeReviewTaskService {

    private final CodeReviewTaskMapper taskMapper;
    private final CodeReviewDetailMapper detailMapper;
    private final AgentOrchestrator orchestrator;

    public CodeReviewTaskService(CodeReviewTaskMapper taskMapper, CodeReviewDetailMapper detailMapper,
                                  AgentOrchestrator orchestrator) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.orchestrator = orchestrator;
    }

    /**
     * 提交代码审查
     *
     * <p>创建任务记录后立即返回任务信息(状态为处理中)，
     * 实际审查异步执行，前端通过任务编号轮询获取结果。</p>
     *
     * @param request 审查请求(代码内容、文件名、语言类型、提交人)
     * @return 创建的任务信息(状态: 0-处理中)
     */
    public ReviewTaskVO submitReview(ReviewSubmitRequest request) {
        CodeReviewTask task = orchestrator.createTask(
                request.getCodeContent(), request.getCodeName(),
                request.getCodeType(), request.getSubmitBy());
        // 异步触发审查链路(不阻塞当前请求)
        orchestrator.executeReview(task);
        return toVO(task);
    }

    /** 根据ID查询任务 */
    public ReviewTaskVO getTaskById(Long taskId) {
        CodeReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "审查任务不存在");
        }
        return toVO(task);
    }

    /** 根据任务编号查询任务 */
    public ReviewTaskVO getTaskByNo(String taskNo) {
        CodeReviewTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<CodeReviewTask>().eq(CodeReviewTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BusinessException(404, "审查任务不存在");
        }
        return toVO(task);
    }

    /**
     * 分页查询审查任务列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param status   任务状态筛选(可选)
     * @param submitBy 提交人筛选(可选)
     * @return 分页结果
     */
    public Page<ReviewTaskVO> listTasks(int pageNum, int pageSize, Integer status, String submitBy) {
        LambdaQueryWrapper<CodeReviewTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CodeReviewTask::getTaskStatus, status);
        }
        if (submitBy != null && !submitBy.isBlank()) {
            wrapper.eq(CodeReviewTask::getSubmitBy, submitBy);
        }
        wrapper.orderByDesc(CodeReviewTask::getCreateTime);

        Page<CodeReviewTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ReviewTaskVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /** 查询任务的所有问题明细(按排序序号升序) */
    public List<CodeReviewDetail> getTaskDetails(Long taskId) {
        return detailMapper.selectList(
                new LambdaQueryWrapper<CodeReviewDetail>()
                        .eq(CodeReviewDetail::getTaskId, taskId)
                        .orderByAsc(CodeReviewDetail::getSortOrder)
        );
    }

    /**
     * 获取审查报告
     *
     * <p>优先从reportJson字段反序列化(快速)，
     * 若为空则从任务+明细数据重新组装报告。</p>
     *
     * @param taskId 任务ID
     * @return 审查报告VO
     */
    public ReviewReportVO getReport(Long taskId) {
        CodeReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "审查任务不存在");
        }
        if (task.getTaskStatus() != 1) {
            throw new BusinessException("审查任务尚未完成，请稍后再试");
        }
        // 优先从JSON反序列化
        if (task.getReportJson() != null && !task.getReportJson().isBlank()) {
            JSONObject reportJson = JSONObject.parseObject(task.getReportJson());
            return reportJson.toJavaObject(ReviewReportVO.class);
        }
        // 兜底: 从DB数据组装报告
        return buildReportFromDb(taskId, task);
    }

    /** 从数据库数据组装报告(兜底方案) */
    private ReviewReportVO buildReportFromDb(Long taskId, CodeReviewTask task) {
        List<CodeReviewDetail> details = getTaskDetails(taskId);
        int high = 0, mid = 0, low = 0;
        for (CodeReviewDetail d : details) {
            if ("HIGH".equals(d.getRiskLevel())) high++;
            else if ("MEDIUM".equals(d.getRiskLevel())) mid++;
            else low++;
        }

        List<ReviewReportVO.ProblemItem> items = details.stream().map(d ->
                ReviewReportVO.ProblemItem.builder()
                        .detailId(d.getId())
                        .riskLevel(d.getRiskLevel())
                        .problemType(d.getProblemType())
                        .lineNum(d.getLineNum())
                        .problemCode(d.getProblemCode())
                        .problemDesc(d.getProblemDesc())
                        .riskEffect(d.getRiskEffect())
                        .optimizeSuggest(d.getOptimizeSuggest())
                        .fixCode(d.getFixCode())
                        .ragRefTitle(d.getRagRefTitle())
                        .build()
        ).toList();

        return ReviewReportVO.builder()
                .taskId(taskId)
                .taskNo(task.getTaskNo())
                .codeName(task.getCodeName())
                .codeType(task.getCodeType())
                .codeScore(task.getCodeScore())
                .reviewSummary(task.getReviewSummary())
                .riskSummary(ReviewReportVO.RiskSummary.builder()
                        .high(high).medium(mid).low(low).total(high + mid + low).build())
                .problems(items)
                .ragReferences(List.of())
                .optimizeSummary("详见各问题优化方案")
                .build();
    }

    /** Entity → VO转换(不暴露原始代码内容) */
    private ReviewTaskVO toVO(CodeReviewTask task) {
        return ReviewTaskVO.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .codeName(task.getCodeName())
                .codeType(task.getCodeType())
                .codeScore(task.getCodeScore())
                .highRiskCount(task.getHighRiskCount())
                .midRiskCount(task.getMidRiskCount())
                .lowRiskCount(task.getLowRiskCount())
                .ragHitCount(task.getRagHitCount())
                .reviewSummary(task.getReviewSummary())
                .taskStatus(task.getTaskStatus())
                .errorMsg(task.getErrorMsg())
                .submitBy(task.getSubmitBy())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }
}
