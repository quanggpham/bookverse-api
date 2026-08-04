package com.internship.bookverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ValidationErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> details;

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }
}
