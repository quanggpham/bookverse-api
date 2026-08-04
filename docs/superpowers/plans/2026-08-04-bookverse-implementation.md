# BookVerse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-ready REST API for managing e-books with cover image processing, full-text search, caching, and bulk import.

**Architecture:** Layered Spring Boot 3.3 application — Controller → Service → Repository. Single BookController handling all `/api/books/*` endpoints. MapStruct for DTO mapping. Caffeine for API caching + HTTP cache headers for images. Soft-delete via `@SQLDelete`.

**Tech Stack:** Java 21, Spring Boot 3.3.2, Spring Data JPA, H2 (dev) / PostgreSQL (prod), MapStruct 1.5.5, Thumbnailator 0.4.20, Apache POI 5.3.0, Caffeine, Lombok, Springdoc OpenAPI 2.6.0

## Phases & Review Gates

| Phase | Deliverable | Review Metrics |
|-------|-------------|----------------|
| 1. Foundation | POM + config + entity + repository + error handling | Compile clean, H2 console accessible, schema generated |
| 2. Service Layer | BookService + BookMapper + unit tests | All service tests pass, coverage ≥ 80% |
| 3. Controller Layer | BookController + controller tests | All endpoints respond correctly via MockMvc |
| 4. Image Processing | ImageService + cover upload/serve | Image upload → 3 sizes + WebP verified manually |
| 5. Bulk Import | BulkImportService + bulk endpoint | Excel/CSV import returns correct summary |
| 6. Caching | CacheConfig + @Cacheable + eviction | Cache hit/miss verified, eviction on mutations |
| 7. Documentation & Polish | Swagger UI, final review | Swagger UI shows all endpoints, no warnings |

**Review file per phase:** `docs/superpowers/reviews/phase-X-review.md`

## Global Constraints

- Java 21
- Spring Boot 3.3.2
- Package-by-layer: controller/service/repository/entity/dto/mapper/config/exception
- No interfaces for single-implementation services
- Success: return DTO directly, no wrapper envelope
- Error: ErrorResponse with code, message, timestamp, path
- Soft delete via @SQLDelete + @Where
- PUT is JSON-only (no cover upload)
- Bulk import: metadata only, synchronous, skip invalid rows

---

### Phase 1: Foundation

**Goal:** Project compiles, database connects, entity schema created, error handling framework in place.

#### Task 1.1: Add Caffeine dependency and application config

**Files:**
- Modify: `pom.xml` (add Caffeine dependency)
- Create: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `application.yml` with H2 datasource, JPA ddl-auto=update, multipart max 5MB, H2 console enabled

- [ ] **Step 1: Add Caffeine to pom.xml**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Add this after the `spring-boot-starter-cache` dependency block.

- [ ] **Step 2: Create application.yml**

```yaml
spring:
  application:
    name: bookverse
  datasource:
    url: jdbc:h2:mem:bookverse
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB

server:
  port: 8080

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 3: Verify Maven compiles**

```bash
mvn compile
```

Expected: BUILD SUCCESS, no errors.

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git commit -m "feat: add Caffeine dependency and application config"
```

#### Task 1.2: Create Book entity

**Files:**
- Create: `src/main/java/com/internship/bookverse/entity/Book.java`

**Interfaces:**
- Produces: `Book` JPA entity with all fields, @SQLDelete, @Where, @PrePersist, @PreUpdate, @Table index

- [ ] **Step 1: Create Book entity**

```java
package com.internship.bookverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(name = "book", indexes = {
    @Index(name = "idx_book_title_author", columnList = "title, author")
})
@SQLDelete(sql = "UPDATE book SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(unique = true)
    private String isbn;

    private Integer year;

    private String category;

    private Double rating;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String coverPath;

    @Column(nullable = false)
    private Boolean deleted = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/entity/Book.java
git commit -m "feat: add Book entity with soft delete and audit"
```

#### Task 1.3: Create BookRepository

**Files:**
- Create: `src/main/java/com/internship/bookverse/repository/BookRepository.java`

**Interfaces:**
- Produces: `BookRepository extends JpaRepository<Book, Long>` with search methods

- [ ] **Step 1: Create BookRepository**

```java
package com.internship.bookverse.repository;

import com.internship.bookverse.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByCategory(String category, Pageable pageable);

    Page<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
            String title, String author, Pageable pageable);

    Page<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseAndCategory(
            String title, String author, String category, Pageable pageable);

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/repository/BookRepository.java
git commit -m "feat: add BookRepository with search and filter methods"
```

