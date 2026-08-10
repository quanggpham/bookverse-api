package com.internship.bookverse.integration;

import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BulkImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BulkImportIntegrationTest {

    @Autowired
    private BulkImportService bulkImportService;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void cleanUp() {
        bookRepository.deleteAll();
    }

    @Test
    void importBooks_shouldSkipRowsWithIsbnDuplicateInsideFile() {
        String csv = "title,author,isbn\n"
                + "Book One,Author One,978-DUP-1\n"
                + "Book Two,Author Two,978-DUP-1\n"
                + "Book Three,Author Three,978-DUP-2";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(bookRepository.count()).isEqualTo(2);
    }

    @Test
    void importBooks_shouldSkipRowsWithIsbnAlreadyInDatabase() {
        bookRepository.save(com.internship.bookverse.entity.Book.builder()
                .title("Existing")
                .author("Author")
                .isbn("978-EXISTING-1")
                .build());

        String csv = "title,author,isbn\n"
                + "New Book One,Author One,978-EXISTING-1\n"
                + "New Book Two,Author Two,978-NEW-1";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(bookRepository.count()).isEqualTo(2);
    }

    @Test
    void importBooks_shouldImportAllRows_whenNoDuplicates() {
        String csv = "title,author,isbn\n"
                + "Book One,Author One,978-OK-1\n"
                + "Book Two,Author Two,978-OK-2";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(bookRepository.count()).isEqualTo(2);
    }
}