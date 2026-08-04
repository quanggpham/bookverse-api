package com.internship.bookverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private Integer year;
    private String category;
    private Double rating;
    private String description;
    private String coverPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