#### Task 1.4: Create DTOs

**Files:**
- Create: `src/main/java/com/internship/bookverse/dto/ErrorResponse.java`
- Create: `src/main/java/com/internship/bookverse/dto/request/BookCreateRequest.java`
- Create: `src/main/java/com/internship/bookverse/dto/request/BookUpdateRequest.java`
- Create: `src/main/java/com/internship/bookverse/dto/response/BookResponse.java`
- Create: `src/main/java/com/internship/bookverse/dto/response/BulkImportResult.java`

**Interfaces:**
- Produces: All DTOs with Jakarta Validation annotations

- [ ] **Step 1: Create ErrorResponse**

```java
package com.internship.bookverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
}
```

- [ ] **Step 2: Create BookCreateRequest**

```java
package com.internship.bookverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookCreateRequest {

    @NotBlank(message = "Title must not be blank")
    private String title;

    @NotBlank(message = "Author must not be blank")
    private String author;

    private String isbn;

    private Integer year;

    private String category;

    private Double rating;

    private String description;
}
```

- [ ] **Step 3: Create BookUpdateRequest**

```java
package com.internship.bookverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookUpdateRequest {

    @NotBlank(message = "Title must not be blank")
    private String title;

    @NotBlank(message = "Author must not be blank")
    private String author;

    private String isbn;

    private Integer year;

    private String category;

    private Double rating;

    private String description;
}
```

- [ ] **Step 4: Create BookResponse**

```java
package com.internship.bookverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private Integer year;
    private String category;
    private Double rating;
    private String description;
    private String coverPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: Create BulkImportResult**

```java
package com.internship.bookverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResult {

    private int totalRows;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    @Getter
    @AllArgsConstructor
    public static class ImportError {
        private int row;
        private String reason;
    }
}
```

- [ ] **Step 6: Create ValidationErrorResponse**

```java
package com.internship.bookverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ValidationErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> details;

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }
}
```

- [ ] **Step 7: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/internship/bookverse/dto/
git commit -m "feat: add request/response DTOs, ErrorResponse and ValidationErrorResponse"
```

#### Task 1.5: Create GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/internship/bookverse/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `@RestControllerAdvice` handling all exception types per spec error codes
- Produces: Custom `BookNotFoundException`, `IsbnAlreadyExistsException`, `InvalidImageFormatException`

- [ ] **Step 1: Create custom exceptions**

Create `src/main/java/com/internship/bookverse/exception/BookNotFoundException.java`:

```java
package com.internship.bookverse.exception;

public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(Long id) {
        super("Book not found with id: " + id);
    }
}
```

Create `src/main/java/com/internship/bookverse/exception/IsbnAlreadyExistsException.java`:

```java
package com.internship.bookverse.exception;

public class IsbnAlreadyExistsException extends RuntimeException {
    public IsbnAlreadyExistsException(String isbn) {
        super("ISBN already exists: " + isbn);
    }
}
```

Create `src/main/java/com/internship/bookverse/exception/InvalidImageFormatException.java`:

```java
package com.internship.bookverse.exception;

public class InvalidImageFormatException extends RuntimeException {
    public InvalidImageFormatException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create GlobalExceptionHandler**

```java
package com.internship.bookverse.exception;

