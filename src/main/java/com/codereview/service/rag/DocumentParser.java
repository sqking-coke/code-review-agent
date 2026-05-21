package com.codereview.service.rag;

import lombok.extern.slf4j.*;
import org.apache.pdfbox.*;
import org.apache.pdfbox.text.*;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.parser.*;
import org.commonmark.renderer.text.*;
import org.springframework.stereotype.*;
import org.springframework.web.multipart.*;

import java.io.*;
import java.nio.charset.*;
import java.util.stream.*;

/**
 * 知识文档解析组件
 *
 * <p>负责将不同格式的上传文档统一解析为纯文本内容，
 * 去除格式标记、提取正文，为后续分块和向量化做准备。</p>
 *
 * <p>支持格式:
 * <ul>
 *   <li>Markdown(.md/.markdown): 通过CommonMark解析为纯文本</li>
 *   <li>纯文本(.txt): 直接读取UTF-8内容</li>
 *   <li>PDF(.pdf): 通过PDFBox提取文本(按位置排序)</li>
 *   <li>Word(.docx): 通过POI提取段落文本</li>
 *   <li>代码文件(.java/.py/.go/.js/.ts/.sql): 按纯文本处理</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * 根据文件扩展名路由到对应的解析器
     *
     * @param file 上传的MultipartFile
     * @return 解析后的纯文本内容
     * @throws Exception 解析异常或不支持的文件格式
     */
    public String parse(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return parseMarkdown(file);
        } else if (lowerName.endsWith(".txt")) {
            return parsePlainText(file);
        } else if (lowerName.endsWith(".pdf")) {
            return parsePdf(file);
        } else if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
            return parseDocx(file);
        } else if (lowerName.endsWith(".java") || lowerName.endsWith(".py")
                || lowerName.endsWith(".go") || lowerName.endsWith(".js")
                || lowerName.endsWith(".ts") || lowerName.endsWith(".sql")) {
            return parsePlainText(file);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + lowerName);
        }
    }

    /** Markdown解析: 通过CommonMark转为纯文本(去除格式标记) */
    private String parseMarkdown(MultipartFile file) throws Exception {
        String raw = new String(file.getBytes(), StandardCharsets.UTF_8);
        Parser parser = Parser.builder().build();
        TextContentRenderer renderer = TextContentRenderer.builder().build();
        org.commonmark.node.Node doc = parser.parse(raw);
        return renderer.render(doc);
    }

    /** 纯文本/代码文件直接读取 */
    private String parsePlainText(MultipartFile file) throws Exception {
        return new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
    }

    /** PDF解析: 按文本位置排序提取 */
    private String parsePdf(MultipartFile file) throws Exception {
        try (var pdfDoc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(pdfDoc);
        }
    }

    /** Word(.docx)解析: 提取所有段落文本 */
    private String parseDocx(MultipartFile file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining("\n"));
        }
    }
}
