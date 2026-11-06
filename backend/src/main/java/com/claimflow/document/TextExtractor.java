package com.claimflow.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Pulls plain text out of uploaded documents so the AI step can read them. */
@Component
public class TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);
    private static final int MAX_CHARS = 20_000;

    public String extract(byte[] content, String contentType) {
        try {
            String text = switch (contentType) {
                case "application/pdf" -> {
                    try (PDDocument doc = Loader.loadPDF(content)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        stripper.setEndPage(Math.min(doc.getNumberOfPages(), 20));
                        yield stripper.getText(doc);
                    }
                }
                case "text/plain" -> new String(content, StandardCharsets.UTF_8);
                default -> null; // images: OCR would go here (e.g. Tesseract); kept out to stay lightweight
            };
            if (text == null) return null;
            text = text.replace("\u0000", "").strip();
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", contentType, e.getMessage());
            return null;
        }
    }
}
