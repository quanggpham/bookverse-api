package com.internship.bookverse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the Book-Crossing CSV dataset ({@code data/books.csv}).
 *
 * <p>The file is {@code ;}-delimited with double-quoted fields. Only the five
 * identity-ish columns are read: ISBN, title, author, year, publisher. HTML
 * entities such as {@code &amp;} are unescaped. Malformed rows (wrong column
 * count, blank title, invalid year) are skipped.
 */
@Slf4j
@Component
public class BookCsvParser {

    private static final int MIN_COLUMN_COUNT = 5; // identity columns we consume; full rows have 8
    private static final int IDX_ISBN = 0;
    private static final int IDX_TITLE = 1;
    private static final int IDX_AUTHOR = 2;
    private static final int IDX_YEAR = 3;
    private static final int IDX_PUBLISHER = 4;

    /**
     * Parses an entire CSV stream, skipping the header and malformed rows.
     *
     * @param in the CSV input stream
     * @return valid records, never {@code null}
     */
    public List<BookCsvRecord> parse(InputStream in) {
        List<BookCsvRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header line
            if (header == null) {
                log.debug("parse: empty CSV input");
                return records;
            }
            String line;
            int skipped = 0;
            while ((line = reader.readLine()) != null) {
                BookCsvRecord rec = parseLine(line);
                if (rec != null) {
                    records.add(rec);
                } else {
                    skipped++;
                }
            }
            log.debug("parse: read {} records, skipped {} malformed lines", records.size(), skipped);
        } catch (IOException e) {
            log.warn("parse: failed reading CSV input: {}", e.getMessage());
        }
        return records;
    }

    /**
     * Parses a single {@code ;}-delimited CSV line into a record, or returns
     * {@code null} when the line is malformed.
     */
    BookCsvRecord parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] fields = split(line);
        if (fields.length < MIN_COLUMN_COUNT) {
            log.debug("parseLine: skipping line with {} fields (expected at least {})", fields.length, MIN_COLUMN_COUNT);
            return null;
        }

        String title = unquote(fields[IDX_TITLE]);
        if (title == null || title.isBlank()) {
            return null;
        }
        String author = unquote(fields[IDX_AUTHOR]);
        if (author == null || author.isBlank()) {
            return null;
        }

        Integer year = parseYear(fields[IDX_YEAR]);
        if (year == null) {
            return null;
        }

        return new BookCsvRecord(
                unquote(fields[IDX_ISBN]),
                title,
                author,
                year,
                unquote(fields[IDX_PUBLISHER]));
    }

    /**
     * Splits on {@code ;} while respecting double-quoted fields, so a
     * semicolon inside quotes does not break the row. Escaped quotes are not
     * expected in this dataset.
     */
    private String[] split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ';' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("&amp;", "&");
    }

    private Integer parseYear(String raw) {
        String s = unquote(raw);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            int year = Integer.parseInt(s.trim());
            return (year >= 1800 && year <= 2100) ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
