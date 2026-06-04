package com.dontdelay.exam.infra;

import com.dontdelay.exam.service.model.ExtractedPage;

import java.util.List;

public interface OcrClient {

    List<ExtractedPage> extract(byte[] documentBytes, String filename);
}
