package com.dontdelay.exam.service;

import com.dontdelay.exam.config.ExamProperties;
import com.dontdelay.exam.infra.OcrClient;
import com.dontdelay.exam.service.model.ExtractedPage;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExtractionService {

    private final ExamProperties examProperties;
    private final ObjectProvider<OcrClient> ocrClientProvider;

    public ExtractionService(ExamProperties examProperties, ObjectProvider<OcrClient> ocrClientProvider) {
        this.examProperties = examProperties;
        this.ocrClientProvider = ocrClientProvider;
    }

    public List<ExtractedPage> extract(InputStream pdfStream) {
        byte[] pdfBytes;
        try {
            pdfBytes = pdfStream.readAllBytes();
        } catch (IOException e) {
            throw new ExtractionException("EXTRACTION_FAILED", "PDF 파일을 읽지 못했습니다.", e);
        }

        List<ExtractedPage> pdfBoxPages = extractWithPdfBox(pdfBytes);
        if (!needsOcr(pdfBoxPages)) {
            validateExtractedText(pdfBoxPages);
            return pdfBoxPages;
        }

        return extractWithOcrFallback(pdfBytes, pdfBoxPages);
    }

    private List<ExtractedPage> extractWithPdfBox(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new ExtractionException("ENCRYPTED_PDF", "암호화된 PDF는 처리할 수 없습니다.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            List<ExtractedPage> pages = new ArrayList<>(pageCount);

            for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String text = stripper.getText(document);
                pages.add(new ExtractedPage(pageNo, normalize(text)));
            }

            return pages;
        } catch (ExtractionException e) {
            throw e;
        } catch (IOException e) {
            throw new ExtractionException("EXTRACTION_FAILED", "PDF 텍스트 추출에 실패했습니다.", e);
        }
    }

    private List<ExtractedPage> extractWithOcrFallback(byte[] pdfBytes, List<ExtractedPage> pdfBoxPages) {
        if (!examProperties.getOcr().isEnabled()) {
            validateExtractedText(pdfBoxPages);
            return pdfBoxPages;
        }

        OcrClient ocrClient = ocrClientProvider.getIfAvailable();
        if (ocrClient == null) {
            int totalChars = pdfBoxPages.stream().mapToInt(page -> page.text().length()).sum();
            if (totalChars == 0) {
                throw new ExtractionException(
                        "OCR_FAILED",
                        "스캔 PDF 처리를 위해 UPSTAGE_API_KEY 설정이 필요합니다."
                );
            }
            log.warn("PDF text is sparse but Upstage OCR is not configured; using PDFBox result.");
            validateExtractedText(pdfBoxPages);
            return pdfBoxPages;
        }

        log.info("PDFBox text sparse (avg chars/page below threshold); falling back to Upstage OCR");
        List<ExtractedPage> ocrPages = ocrClient.extract(pdfBytes, "document.pdf");
        validateExtractedText(ocrPages);
        return ocrPages;
    }

    boolean needsOcr(List<ExtractedPage> pages) {
        if (pages.isEmpty()) {
            return true;
        }

        int totalChars = pages.stream().mapToInt(page -> page.text().length()).sum();
        if (totalChars == 0) {
            return true;
        }

        int threshold = examProperties.getOcr().getMinCharsPerPage();
        double avgCharsPerPage = (double) totalChars / pages.size();
        return avgCharsPerPage < threshold;
    }

    private void validateExtractedText(List<ExtractedPage> pages) {
        int totalChars = pages.stream().mapToInt(page -> page.text().length()).sum();
        if (totalChars == 0) {
            throw new ExtractionException("EMPTY_PDF", "PDF에서 추출된 텍스트가 없습니다.");
        }
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
