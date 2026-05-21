package com.codereview.exception;

import com.codereview.dto.vo.*;
import lombok.extern.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;

import java.util.stream.*;

/**
 * 全局异常处理器
 *
 * <p>统一拦截Controller层抛出的异常，转换为标准化的 {@link Result} 响应体。
 * 分层处理三类异常:
 * <ul>
 *   <li>{@link BusinessException}: 业务异常，返回对应错误码和消息，HTTP 200</li>
 *   <li>{@link MethodArgumentNotValidException}: 参数校验失败，返回400</li>
 *   <li>{@link Exception}: 未捕获的系统异常，返回500，避免堆栈信息泄露</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理
     *
     * <p>HTTP状态码仍为200，通过响应体中的code区分业务成功/失败。</p>
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常处理 (JSR-303 @Valid校验失败)
     *
     * <p>提取所有字段校验错误信息，拼接为分号分隔的字符串返回。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, "参数校验失败: " + msg);
    }

    /**
     * 兜底异常处理 - 防止堆栈信息泄露到前端
     *
     * <p>仅返回异常消息摘要，不暴露完整堆栈。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统内部错误: " + e.getMessage());
    }
}
