package com.internship.bookverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResult {

    private int totalRows;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    @Getter
    @AllArgsConstructor
    public static class ImportError {
        private int row;
        private String reason;
    }
}
