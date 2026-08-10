package com.internship.bookverse.controller;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.dto.response.BulkImportResult;
import com.internship.bookverse.dto.response.CategoryCount;
import com.internship.bookverse.dto.response.YearCount;
import com.internship.bookverse.service.BookService;
import com.internship.bookverse.service.BulkImportService;
import com.internship.bookverse.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final ImageService imageService;
    private final BulkImportService bulkImportService;

    @GetMapping
    public ResponseEntity<Page<BookResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year) {
        log.info("GET /api/books?page={}&size={}&category={}&year={}",
                pageable.getPageNumber(), pageable.getPageSize(), category, year);
        return ResponseEntity.ok(bookService.getAll(pageable, category, year));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getById(@PathVariable Long id) {
        log.debug("GET /api/books/{}", id);
        return ResponseEntity.ok(bookService.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> create(
            @RequestPart("book") @Valid BookCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        log.info("POST /api/books title='{}' hasCover={}", request.getTitle(), cover != null && !cover.isEmpty());
        BookResponse response = bookService.create(request);
        if (cover != null && !cover.isEmpty()) {
            String coverPath = imageService.upload(cover, response.getId());
            response = bookService.updateCoverPath(response.getId(), coverPath);
            log.info("POST /api/books: cover uploaded for id={} path={}", response.getId(), coverPath);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkImportResult> bulkImport(
            @RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        log.info("POST /api/books/bulk file='{}' size={} bytes", filename, file.getSize());
        BulkImportResult result = bulkImportService.importBooks(file);
        log.info("POST /api/books/bulk: total={} success={} failed={}",
                result.getTotalRows(), result.getSuccessCount(), result.getFailedCount());
        return ResponseEntity.ok(result);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> update(
            @PathVariable Long id,
            @RequestPart("book") @Valid BookUpdateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        log.info("PUT /api/books/{} title='{}' hasCover={}", id, request.getTitle(),
                cover != null && !cover.isEmpty());
        BookResponse response = bookService.update(id, request);
        if (cover != null && !cover.isEmpty()) {
            String coverPath = imageService.upload(cover, id);
            response = bookService.updateCoverPath(id, coverPath);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /api/books/{}", id);
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<BookResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("GET /api/books/search?q='{}'&category={}&year={}&page={}", q, category, year, pageable.getPageNumber());
        return ResponseEntity.ok(bookService.search(q, category, year, pageable));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryCount>> getCategories() {
        log.info("GET /api/books/categories");
        return ResponseEntity.ok(bookService.getCategories());
    }

    @GetMapping("/years")
    public ResponseEntity<List<YearCount>> getYears() {
        log.info("GET /api/books/years");
        return ResponseEntity.ok(bookService.getYears());
    }

    @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> updateCover(
            @PathVariable Long id,
            @RequestPart("cover") MultipartFile cover) {
        log.info("PUT /api/books/{}/cover size={} bytes", id, cover.getSize());
        bookService.getById(id); // pre-check existence so we 404 before writing files
        String coverPath = imageService.upload(cover, id);
        return ResponseEntity.ok(bookService.updateCoverPath(id, coverPath));
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<Resource> getCover(
            @PathVariable Long id,
            @RequestParam(defaultValue = "large") String size) {
        log.debug("GET /api/books/{}/cover?size={}", id, size);
        BookResponse book = bookService.getById(id);
        return imageService.serve(book.getCoverPath(), size);
    }
}
