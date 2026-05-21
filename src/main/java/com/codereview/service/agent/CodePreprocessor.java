package com.codereview.service.agent;

import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.regex.*;

/**
 * 代码预处理组件
 *
 * <p>在审查前对用户提交的原始代码进行标准化处理:
 * <ul>
 *   <li>统一换行符(\r\n → \n)</li>
 *   <li>压缩多余空行(3+个连续空行 → 2个)</li>
 *   <li>清理多余空格</li>
 *   <li>长度校验与拦截</li>
 *   <li>大代码智能分片(按行边界切分，不截断语句)</li>
 * </ul>
 * </p>
 *
 * <p>处理后的代码更适合正则匹配和LLM理解，减少干扰因素。</p>
 */
@Slf4j
@Component
public class CodePreprocessor {

    /** 单次审查最大字符数限制 */
    @Value("${review.max-code-length:50000}")
    private int maxCodeLength;

    /** 大代码分片大小(字符数) */
    @Value("${review.code-chunk-size:8000}")
    private int codeChunkSize;

    /** 连续3个及以上空行 */
    private static final Pattern MULTI_BLANK_LINE = Pattern.compile("(?m)\\n{3,}");

    /** 多个连续空格/Tab */
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");

    /**
     * 代码标准化预处理
     *
     * @param rawCode 用户提交的原始代码
     * @return 清洗后的代码
     * @throws IllegalArgumentException 代码为空或超长
     */
    public String preprocess(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("代码内容不能为空");
        }
        if (rawCode.length() > maxCodeLength) {
            throw new IllegalArgumentException("代码长度超过限制: " + maxCodeLength + "字符");
        }

        // 统一换行符: \r\n → \n, \r → \n
        String result = rawCode;
        result = result.replace("\r\n", "\n").replace("\r", "\n");
        // 压缩多余空行: 3+个 → 2个
        result = MULTI_BLANK_LINE.matcher(result).replaceAll("\n\n");
        // 压缩多余空格
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        result = result.strip();

        if (result.isBlank()) {
            throw new IllegalArgumentException("代码内容经预处理后为空");
        }
        log.debug("代码预处理完成: 原始长度={}, 处理后长度={}", rawCode.length(), result.length());
        return result;
    }

    /**
     * 大代码智能分片
     *
     * <p>当代码超过chunkSize时，按行边界切分为多块。
     * 保证每块大小接近但不超过chunkSize，同时不截断单行代码。</p>
     *
     * @param code 完整代码
     * @return 代码分片列表(小代码返回单元素列表)
     */
    public List<String> chunkCode(String code) {
        List<String> chunks = new ArrayList<>();
        if (code.length() <= codeChunkSize) {
            chunks.add(code);
            return chunks;
        }

        // 按行切分保证不截断语句
        String[] lines = code.split("\n");
        StringBuilder chunk = new StringBuilder();
        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > codeChunkSize && chunk.length() > 0) {
                chunks.add(chunk.toString());
                chunk = new StringBuilder();
            }
            chunk.append(line).append("\n");
        }
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
        }
        log.debug("代码分片完成: 总分片数={}", chunks.size());
        return chunks;
    }

    /**
     * 提取指定行号的代码片段
     *
     * <p>支持:
     * <ul>
     *   <li>单行: "15" → 返回第15行内容</li>
     *   <li>范围: "15-20" → 返回第15到20行内容</li>
     * </ul>
     * </p>
     *
     * @param code    完整代码
     * @param lineNum 行号或范围
     * @return 对应的代码片段
     */
    public String extractCodeSnippet(String code, String lineNum) {
        if (lineNum == null || lineNum.isBlank()) return code;
        String[] lines = code.split("\n");
        try {
            if (lineNum.contains("-")) {
                // 范围: "start-end"
                String[] parts = lineNum.split("-");
                int start = Math.max(0, Integer.parseInt(parts[0].trim()) - 1);
                int end = Math.min(lines.length, Integer.parseInt(parts[1].trim()));
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    sb.append(lines[i]).append("\n");
                }
                return sb.toString();
            } else {
                // 单行
                int line = Integer.parseInt(lineNum.trim()) - 1;
                if (line >= 0 && line < lines.length) {
                    return lines[line];
                }
            }
        } catch (NumberFormatException e) {
            log.warn("行号解析失败: {}", lineNum);
        }
        return code; // 解析失败返回全部代码
    }
}
