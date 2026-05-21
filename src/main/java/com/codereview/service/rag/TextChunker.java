package com.codereview.service.rag;

import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * 智能文本分块组件
 *
 * <p>将解析后的长文本切分为适合向量化和检索的文本块(chunk)。
 * 采用基于段落边界的语义分块策略，非固定字数截断，保护语义完整性。</p>
 *
 * <p>分块参数:
 * <ul>
 *   <li>chunk-size: 目标分块大小(tokens)，默认500</li>
 *   <li>chunk-overlap: 相邻块重叠大小(tokens)，默认80</li>
 * </ul>
 * 重叠设计确保跨chunk边界的关键信息不会丢失，
 * 如一个类的定义可能在chunk A末尾、其方法在chunk B开头。</p>
 */
@Slf4j
@Component
public class TextChunker {

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:80}")
    private int chunkOverlap;

    /**
     * 按段落边界切分文本
     *
     * <p>以 \n\n(双换行)为段落边界，逐段累积，
     * 当累积token数超过chunkSize时输出一个chunk并重置。
     * 新chunk开头保留上一chunk末尾的overlap内容。</p>
     *
     * @param text 待切分的纯文本
     * @return 文本块列表(空文本返回空列表)
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");

        StringBuilder current = new StringBuilder();
        int currentTokenEstimate = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraTokens = estimateTokens(trimmed);

            // 当前chunk已满，输出并开始新chunk
            if (current.length() > 0 && currentTokenEstimate + paraTokens > chunkSize) {
                chunks.add(current.toString().trim());

                // 保留overlap: 截取当前chunk末尾部分作为新chunk的开头
                if (chunkOverlap > 0 && current.length() > chunkOverlap) {
                    String overlap = current.substring(Math.max(0, current.length() - chunkOverlap));
                    current = new StringBuilder(overlap);
                    currentTokenEstimate = estimateTokens(overlap);
                } else {
                    current = new StringBuilder();
                    currentTokenEstimate = 0;
                }
            }

            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(trimmed);
            currentTokenEstimate += paraTokens;
        }

        // 最后一个chunk
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        log.info("文本分块完成: 原文长度={}, 分块数={}, chunkSize={}, overlap={}",
                text.length(), chunks.size(), chunkSize, chunkOverlap);
        return chunks;
    }

    /**
     * 估算文本的Token数量
     *
     * <p>估算规则(粗略):
     * <ul>
     *   <li>中文字符: 1字 ≈ 1 token</li>
     *   <li>英文/数字/符号: 4字符 ≈ 1 token</li>
     * </ul>
     * 此估算用于分块决策，无需精确值。</p>
     */
    private int estimateTokens(String text) {
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return chineseChars + (otherChars / 4) + 1;
    }
}
