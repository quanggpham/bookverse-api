package com.internship.bookverse.repository;

import com.internship.bookverse.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByCategory(String category, Pageable pageable);

    @Query("SELECT b FROM Book b WHERE (LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND (:category IS NULL OR b.category = :category)")
    Page<Book> searchBooks(@Param("title") String title, @Param("author") String author, @Param("category") String category, Pageable pageable);

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);
}
