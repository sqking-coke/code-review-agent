package com.codereview.dto.vo;

import lombok.*;

/**
 * 统一API响应包装类
 *
 * <p>所有Controller层返回结果均使用此类包装，确保前端收到的响应格式统一。
 * 响应格式: {@code {"code": 200, "message": "success", "data": {...}} }</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 状态码: 200-成功, 4xx-客户端错误, 5xx-服务端错误 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据体 */
    private T data;

    /** 成功返回(带数据) */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功返回(无数据) */
    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    /** 失败返回(指定错误码) */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败返回(默认500错误码) */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
