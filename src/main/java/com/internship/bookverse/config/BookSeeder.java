package com.internship.bookverse.config;

import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookCsvParser;
import com.internship.bookverse.service.BookCsvRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seeds the database with sample books on startup when it is empty.
 *
 * <p>Reads the Book-Crossing CSV dataset ({@code data/books.csv}), dedupes by
 * ISBN, picks a random subset, and fills the fields the dataset does not
 * provide: category (from publisher), rating (3.0–5.0) and a generated
 * description. Seeding only runs when the book table is empty.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class BookSeeder implements CommandLineRunner {

    static final int SEED_COUNT = 500;

    private final BookRepository bookRepository;
    private final BookCsvParser parser;

    @Value("${app.seed.csv-path:data/books.csv}")
    private String csvPath;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("seed: disabled (app.seed.enabled=false)");
            return;
        }
        if (bookRepository.count() > 0) {
            log.info("seed: skipped — book table already has {} rows", bookRepository.count());
            return;
        }

        List<BookCsvRecord> records = readRecords();
        if (records.isEmpty()) {
            log.warn("seed: no records parsed from {} — nothing to seed", csvPath);
            return;
        }

        List<BookCsvRecord> deduped = dedupeByIsbn(records);
        log.info("seed: parsed {} rows, {} unique after ISBN dedupe", records.size(), deduped.size());

        List<Book> books = pickRandom(deduped, SEED_COUNT);
        bookRepository.saveAll(books);
        log.info("seed: inserted {} books from {}", books.size(), csvPath);
    }

    private List<BookCsvRecord> readRecords() {
        Path path = Paths.get(csvPath);
        if (!Files.exists(path)) {
            log.warn("seed: CSV not found at {} — skipping seed", path.toAbsolutePath());
            return List.of();
        }
        try (InputStream in = Files.newInputStream(path)) {
            return parser.parse(in);
        } catch (IOException e) {
            log.error("seed: failed reading {} — skipping seed", path.toAbsolutePath(), e);
            return List.of();
        }
    }

    private List<BookCsvRecord> dedupeByIsbn(List<BookCsvRecord> records) {
        Set<String> seen = new HashSet<>();
        List<BookCsvRecord> deduped = new ArrayList<>(records.size());
        for (BookCsvRecord rec : records) {
            if (rec.isbn() != null && !rec.isbn().isBlank() && !seen.add(rec.isbn())) {
                continue; // already saw this ISBN
            }
            deduped.add(rec);
        }
        return deduped;
    }

    private List<Book> pickRandom(List<BookCsvRecord> pool, int count) {
        List<BookCsvRecord> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        int take = Math.min(count, shuffled.size());

        List<Book> books = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            books.add(toBook(shuffled.get(i)));
        }
        return books;
    }

    private Book toBook(BookCsvRecord rec) {
        String category = rec.publisher() != null && !rec.publisher().isBlank()
                ? rec.publisher()
                : "Unknown";
        return Book.builder()
                .isbn(rec.isbn())
                .title(rec.title())
                .author(rec.author())
                .year(rec.year())
                .category(category)
                .rating(Math.round((3.0 + random.nextDouble() * 2.0) * 10.0) / 10.0) // 3.0–5.0
                .description("A sample book: " + rec.title() + " by " + rec.author()
                        + " (published " + rec.year() + ").")
                .build();
    }
}
