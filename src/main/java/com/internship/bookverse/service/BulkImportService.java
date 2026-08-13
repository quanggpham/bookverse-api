package com.internship.bookverse.service;

import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final BookRepository bookRepository;
    private final BookService bookService;

    @Transactional
    public BulkImportResult importBooks(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            log.debug("importBooks: null filename");
            return BulkImportResult.builder()
                    .totalRows(0).successCount(0).failedCount(0).build();
        }

        log.info("importBooks: file='{}' size={}", filename, file.getSize());

        if (filename.endsWith(".csv")) {
            return importFromCsv(file);
        } else if (filename.endsWith(".xlsx")) {
            return importFromExcel(file);
        } else {
            log.warn("importBooks: unsupported format {}", filename);
            throw new IllegalArgumentException("Unsupported file format. Use .csv or .xlsx");
        }
    }

    /**
     * Internal record tying a CSV row to its source row number (1-based, including header as row 1).
     * This ensures error reporting always references the original spreadsheet/CSV row,
     * even after invalid rows are skipped during parsing.
     */
    private record RowAndBook(int sourceRow, Book book) {}

    private BulkImportResult importFromCsv(MultipartFile file) {
        int totalRows = 0;
        List<BulkImportResult.ImportError> errors = new ArrayList<>();
        List<RowAndBook> validRows = new ArrayList<>();

        try (Reader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withTrim()
                     .parse(reader)) {

            // CSVParser recordNumber is 1-based for data rows (first data row = 1).
            // File row = recordNumber + 1 (header is file row 1, first data is row 2).
            for (CSVRecord record : parser) {
                long recordNumber = parser.getRecordNumber();
                int sourceRow = (int) recordNumber + 1;
                totalRows++;

                try {
                    String title = record.get("title");
                    String author = record.get("author");
                    if (title == null || title.isBlank() || author == null || author.isBlank()) {
                        errors.add(new BulkImportResult.ImportError(sourceRow, "Title and author are required"));
                        continue;
                    }

                    Book book = Book.builder()
                            .title(title.trim())
                            .author(author.trim())
                            .isbn(record.isMapped("isbn") && !record.get("isbn").isBlank()
                                    ? record.get("isbn").trim() : null)
                            .year(record.isMapped("year") && !record.get("year").isBlank()
                                    ? parseYear(record.get("year")) : null)
                            .category(record.isMapped("category") ? record.get("category").trim() : null)
                            .rating(record.isMapped("rating") && !record.get("rating").isBlank()
                                    ? parseRating(record.get("rating")) : null)
                            .description(record.isMapped("description") ? record.get("description").trim() : null)
                            .build();

                    validRows.add(new RowAndBook(sourceRow, book));
                } catch (Exception e) {
                    log.debug("importFromCsv: row {} parse error: {}", sourceRow, e.getMessage());
                    errors.add(new BulkImportResult.ImportError(sourceRow, e.getMessage()));
                }
            }

            if (!validRows.isEmpty()) {
                saveAllCheckingDuplicates(validRows, errors);
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }

        log.info("importFromCsv: rows={} valid={} errors={}", totalRows, validRows.size(), errors.size());

        // Evict all read caches after successful import so subsequent reads see fresh data
        if (!validRows.isEmpty()) {
            bookService.evictReadCaches();
        }

        return BulkImportResult.builder()
                .totalRows(totalRows)
                .successCount(validRows.size())
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }

    private BulkImportResult importFromExcel(MultipartFile file) {
        int totalRows = 0;
        List<BulkImportResult.ImportError> errors = new ArrayList<>();
        List<RowAndBook> validRows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) { // skip header
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                totalRows++;
                int sourceRow = rowIdx + 1; // 1-based: header=1, first data=2

                try {
                    String title = getCellString(row, 0);
                    String author = getCellString(row, 1);
                    if (title == null || title.isBlank() || author == null || author.isBlank()) {
                        errors.add(new BulkImportResult.ImportError(sourceRow, "Title and author are required"));
                        continue;
                    }

                    Book book = Book.builder()
                            .title(title.trim())
                            .author(author.trim())
                            .isbn(getCellString(row, 2))
                            .year(parseYear(getCellString(row, 3)))
                            .category(getCellString(row, 4))
                            .rating(parseRating(getCellString(row, 5)))
                            .description(getCellString(row, 6))
                            .build();

                    validRows.add(new RowAndBook(sourceRow, book));
                } catch (Exception e) {
                    log.debug("importFromExcel: row {} parse error: {}", sourceRow, e.getMessage());
                    errors.add(new BulkImportResult.ImportError(sourceRow, e.getMessage()));
                }
            }

            if (!validRows.isEmpty()) {
                saveAllCheckingDuplicates(validRows, errors);
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage());
        }

        log.info("importFromExcel: rows={} valid={} errors={}", totalRows, validRows.size(), errors.size());

        if (!validRows.isEmpty()) {
            bookService.evictReadCaches();
        }

        return BulkImportResult.builder()
                .totalRows(totalRows)
                .successCount(validRows.size())
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }

    /**
     * Persists the validated books while guarding against ISBN duplicate
     * collisions. Rows whose ISBN already exists in the database (or was seen
     * earlier in the same import) are reported as failed rows rather than
     * blowing up the whole save. The source row number from the original
     * CSV/Excel is preserved in the error.
     */
    private void saveAllCheckingDuplicates(List<RowAndBook> validRows,
                                           List<BulkImportResult.ImportError> errors) {
        List<RowAndBook> selected = new ArrayList<>(validRows.size());
        Set<String> seenIsbns = new HashSet<>();

        for (RowAndBook rowAndBook : validRows) {
            Book candidate = rowAndBook.book();
            String isbn = candidate.getIsbn();
            if (isbn != null && !isbn.isBlank()
                    && (bookRepository.existsByIsbn(isbn) || !seenIsbns.add(isbn))) {
                log.debug("saveAllCheckingDuplicates: skipping duplicate isbn={} at row {}", isbn, rowAndBook.sourceRow());
                errors.add(new BulkImportResult.ImportError(
                        rowAndBook.sourceRow(), "Duplicate ISBN in file or database: " + isbn));
            } else {
                selected.add(rowAndBook);
            }
        }

        // Replace the original validRows list with the filtered list so successCount is correct
        validRows.clear();
        validRows.addAll(selected);

        if (!selected.isEmpty()) {
            log.debug("saveAllCheckingDuplicates: persisting {} books", selected.size());
            bookRepository.saveAll(selected.stream().map(RowAndBook::book).toList());
        }
    }

    private String getCellString(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> cell.toString();
        };
    }

    private Integer parseYear(String s) {
        if (s == null || s.isBlank()) return null;
        return Integer.parseInt(s.trim());
    }

    private Double parseRating(String s) {
        if (s == null || s.isBlank()) return null;
        return Double.parseDouble(s.trim());
    }
}