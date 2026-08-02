package com.enterprise.knowledge.ai.parser;

import com.enterprise.knowledge.domain.Document.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Document parser that extracts text content from various file formats.
 * Supports PDF, DOCX, TXT, Markdown, and HTML files.
 * Returns structured text with page/section information when available.
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * Parse document and extract text content.
     * @param inputStream The file input stream
     * @param fileType The document file type
     * @return Parsed document result with text and metadata
     */
    public ParsedDocument parse(InputStream inputStream, FileType fileType) throws IOException {
        return switch (fileType) {
            case PDF -> parsePdf(inputStream);
            case DOCX -> parseDocx(inputStream);
            case TXT, MD -> parseText(inputStream);
            case HTML -> parseHtml(inputStream);
        };
    }

    private ParsedDocument parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            
            List<PageContent> pages = new ArrayList<>();
            int totalPages = document.getNumberOfPages();
            
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);
                
                if (pageText != null && !pageText.trim().isEmpty()) {
                    pages.add(PageContent.builder()
                        .pageNumber(pageNum)
                        .content(pageText.trim())
                        .build());
                }
            }
            
            String fullText = pages.stream()
                .map(PageContent::getContent)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
            
            log.info("Parsed PDF: {} pages, {} characters", totalPages, fullText.length());
            
            return ParsedDocument.builder()
                .fullText(fullText)
                .pages(pages)
                .pageCount(totalPages)
                .wordCount(countWords(fullText))
                .build();
        }
    }

    private ParsedDocument parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            List<PageContent> sections = new ArrayList<>();
            int sectionIndex = 0;
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    fullText.append(text).append("\n");
                    
                    // Treat each paragraph as a logical section
                    sections.add(PageContent.builder()
                        .pageNumber(++sectionIndex)
                        .content(text.trim())
                        .build());
                }
            }
            
            String content = fullText.toString().trim();
            log.info("Parsed DOCX: {} paragraphs, {} characters", sections.size(), content.length());
            
            return ParsedDocument.builder()
                .fullText(content)
                .pages(sections)
                .pageCount(sections.size())
                .wordCount(countWords(content))
                .build();
        }
    }

    private ParsedDocument parseText(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        
        log.info("Parsed text file: {} characters", content.length());
        
        return ParsedDocument.builder()
            .fullText(content)
            .pages(List.of(PageContent.builder()
                .pageNumber(1)
                .content(content)
                .build()))
            .pageCount(1)
            .wordCount(countWords(content))
            .build();
    }

    private ParsedDocument parseHtml(InputStream inputStream) throws IOException {
        org.jsoup.nodes.Document doc = Jsoup.parse(inputStream, "UTF-8", "");
        
        // Extract text from body, preserving some structure
        String content = doc.body().text();
        
        log.info("Parsed HTML: {} characters", content.length());
        
        return ParsedDocument.builder()
            .fullText(content)
            .pages(List.of(PageContent.builder()
                .pageNumber(1)
                .content(content)
                .build()))
            .pageCount(1)
            .wordCount(countWords(content))
            .build();
    }

    private int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\s+").length;
    }

    /**
     * Parsed document result containing full text and page-level breakdown.
     */
    @lombok.Data
    @lombok.Builder
    public static class ParsedDocument {
        private String fullText;
        private List<PageContent> pages;
        private int pageCount;
        private int wordCount;
    }

    /**
     * Content from a single page or section of a document.
     */
    @lombok.Data
    @lombok.Builder
    public static class PageContent {
        private int pageNumber;
        private String content;
        private String sectionTitle;
    }
}
