package com.internship.bookverse.service;

import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookService bookService;

    @InjectMocks
    private BulkImportService bulkImportService;

    @Test
    void importBooks_shouldImportAllValidCsvRows() {
        String csv = "title,author,isbn,year,category\n"
                + "Book One,Author One,978-1,2020,Fiction\n"
                + "Book Two,Author Two,978-2,2021,Technology\n"
                + "Book Three,Author Three,,,History";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailedCount()).isEqualTo(0);
        verify(bookRepository, times(1)).saveAll(anyList());
    }

    @Test
    void importBooks_shouldSkipInvalidRows() {
        String csv = "title,author\n"
                + ",Invalid Author\n"  // blank title
                + "Valid Title,Valid Author";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(2);
    }

    @Test
    void importBooks_shouldThrow_whenUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "books.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> bulkImportService.importBooks(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file format");
    }

    @Test
    void importBooks_shouldReturnZeroRows_whenFileHasNullName() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(0);
    }

    @Test
    void importBooks_shouldParseQuotedCommaInDescription() {
        // Comma inside a quoted description must not split the field.
        String csv = "title,author,isbn,description\n"
                + "Book One,Author One,978-1,\"Hello, world\"";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    void importBooks_shouldReportDuplicateIsbnRow_afterInvalidRow() {
        // Row 2 is invalid (blank title); the duplicate ISBN at row 4 must
        // still be reported with row number 4 (its actual file row), not 3.
        // File: header(1), invalid(2), first-DUP(3), second-DUP(4)
        String csv = "title,author,isbn\n"
                + ",Author Skip,978-SKIP\n"
                + "Book One,Author One,978-DUP\n"
                + "Book Two,Author Two,978-DUP";

        MockMultipartFile file = new MockMultipartFile(
                "file", "books.csv", "text/csv", csv.getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getErrors()).extracting("row").containsExactly(2, 4);
        assertThat(result.getErrors().get(1).getRow()).isEqualTo(4);
    }
}
