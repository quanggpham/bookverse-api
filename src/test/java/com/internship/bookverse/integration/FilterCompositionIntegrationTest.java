package com.internship.bookverse.integration;

import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FilterCompositionIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void cleanUp() {
        bookRepository.deleteAll();
    }

    private Book book(String title, String category, Integer year) {
        return Book.builder().title(title).author("Author").category(category).year(year).build();
    }

    @Test
    void getAll_shouldComposeCategoryAndYear() {
        bookRepository.saveAll(List.of(
                book("Alpha", "Science", 2020),
                book("Beta", "Science", 2015),
                book("Gamma", "Fiction", 2020)));

        Page<BookResponse> page = bookService.getAll(PageRequest.of(0, 20), "Science", 2020);

        assertThat(page.getContent())
                .extracting(BookResponse::getTitle)
                .containsExactly("Alpha");
    }

    @Test
    void getCategories_shouldReturnCountsSortedDesc() {
        bookRepository.saveAll(List.of(
                book("A", "Science", 2020),
                book("B", "Science", 2015),
                book("C", "Fiction", 2020)));

        var counts = bookService.getCategories();

        assertThat(counts).extracting(c -> c.name()).containsExactly("Science", "Fiction");
        assertThat(counts.get(0).count()).isEqualTo(2);
    }

    @Test
    void getYears_shouldReturnYearsSortedDesc() {
        bookRepository.saveAll(List.of(
                book("A", "Science", 2020),
                book("B", "Science", 2015),
                book("C", "Fiction", 2015)));

        var years = bookService.getYears();

        assertThat(years).extracting(y -> y.year()).containsExactly(2020, 2015);
        assertThat(years.get(1).count()).isEqualTo(2);
    }
}
