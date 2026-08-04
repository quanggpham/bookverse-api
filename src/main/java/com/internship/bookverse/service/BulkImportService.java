package com.internship.bookverse.service;

import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final BookRepository bookRepository;

    @Transactional
    public BulkImportResult importBooks(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return BulkImportResult.builder()
                    .totalRows(0).successCount(0).failedCount(0).build();
        }

        if (filename.endsWith(".csv")) {
            return importFromCsv(file);
        } else if (filename.endsWith(".xlsx")) {
            return importFromExcel(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Use .csv or .xlsx");
        }
    }

    private BulkImportResult importFromCsv(MultipartFile file) {
        int totalRows = 0;
        int successCount = 0;
        List<BulkImportResult.ImportError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String header = reader.readLine(); // skip header
            if (header == null) {
                return BulkImportResult.builder()
                        .totalRows(0).successCount(0).failedCount(0).build();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length < 2 || fields[0].isBlank() || fields[1].isBlank()) {
                        errors.add(new BulkImportResult.ImportError(totalRows + 1, "Title and author are required"));
                        continue;
                    }

                    Book book = Book.builder()
                            .title(fields[0].trim())
                            .author(fields[1].trim())
                            .isbn(fields.length > 2 && !fields[2].isBlank() ? fields[2].trim() : null)
                            .year(fields.length > 3 && !fields[3].isBlank() ? parseYear(fields[3]) : null)
                            .category(fields.length > 4 ? fields[4].trim() : null)
                            .rating(fields.length > 5 && !fields[5].isBlank() ? parseRating(fields[5]) : null)
                            .description(fields.length > 6 ? fields[6].trim() : null)
                            .build();

                    bookRepository.save(book);
                    successCount++;
                } catch (Exception e) {
                    errors.add(new BulkImportResult.ImportError(totalRows + 1, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }

        return BulkImportResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }

    private BulkImportResult importFromExcel(MultipartFile file) {
        int totalRows = 0;
        int successCount = 0;
        List<BulkImportResult.ImportError> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) { // skip header
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                totalRows++;

                try {
                    String title = getCellString(row, 0);
                    String author = getCellString(row, 1);
                    if (title == null || title.isBlank() || author == null || author.isBlank()) {
                        errors.add(new BulkImportResult.ImportError(rowIdx + 1, "Title and author are required"));
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

                    bookRepository.save(book);
                    successCount++;
                } catch (Exception e) {
                    errors.add(new BulkImportResult.ImportError(rowIdx + 1, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage());
        }

        return BulkImportResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(errors.size())
                .errors(errors)
                .build();
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
