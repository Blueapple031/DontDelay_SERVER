package com.dontdelay.exam.dto;

import java.util.List;

public record DocumentListResponse(
        List<DocumentSummaryDto> items,
        long total
) {
}
