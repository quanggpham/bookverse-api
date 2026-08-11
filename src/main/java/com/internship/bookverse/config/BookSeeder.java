package com.internship.bookverse.config;

import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookCsvParser;
import com.internship.bookverse.service.BookCsvRecord;
import com.internship.bookverse.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private final ImageService imageService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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

        List<Picked> picked = pickRandom(deduped, SEED_COUNT);
        List<Book> books = picked.stream().map(Picked::book).toList();
        // Save first so each book has an id (ImageService.upload keys files by book id),
        // then fetch covers and persist the cover paths.
        bookRepository.saveAll(books);
        int withCover = 0;
        for (Picked p : picked) {
            if (p.record().coverUrl() != null && !p.record().coverUrl().isBlank()) {
                if (downloadCover(p.book(), p.record().coverUrl())) {
                    withCover++;
                }
            }
        }
        bookRepository.saveAll(books);
        log.info("seed: inserted {} books from {} ({} with covers)",
                books.size(), csvPath, withCover);
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

    private List<Picked> pickRandom(List<BookCsvRecord> pool, int count) {
        List<BookCsvRecord> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        int take = Math.min(count, shuffled.size());

        List<Picked> picked = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            picked.add(new Picked(toBook(shuffled.get(i)), shuffled.get(i)));
        }
        return picked;
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

    /** A picked book bound to its CSV record (for its cover URL). */
    private record Picked(Book book, BookCsvRecord record) {
    }

    /**
     * Downloads the CSV cover URL and pipes it through {@link ImageService},
     * which resizes and converts it to the three standard WebP sizes. Failures
     * (unreachable host, non-image response, ...) are logged and leave the
     * book without a cover rather than aborting the seed.
     */
    /** Downloads one cover; returns {@code true} when the book ended up with a cover. */
    private boolean downloadCover(Book book, String coverUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(coverUrl))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.debug("seed: cover download failed for '{}' ({}): HTTP {}",
                        book.getTitle(), coverUrl, response.statusCode());
                return false;
            }

            MultipartFile multipart = new ByteArrayMultipartFile("cover", "cover.jpg", "image/jpeg",
                    response.body());
            String coverPath = imageService.upload(multipart, book.getId());
            if (coverPath != null) {
                book.setCoverPath(coverPath);
                return true;
            }
        } catch (Exception e) {
            log.debug("seed: cover download failed for '{}' ({}): {}", book.getTitle(),
                    coverUrl, e.getMessage());
        }
        return false;
    }

    /** Minimal MultipartFile backed by an in-memory byte array. */
    private record ByteArrayMultipartFile(
            String name, String originalFilename, String contentType, byte[] content)
            implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
