package com.codereview.config;

import lombok.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.*;

import java.util.concurrent.*;

/**
 * 异步线程池配置
 *
 * <p>为代码审查任务提供专用线程池，实现异步处理、线程隔离。
 * 大代码片段审查不阻塞主请求线程，前端可轮询获取审查进度。</p>
 *
 * <p>线程池参数说明:
 * <ul>
 *   <li>corePoolSize: 核心线程数，常驻线程</li>
 *   <li>maxPoolSize: 最大线程数，高峰期可扩展上限</li>
 *   <li>queueCapacity: 阻塞队列容量，超出后触发拒绝策略</li>
 *   <li>拒绝策略: CallerRunsPolicy - 由调用线程执行，防止任务丢失</li>
 * </ul>
 * </p>
 */
@Data
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "async")
public class ThreadPoolConfig {

    /** 核心线程数 */
    private int corePoolSize = 4;

    /** 最大线程数 */
    private int maxPoolSize = 8;

    /** 阻塞队列容量 */
    private int queueCapacity = 200;

    /** 空闲线程保留时间(秒) */
    private int keepAliveSeconds = 60;

    /** 单任务超时(分钟) */
    private int taskTimeoutMinutes = 10;

    /**
     * 审查专用线程池
     *
     * <p>线程名前缀 code-review- 便于日志追踪和问题排查。
     * 优雅关闭时等待进行中任务完成，最长等待60秒。</p>
     *
     * @return 配置好的线程池执行器
     */
    @Bean("reviewExecutor")
    public Executor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("code-review-");
        // 调用者运行策略: 队列满时由主线程执行，防止任务被丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭: 等待进行中任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
