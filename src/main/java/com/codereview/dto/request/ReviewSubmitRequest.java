package com.codereview.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 代码审查提交请求DTO
 *
 * <p>前端提交代码审查时使用，支持单段代码文本提交。
 * JSR-303参数校验确保代码内容不为空且不超过长度限制。</p>
 */
@Data
public class ReviewSubmitRequest {

    /** 提交审查的代码内容，不能为空，最大50000字符 */
    @NotBlank(message = "代码内容不能为空")
    @Size(max = 50000, message = "代码长度不能超过50000字符")
    private String codeContent;

    /** 代码文件名或模块名称(可选) */
    private String codeName;

    /** 代码语言类型，默认Java，支持Python/Go/JavaScript等 */
    private String codeType = "Java";

    /** 提交人标识(可选，用于统计个人代码质量) */
    private String submitBy;
}
