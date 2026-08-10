package com.internship.bookverse.integration;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SoftDeleteIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BookResponse createBook(String isbn) {
        return bookService.create(BookCreateRequest.builder()
                .title("Soft Delete Test Book")
                .author("Test Author")
                .isbn(isbn)
                .build());
    }

    @Test
    void deletedBook_shouldNotAppearInGetAll() {
        BookResponse created = createBook("978-SOFT-DELETE-1");
        bookService.delete(created.getId());

        Page<BookResponse> all = bookService.getAll(PageRequest.of(0, 20), null, null);

        assertThat(all.getContent())
                .extracting(BookResponse::getId)
                .doesNotContain(created.getId());
    }

    @Test
    void deletedBook_getById_shouldThrowNotFound() {
        BookResponse created = createBook("978-SOFT-DELETE-2");
        bookService.delete(created.getId());

        assertThatThrownBy(() -> bookService.getById(created.getId()))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void deletedBook_isbn_shouldBeReusableByNewBook() {
        String isbn = "978-SOFT-DELETE-3";
        BookResponse created = createBook(isbn);
        bookService.delete(created.getId());

        // Should NOT throw a unique-constraint violation here.
        BookResponse recreated = createBook(isbn);

        assertThat(recreated.getId()).isNotNull();
    }

    @Test
    void delete_shouldSoftDelete_notHardDelete_row() {
        BookResponse created = createBook("978-SOFT-DELETE-4");
        bookService.delete(created.getId());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book WHERE id = ? AND deleted = true",
                Integer.class, created.getId());

        assertThat(rowCount).isEqualTo(1);
    }
}