package com.internship.bookverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "book", indexes = {
    @Index(name = "idx_book_title_author", columnList = "title, author")
})
@SQLDelete(sql = "UPDATE book SET deleted = true, isbn = NULL WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(unique = true)
    private String isbn;

    @Column(name = "publication_year")
    private Integer year;

    private String category;

    private Double rating;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String coverPath;

    @Builder.Default
    @Column(nullable = false)
    private Boolean deleted = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
