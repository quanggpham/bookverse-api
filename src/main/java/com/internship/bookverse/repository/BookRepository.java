package com.internship.bookverse.repository;

import com.internship.bookverse.dto.response.CategoryCount;
import com.internship.bookverse.dto.response.YearCount;
import com.internship.bookverse.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT b FROM Book b WHERE (:category IS NULL OR b.category = :category) AND (:year IS NULL OR b.year = :year)")
    Page<Book> findByFilters(@Param("category") String category, @Param("year") Integer year, Pageable pageable);

    @Query("SELECT b FROM Book b WHERE (LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND (:category IS NULL OR b.category = :category) AND (:year IS NULL OR b.year = :year)")
    Page<Book> searchBooks(@Param("title") String title, @Param("author") String author, @Param("category") String category, @Param("year") Integer year, Pageable pageable);

    @Query("SELECT b.category AS name, COUNT(b) AS count FROM Book b WHERE b.category IS NOT NULL AND b.category <> '' GROUP BY b.category ORDER BY COUNT(b) DESC")
    List<CategoryCount> findDistinctCategories();

    @Query("SELECT b.year AS year, COUNT(b) AS count FROM Book b WHERE b.year IS NOT NULL GROUP BY b.year ORDER BY b.year DESC")
    List<YearCount> findDistinctYears();

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);
}