import com.internship.bookverse.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookNotFound(
            BookNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("BOOK_NOT_FOUND")
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(IsbnAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleIsbnDuplicate(
            IsbnAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .code("ISBN_ALREADY_EXISTS")
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(InvalidImageFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidImageFormat(
            InvalidImageFormatException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("INVALID_IMAGE_FORMAT")
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.builder()
                        .code("FILE_TOO_LARGE")
                        .message("File size exceeds the maximum allowed size")
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ValidationErrorResponse.FieldError> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ValidationErrorResponse.FieldError(
                        fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ValidationErrorResponse.builder()
                        .code("VALIDATION_ERROR")
                        .message("Validation failed")
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .details(details)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternal(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .code("INTERNAL_ERROR")
                        .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                        .timestamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build());
    }
}
```

Note: `ValidationErrorResponse` is created as a separate DTO (added in Step 6 of Task 1.4 above).

- [ ] **Step 3: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/internship/bookverse/exception/ src/main/java/com/internship/bookverse/dto/
git commit -m "feat: add exception classes and global exception handler"
```

#### Task 1.6: Create main application class

**Files:**
- Create: `src/main/java/com/internship/bookverse/BookVerseApplication.java`

- [ ] **Step 1: Create BookVerseApplication**

```java
package com.internship.bookverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookVerseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookVerseApplication.class, args);
    }
}
```

- [ ] **Step 2: Start application and verify it runs**

```bash
mvn spring-boot:run
```

Expected: Application starts on port 8080. Visit http://localhost:8080/h2-console to verify H2 console is accessible. Stop with Ctrl+C.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/BookVerseApplication.java
git commit -m "feat: add Spring Boot main application class"
```

### Phase 1 Review Gate

Run: `mvn compile`
Checklist:
- [ ] No compilation errors
- [ ] Application starts successfully (`mvn spring-boot:run`)
- [ ] H2 console accessible at http://localhost:8080/h2-console
- [ ] Book table auto-created in H2 (check via H2 console)
- [ ] Count classes: Entity=1, Repository=1, DTOs=6, Exceptions=4, Config=0

Write review results to `docs/superpowers/reviews/phase-1-review.md`.

---

### Phase 2: Service Layer

**Goal:** BookService with full CRUD + search logic, BookMapper for DTO mapping, unit tests with 80%+ coverage.

#### Task 2.1: Create BookMapper

**Files:**
- Create: `src/main/java/com/internship/bookverse/mapper/BookMapper.java`

**Interfaces:**
- Produces: `BookMapper` MapStruct interface with `toEntity(CreateRequest)`, `toEntity(UpdateRequest)`, `toResponse(Book)`, `updateEntity(UpdateRequest, @MappingTarget Book)`

- [ ] **Step 1: Create BookMapper**

```java
package com.internship.bookverse.mapper;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverPath", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Book toEntity(BookCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverPath", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(BookUpdateRequest request, @MappingTarget Book book);

    BookResponse toResponse(Book book);
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS (MapStruct generates implementation at compile time).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/mapper/BookMapper.java
git commit -m "feat: add MapStruct BookMapper"
```

#### Task 2.2: Create BookService

**Files:**
- Create: `src/main/java/com/internship/bookverse/service/BookService.java`

**Interfaces:**
- Consumes: `BookRepository`, `BookMapper`
- Produces: `BookService` with methods: `getAll`, `getById`, `create`, `update`, `delete`, `search`

- [ ] **Step 1: Create BookService**

```java
package com.internship.bookverse.service;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.exception.IsbnAlreadyExistsException;
import com.internship.bookverse.mapper.BookMapper;
import com.internship.bookverse.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public Page<BookResponse> getAll(Pageable pageable, String category, Integer year) {
        if (category != null) {
            return bookRepository.findByCategory(category, pageable)
                    .map(bookMapper::toResponse);
        }
        return bookRepository.findAll(pageable)
                .map(bookMapper::toResponse);
    }

    public BookResponse getById(Long id) {
        Book book = findBookOrThrow(id);
        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse create(BookCreateRequest request) {
        validateIsbnUniqueness(request.getIsbn());
        Book book = bookMapper.toEntity(request);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    @Transactional
    public BookResponse update(Long id, BookUpdateRequest request) {
        Book book = findBookOrThrow(id);
        String newIsbn = request.getIsbn();
        if (newIsbn != null && !newIsbn.equals(book.getIsbn())) {
            validateIsbnUniqueness(newIsbn);
        }
        bookMapper.updateEntity(request, book);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        bookRepository.deleteById(id);
    }

    public Page<BookResponse> search(String q, String category, Pageable pageable) {
        if (category != null) {
            return bookRepository
                    .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseAndCategory(q, q, category, pageable)
                    .map(bookMapper::toResponse);
        }
        return bookRepository
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(q, q, pageable)
                .map(bookMapper::toResponse);
    }

    @Transactional
    public BookResponse updateCoverPath(Long id, String coverPath) {
        Book book = findBookOrThrow(id);
        book.setCoverPath(coverPath);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    private Book findBookOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    private void validateIsbnUniqueness(String isbn) {
        if (isbn != null && !isbn.isBlank() && bookRepository.existsByIsbn(isbn)) {
            throw new IsbnAlreadyExistsException(isbn);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/service/BookService.java
git commit -m "feat: add BookService with CRUD and search"
```

#### Task 2.3: Write BookService unit tests

**Files:**
- Create: `src/test/java/com/internship/bookverse/service/BookServiceTest.java`

**Interfaces:**
- Consumes: `BookService`, `BookRepository` (mocked), `BookMapper` (real, generated by MapStruct)

- [ ] **Step 1: Create test class with setup**

```java
package com.internship.bookverse.service;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.exception.IsbnAlreadyExistsException;
import com.internship.bookverse.mapper.BookMapperImpl;
import com.internship.bookverse.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    private BookService bookService;

    private Book book;

    @BeforeEach
    void setUp() {
        BookMapperImpl mapper = new BookMapperImpl();
        bookService = new BookService(bookRepository, mapper);

        book = Book.builder()
                .id(1L)
                .title("Spring Boot in Action")
                .author("Craig Walls")
                .isbn("978-1617292545")
                .year(2016)
                .category("Technology")
                .rating(4.5)
                .description("A comprehensive guide to Spring Boot")
                .build();
    }

    @Test
    void getById_shouldReturnBookResponse_whenBookExists() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookResponse result = bookService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Spring Boot in Action");
        assertThat(result.getAuthor()).isEqualTo("Craig Walls");
    }

    @Test
    void getById_shouldThrowBookNotFoundException_whenBookDoesNotExist() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getById(99L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_shouldReturnBookResponse_whenRequestValid() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("New Book")
                .author("New Author")
                .isbn("978-1234567890")
                .build();

        when(bookRepository.existsByIsbn("978-1234567890")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookResponse result = bookService.create(request);

        assertThat(result.getTitle()).isEqualTo("New Book");
        assertThat(result.getAuthor()).isEqualTo("New Author");
    }

    @Test
    void create_shouldThrowIsbnAlreadyExistsException_whenIsbnDuplicate() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("New Book")
                .author("New Author")
                .isbn("978-1234567890")
                .build();

        when(bookRepository.existsByIsbn("978-1234567890")).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(request))
                .isInstanceOf(IsbnAlreadyExistsException.class)
                .hasMessageContaining("978-1234567890");
    }

    @Test
    void create_shouldNotThrow_whenIsbnIsNull() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("Book Without ISBN")
                .author("Author")
                .build();

        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookResponse result = bookService.create(request);

        assertThat(result.getIsbn()).isNull();
    }

    @Test
    void update_shouldReturnUpdatedBookResponse_whenBookExists() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .isbn("978-1617292545") // same ISBN as existing
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.update(1L, request);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getAuthor()).isEqualTo("Updated Author");
    }

    @Test
    void update_shouldThrow_whenNewIsbnAlreadyExistsOnAnotherBook() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .isbn("978-NEW-ISBN")
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbn("978-NEW-ISBN")).thenReturn(true);

        assertThatThrownBy(() -> bookService.update(1L, request))
                .isInstanceOf(IsbnAlreadyExistsException.class);
    }

    @Test
    void delete_shouldDelete_whenBookExists() {
        when(bookRepository.existsById(1L)).thenReturn(true);

        bookService.delete(1L);

        verify(bookRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowBookNotFoundException_whenBookDoesNotExist() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> bookService.delete(99L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void search_shouldReturnMatchingBooks() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
                any(), any(), any())).thenReturn(page);

        Page<BookResponse> result = bookService.search("Spring", null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Spring Boot in Action");
    }

    @Test
    void search_shouldFilterByCategory_whenCategoryProvided() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseAndCategory(
                any(), any(), any(), any())).thenReturn(page);

        Page<BookResponse> result = bookService.search("Spring", "Technology", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run all tests**

```bash
mvn test
```

Expected: All tests pass. 10 tests total.

- [ ] **Step 3: Check test coverage (service layer)**

```bash
mvn jacoco:report
```

Or manually verify: every public method in BookService is covered by at least one test.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/internship/bookverse/service/BookServiceTest.java
git commit -m "test: add BookService unit tests"
```

### Phase 2 Review Gate

Run: `mvn test`
Checklist:
- [ ] All tests pass: 10/10
- [ ] Service layer coverage ≥ 80% (all public methods tested)
- [ ] Business rules verified: ISBN uniqueness, not-found throws, soft delete

Write review results to `docs/superpowers/reviews/phase-2-review.md`.

---

### Phase 3: Controller Layer

**Goal:** BookController with all 7 endpoints, MockMvc integration tests.

#### Task 3.1: Create BookController

**Files:**
- Create: `src/main/java/com/internship/bookverse/controller/BookController.java`

**Interfaces:**
- Consumes: `BookService`
- Produces: REST endpoints per spec

- [ ] **Step 1: Create BookController**

```java
package com.internship.bookverse.controller;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public ResponseEntity<Page<BookResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(bookService.getAll(pageable, category, year));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> create(
            @RequestPart("book") @Valid BookCreateRequest request,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        BookResponse response = bookService.create(request);
        // Image upload handled in Phase 4
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid BookUpdateRequest request) {
        return ResponseEntity.ok(bookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<BookResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(bookService.search(q, category, pageable));
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<?> getCover(
            @PathVariable Long id,
            @RequestParam(defaultValue = "large") String size) {
        // Image serve handled in Phase 4
        return ResponseEntity.notFound().build();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/controller/BookController.java
git commit -m "feat: add BookController with all endpoints"
```

#### Task 3.2: Write BookController integration tests

**Files:**
- Create: `src/test/java/com/internship/bookverse/controller/BookControllerTest.java`

- [ ] **Step 1: Create controller test**

```java
package com.internship.bookverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.exception.GlobalExceptionHandler;
import com.internship.bookverse.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
@Import(GlobalExceptionHandler.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    private BookResponse bookResponse;

    @BeforeEach
    void setUp() {
        bookResponse = BookResponse.builder()
                .id(1L)
                .title("Spring Boot in Action")
                .author("Craig Walls")
                .isbn("978-1617292545")
                .year(2016)
                .category("Technology")
                .rating(4.5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAll_shouldReturnPageOfBooks() throws Exception {
        when(bookService.getAll(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bookResponse)));

        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot in Action"))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getById_shouldReturnBook() throws Exception {
        when(bookService.getById(1L)).thenReturn(bookResponse);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"))
                .andExpect(jsonPath("$.author").value("Craig Walls"));
    }

    @Test
    void getById_shouldReturn404_whenBookNotFound() throws Exception {
        when(bookService.getById(99L)).thenThrow(new BookNotFoundException(99L));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Book not found with id: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/books/99"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        String bookJson = objectMapper.writeValueAsString(
                BookCreateRequest.builder()
                        .title("New Book")
                        .author("New Author")
                        .build());
        when(bookService.create(any())).thenReturn(bookResponse);

        MockMultipartFile bookPart = new MockMultipartFile(
                "book", "", "application/json", bookJson.getBytes());

        mockMvc.perform(multipart("/api/books")
                        .file(bookPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
    }

    @Test
    void create_shouldReturn400_whenValidationFails() throws Exception {
        String bookJson = objectMapper.writeValueAsString(
                BookCreateRequest.builder()
                        .title("")  // blank
                        .author("") // blank
                        .build());

        MockMultipartFile bookPart = new MockMultipartFile(
                "book", "", "application/json", bookJson.getBytes());

        mockMvc.perform(multipart("/api/books")
                        .file(bookPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_shouldReturn200() throws Exception {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .build();
        when(bookService.update(eq(1L), any())).thenReturn(bookResponse);

        mockMvc.perform(put("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());

        verify(bookService).delete(1L);
    }

    @Test
    void search_shouldReturnPage() throws Exception {
        when(bookService.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bookResponse)));

        mockMvc.perform(get("/api/books/search")
                        .param("q", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot in Action"));
    }
}
```

- [ ] **Step 2: Run all tests**

```bash
mvn test
```

Expected: All controller + service tests pass. 18 tests total.

- [ ] **Step 3: Start application and manually test with curl**

```bash
mvn spring-boot:run
```

Test with:
```bash
# List books
curl http://localhost:8080/api/books

# Create a book
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Book","author":"Test Author"}'

# Get by id
curl http://localhost:8080/api/books/1

# Search
curl "http://localhost:8080/api/books/search?q=Test"
```

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/internship/bookverse/controller/BookControllerTest.java
git commit -m "test: add BookController integration tests"
```

### Phase 3 Review Gate

Run: `mvn test`
Checklist:
- [ ] All 18 tests pass (10 service + 8 controller)
- [ ] Manual curl verification: CRUD + search work correctly
- [ ] Error responses return proper format (404, 400)
- [ ] POST multipart endpoint declared (consumes MULTIPART_FORM_DATA)
- [ ] Cover endpoint returns 404 placeholder (Phase 4 will implement)

Write review results to `docs/superpowers/reviews/phase-3-review.md`.

---

### Phase 4: Image Processing

**Goal:** Image upload integrated with POST, cover serve with HTTP caching, Thumbnailator resize + WebP conversion.

#### Task 4.1: Create ImageService

**Files:**
- Create: `src/main/java/com/internship/bookverse/service/ImageService.java`

**Interfaces:**
- Consumes: (no dependencies — pure file I/O + Thumbnailator)
- Produces: `upload(MultipartFile, Long) → String coverPath`, `serve(String coverPath, String size) → ResponseEntity<Resource>`

- [ ] **Step 1: Create ImageService**

```java
package com.internship.bookverse.service;

import com.internship.bookverse.exception.InvalidImageFormatException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ImageService {

    private static final Set<String> ALLOWED_FORMATS = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int MEDIUM_WIDTH = 500;
    private static final int LARGE_WIDTH = 1200;

    @Value("${app.upload.dir:uploads/covers}")
    private String uploadDir;

    public String upload(MultipartFile file, Long bookId) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateFormat(file);

        try {
            String datePath = LocalDate.now().toString().replace("-", "/");
            Path baseDir = Paths.get(uploadDir, datePath);
            Files.createDirectories(baseDir);

            // Resize and convert to WebP
            resizeAndSave(file, baseDir, bookId, THUMBNAIL_WIDTH, "thumb");
            resizeAndSave(file, baseDir, bookId, MEDIUM_WIDTH, "medium");
            resizeAndSave(file, baseDir, bookId, LARGE_WIDTH, "large");

            return baseDir.resolve(String.valueOf(bookId)).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("Failed to process image for book {}", bookId, e);
            throw new RuntimeException("Failed to process image", e);
        }
    }

    public ResponseEntity<Resource> serve(String coverPath, String size) {
        if (coverPath == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(coverPath + "-" + size + ".webp");
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .body(resource);
    }

    private void validateFormat(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FORMATS.contains(contentType)) {
            throw new InvalidImageFormatException(
                    "Invalid image format: " + contentType + ". Allowed: JPG, PNG, WebP");
        }
    }

    private void resizeAndSave(MultipartFile file, Path baseDir, Long bookId, int width, String sizeLabel)
            throws IOException {
        String filename = bookId + "-" + sizeLabel + ".webp";
        Path outputPath = baseDir.resolve(filename);

        Thumbnails.of(file.getInputStream())
                .width(width)
                .outputFormat("webp")
                .toFile(outputPath.toFile());

        log.debug("Saved cover image: {}", outputPath);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/service/ImageService.java
git commit -m "feat: add ImageService with resize and WebP conversion"
```

#### Task 4.2: Wire ImageService into BookController

**Files:**
- Modify: `src/main/java/com/internship/bookverse/controller/BookController.java`

- [ ] **Step 1: Update BookController to call ImageService**

Inject `ImageService` and update `create` and `getCover` methods:

```java
private final ImageService imageService;

// In create method, after bookService.create(request):
if (cover != null && !cover.isEmpty()) {
    String coverPath = imageService.upload(cover, response.getId());
    response = bookService.updateCoverPath(response.getId(), coverPath);
}

// In getCover method:
BookResponse book = bookService.getById(id);
return imageService.serve(book.getCoverPath(), size);
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/controller/BookController.java
git commit -m "feat: wire ImageService into BookController"
```

#### Task 4.3: Write ImageService unit tests

**Files:**
- Create: `src/test/java/com/internship/bookverse/service/ImageServiceTest.java`

- [ ] **Step 1: Create ImageService test**

```java
package com.internship.bookverse.service;

import com.internship.bookverse.exception.InvalidImageFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageServiceTest {

    private ImageService imageService;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.tempDir = tempDir;
        imageService = new ImageService();
        // Set uploadDir via reflection for testing
        var field = ImageService.class.getDeclaredField("uploadDir");
        field.setAccessible(true);
        field.set(imageService, tempDir.toString());
    }

    @Test
    void upload_shouldReturnNull_whenFileIsNull() {
        String result = imageService.upload(null, 1L);
        assertThat(result).isNull();
    }

    @Test
    void upload_shouldThrow_whenInvalidFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "cover", "test.txt", "text/plain", "fake content".getBytes());

        assertThatThrownBy(() -> imageService.upload(file, 1L))
                .isInstanceOf(InvalidImageFormatException.class)
                .hasMessageContaining("Invalid image format");
    }

    @Test
    void upload_shouldSaveThreeFiles_whenValidImage() {
        // Create a minimal valid PNG (1x1 pixel)
        byte[] pngBytes = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
            0x08, (byte) 0xD7, 0x63, 0x68, 0x60, 0x60, 0x60, 0x00,
            0x00, 0x00, 0x04, 0x00, 0x01, 0x27, 0x34, 0x0A, 0x20,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };

        MockMultipartFile file = new MockMultipartFile(
                "cover", "test.png", "image/png", pngBytes);

        String coverPath = imageService.upload(file, 1L);

        assertThat(coverPath).isNotNull();
        assertThat(coverPath).contains("1");
        assertThat(Files.exists(Paths.get(coverPath + "-thumb.webp"))).isTrue();
        assertThat(Files.exists(Paths.get(coverPath + "-medium.webp"))).isTrue();
        assertThat(Files.exists(Paths.get(coverPath + "-large.webp"))).isTrue();
    }

    @Test
    void serve_shouldReturn404_whenCoverPathIsNull() {
        ResponseEntity<Resource> response = imageService.serve(null, "large");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void serve_shouldReturn404_whenFileNotFound() {
        ResponseEntity<Resource> response = imageService.serve("/nonexistent/path/1", "large");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
```

Note: Place a small PNG test image at `src/test/resources/test-image.png` (any small valid PNG).

- [ ] **Step 2: Add test image**

Create `src/test/resources/test-image.png` — use any small PNG file (1x1 pixel minimum).

- [ ] **Step 3: Run tests**

```bash
mvn test
```

Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/internship/bookverse/service/ImageServiceTest.java src/test/resources/test-image.png
git commit -m "test: add ImageService unit tests"
```

### Phase 4 Review Gate

Run: `mvn test && mvn spring-boot:run` (manual test)
Checklist:
- [ ] All tests pass (18 controller/service + ImageService tests)
- [ ] Manual test: POST with cover image via Postman/curl
- [ ] Manual test: Verify 3 files created in `uploads/covers/yyyy/MM/`
- [ ] Manual test: All 3 files are valid WebP, correct sizes
- [ ] Manual test: GET /api/books/{id}/cover?size=thumb returns small image
- [ ] Manual test: Cache-Control header present with max-age=604800

Write review results to `docs/superpowers/reviews/phase-4-review.md`.

---

### Phase 5: Bulk Import

**Goal:** BulkImportService + POST /api/books/bulk endpoint, Excel/CSV parsing.

#### Task 5.1: Create BulkImportService

**Files:**
- Create: `src/main/java/com/internship/bookverse/service/BulkImportService.java`

**Interfaces:**
- Consumes: `BookRepository`
- Produces: `importBooks(MultipartFile) → BulkImportResult`

- [ ] **Step 1: Create BulkImportService**

```java
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
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/service/BulkImportService.java
git commit -m "feat: add BulkImportService for Excel/CSV import"
```

#### Task 5.2: Add bulk import endpoint to BookController

**Files:**
- Modify: `src/main/java/com/internship/bookverse/controller/BookController.java`

- [ ] **Step 1: Add bulk endpoint**

Inject `BulkImportService` and add:

```java
private final BulkImportService bulkImportService;

@PostMapping("/bulk")
public ResponseEntity<BulkImportResult> bulkImport(
        @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(bulkImportService.importBooks(file));
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/controller/BookController.java
git commit -m "feat: add bulk import endpoint to BookController"
```

#### Task 5.3: Write BulkImportService unit tests

**Files:**
- Create: `src/test/java/com/internship/bookverse/service/BulkImportServiceTest.java`

- [ ] **Step 1: Create BulkImportService test**

```java
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock
    private BookRepository bookRepository;

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
        verify(bookRepository, times(3)).save(any(Book.class));
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
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/csv", "data".getBytes());

        BulkImportResult result = bulkImportService.importBooks(file);

        assertThat(result.getTotalRows()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/internship/bookverse/service/BulkImportServiceTest.java
git commit -m "test: add BulkImportService unit tests"
```

### Phase 5 Review Gate

Run: `mvn test`
Checklist:
- [ ] All tests pass
- [ ] CSV import with valid rows: all saved
- [ ] CSV with invalid rows: skipped with error report
- [ ] Unsupported format: throws correctly
- [ ] Manual test: POST /api/books/bulk with CSV file returns summary

Write review results to `docs/superpowers/reviews/phase-5-review.md`.

---

### Phase 6: Caching

**Goal:** Caffeine cache for API responses, HTTP cache for images, cache eviction on mutations.

#### Task 6.1: Create CacheConfig

**Files:**
- Create: `src/main/java/com/internship/bookverse/config/CacheConfig.java`

**Interfaces:**
- Produces: Caffeine `CacheManager` with cache names "books", "bookById", "bookSearch"

- [ ] **Step 1: Create CacheConfig**

```java
package com.internship.bookverse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache("books",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());
        cacheManager.registerCustomCache("bookById",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());
        cacheManager.registerCustomCache("bookSearch",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());
        return cacheManager;
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/config/CacheConfig.java
git commit -m "feat: add Caffeine cache configuration"
```

#### Task 6.2: Add @Cacheable and @CacheEvict to BookService

**Files:**
- Modify: `src/main/java/com/internship/bookverse/service/BookService.java`

- [ ] **Step 1: Add caching annotations**

Add to `getAll`:
```java
@Cacheable(value = "books", key = "{#pageable.pageNumber, #pageable.pageSize, #category, #year}")
```

Add to `getById`:
```java
@Cacheable(value = "bookById", key = "#id")
```

Add to `search`:
```java
@Cacheable(value = "bookSearch", key = "{#q, #category, #pageable.pageNumber, #pageable.pageSize}")
```

Add to `create`:
```java
@CacheEvict(value = {"books", "bookSearch"}, allEntries = true)
```

Add to `update`:
```java
@CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
```

Add to `delete`:
```java
@CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/service/BookService.java
git commit -m "feat: add caching annotations to BookService"
```

### Phase 6 Review Gate

Run: `mvn test && mvn spring-boot:run`

Checklist:
- [ ] All tests pass
- [ ] Make 2 identical GET /api/books requests → second is faster (cache hit)
- [ ] GET /api/books/{id} → second call faster (5-min cache)
- [ ] POST/PUT/DELETE evicts relevant caches
- [ ] Image serve: Cache-Control header present, ETag works
- [ ] Enable `logging.level.com.github.benmanes.caffeine=DEBUG` to verify cache hits/misses

Write review results to `docs/superpowers/reviews/phase-6-review.md`.

---

### Phase 7: Documentation & Final Polish

**Goal:** Swagger UI with all endpoints documented, final review, application runs clean.

#### Task 7.1: Create OpenApiConfig

**Files:**
- Create: `src/main/java/com/internship/bookverse/config/OpenApiConfig.java`

- [ ] **Step 1: Create OpenApiConfig**

```java
package com.internship.bookverse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookVerseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BookVerse API")
                        .description("E-Book Management System — CRUD, cover image processing, full-text search, bulk import")
                        .version("1.0.0"));
    }
}
```

- [ ] **Step 2: Verify Swagger UI**

Start app, visit http://localhost:8080/swagger-ui.html. Verify all 8 endpoints appear (7 book + 1 bulk).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/bookverse/config/OpenApiConfig.java
git commit -m "feat: add OpenAPI/Swagger configuration"
```

#### Task 7.2: Final review and cleanup

- [ ] **Step 1: Run full test suite**

```bash
mvn clean test
```

Expected: All tests pass, BUILD SUCCESS.

- [ ] **Step 2: Run the application end-to-end**

Test all endpoints:
- CRUD flow: create → get → update → delete → get (404)
- Search: create with title "Unique Book", search "Unique" → found
- Cover: upload image → verify 3 files → GET cover → 200
- Bulk: POST with CSV → verify summary

- [ ] **Step 3: Check compilation warnings**

```bash
mvn compile -Xlint:all
```

Expected: No warnings (or only known, acceptable ones).

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "docs: final review and polish"
```

### Phase 7 Review Gate

Checklist:
- [ ] `mvn clean test` — all tests pass
- [ ] Swagger UI shows all endpoints with correct schemas
- [ ] End-to-end manual testing passes all flows
- [ ] No compilation warnings
- [ ] Application starts in < 10 seconds
- [ ] `target/` directory not committed (verified by .gitignore)

Write review results to `docs/superpowers/reviews/phase-7-review.md`.

---

## Implementation Order

```
Phase 1 (Foundation) → Phase 2 (Service) → Phase 3 (Controller) → Phase 4 (Images) → Phase 5 (Bulk Import) → Phase 6 (Caching) → Phase 7 (Docs)
```

Each phase depends on the previous one. Do not skip phases or reorder them.

## File Creation Summary

| Phase | Files Created | Files Modified |
|-------|--------------|----------------|
| 1 | `pom.xml`, `application.yml`, `Book.java`, `BookRepository.java`, 6 DTOs, `GlobalExceptionHandler.java`, 3 exception classes, `BookVerseApplication.java` | — |
| 2 | `BookMapper.java`, `BookService.java`, `BookServiceTest.java` | — |
| 3 | `BookController.java`, `BookControllerTest.java` | — |
| 4 | `ImageService.java`, `ImageServiceTest.java`, `test-image.png` | `BookController.java` |
| 5 | `BulkImportService.java`, `BulkImportServiceTest.java` | `BookController.java` |
| 6 | `CacheConfig.java` | `BookService.java` |
| 7 | `OpenApiConfig.java` | — |
