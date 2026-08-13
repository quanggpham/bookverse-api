package com.internship.bookverse.integration;

import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookService;
import com.internship.bookverse.service.BulkImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BulkImportIntegrationTest {

    @Autowired
    private BulkImportService bulkImportService;

    @Autowired
    private BookService bookService;

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

    @Test
    void importBooks_shouldEvictCache_afterSuccessfulImport() {
        // Seed the database so the cache can be populated
        String csv = "title,author,isbn\n"
                + "Seed Book,Seed Author,978-SEED";

        MockMultipartFile seedFile = new MockMultipartFile(
                "file", "seed.csv", "text/csv", csv.getBytes());
        bulkImportService.importBooks(seedFile);

        // Populate the books cache by calling getAll
        Page<BookResponse> before = bookService.getAll(
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))),
                null, null);
        assertThat(before.getTotalElements()).isEqualTo(1);

        // Import new books
        String csv2 = "title,author,isbn\n"
                + "New Book,New Author,978-NEW-1";

        MockMultipartFile importFile = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv2.getBytes());
        BulkImportResult result = bulkImportService.importBooks(importFile);

        assertThat(result.getSuccessCount()).isEqualTo(1);

        // The cache should be evicted, so a fresh getAll reflects the new data
        // without needing to clear the cache manually
        Page<BookResponse> after = bookService.getAll(
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))),
                null, null);
        assertThat(after.getTotalElements()).isEqualTo(2);
    }
}