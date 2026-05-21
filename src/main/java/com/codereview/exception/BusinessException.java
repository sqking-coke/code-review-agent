package com.codereview.exception;

import lombok.*;

/**
 * 业务异常类
 *
 * <p>用于在服务层抛出可预期的业务异常，由 {@link GlobalExceptionHandler} 统一捕获
 * 并转换为标准 {@code Result} 响应。与系统异常(500)区分，业务异常通常返回200状态码
 * 但携带业务错误码。</p>
 *
 * <p>使用示例:
 * <pre>{@code
 * if (task == null) {
 *     throw new BusinessException(404, "审查任务不存在");
 * }
 * }</pre>
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，如 404-资源不存在, 400-参数错误 */
    private final int code;

    /** 错误描述信息 */
    private final String message;

    /**
     * 创建带错误码的业务异常
     *
     * @param code    业务错误码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 创建默认500错误码的业务异常
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        this(500, message);
    }
}
