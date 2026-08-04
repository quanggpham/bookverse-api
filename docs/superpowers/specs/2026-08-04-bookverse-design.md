# BookVerse — System Design

**Date:** 2026-08-04
**Stack:** Spring Boot 3.3.2, Java 21, Maven

## 1. Design Principles

- **Needs-driven structure** — start simple, extract only when genuine pain emerges (file too large, shared logic, distinct concerns)
- **Single BookController** — all `/api/books/*` in one class until it demonstrably needs splitting
- **Package-by-layer** — controller/service/repository/entity/dto/mapper/config; migrate to package-by-feature only if the entity count grows
- **No speculative abstractions** — no interfaces for single-implementation services, no factory patterns for simple object creation

## 2. Package Structure

```
com.internship.bookverse/
├── BookVerseApplication.java
├── controller/
│   └── BookController.java          # All /api/books/* endpoints
├── service/
│   ├── BookService.java             # CRUD + search business logic
│   ├── ImageService.java            # Resize + WebP conversion + file I/O
│   └── BulkImportService.java       # Excel/CSV parsing → create books
├── repository/
│   └── BookRepository.java          # JpaRepository<Book, Long>
├── entity/
│   └── Book.java                    # JPA entity
├── dto/
│   ├── request/
│   │   ├── BookCreateRequest.java   # POST body with @Valid annotations
│   │   └── BookUpdateRequest.java   # PUT body
│   ├── response/
│   │   ├── BookResponse.java        # Book DTO returned to client
│   │   └── BulkImportResult.java    # Import summary
│   └── ErrorResponse.java           # code, message, timestamp, path
├── mapper/
│   └── BookMapper.java              # MapStruct: DTO ↔ Entity
├── config/
│   ├── CacheConfig.java             # Caffeine configuration
│   └── OpenApiConfig.java           # Swagger/OpenAPI configuration
└── exception/
    └── GlobalExceptionHandler.java  # @RestControllerAdvice
```

## 3. API Endpoints

### CRUD

| Method | Endpoint | Request Body | Response | Status |
|--------|----------|-------------|----------|--------|
| `GET` | `/api/books` | Query: `page`, `size`, `sort`, `category`, `year` | `Page<BookResponse>` | 200 |
| `GET` | `/api/books/{id}` | — | `BookResponse` | 200 / 404 |
| `POST` | `/api/books` | Multipart: `book` (JSON) + `cover` (file, optional) | `BookResponse` | 201 / 400 |
| `PUT` | `/api/books/{id}` | JSON: `BookUpdateRequest` | `BookResponse` | 200 / 404 / 400 |
| `DELETE` | `/api/books/{id}` | — | 204 No Content | 204 / 404 |

### Search & Cover

| Method | Endpoint | Params | Response | Status |
|--------|----------|--------|----------|--------|
| `GET` | `/api/books/search` | `q`, `category`, `page`, `size` | `Page<BookResponse>` | 200 |
| `GET` | `/api/books/{id}/cover` | `size` (thumb/medium/large, default: large) | `image/webp` binary | 200 / 404 |

### Bulk Import

| Method | Endpoint | Request Body | Response | Status |
|--------|----------|-------------|----------|--------|
| `POST` | `/api/books/bulk` | Multipart: `file` (.xlsx/.csv) | `BulkImportResult` | 200 / 400 |

### Notes

- POST uses multipart because it carries both JSON metadata (`book` field) and an optional file (`cover` field)
- PUT is JSON-only (no cover upload); cover image is set once at creation
- DELETE is **soft delete** — sets `deleted = true`, returns 204
- Success responses return the DTO directly (no wrapper envelope)
- Error responses use `ErrorResponse` DTO (see Section 6)

## 4. Entity Design

### Book Entity

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| title | String | @NotBlank |
| author | String | @NotBlank |
| isbn | String | @Unique |
| year | Integer | |
| category | String | |
| rating | Double | |
| description | String | @Lob (TEXT/LONGVARCHAR) |
| coverPath | String | Path to cover directory (size suffix appended at serve time) |
| deleted | Boolean | default false |
| createdAt | LocalDateTime | auto-set via @PrePersist |
| updatedAt | LocalDateTime | auto-set via @PreUpdate |

### Indexes

```sql
CREATE INDEX idx_book_title_author ON book (title, author);
```

### Soft Delete

```java
@SQLDelete(sql = "UPDATE book SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
```

All SELECT queries implicitly filter `deleted = false`. DELETE calls via `bookRepository.deleteById(id)` execute UPDATE instead.

### Audit

`@PrePersist` sets `createdAt`, `@PreUpdate` sets `updatedAt` — no JPA Auditing or Spring Data Envers needed.

## 5. Image Processing

### Upload (`ImageService.upload`)

1. Validate format: JPG, PNG, WebP only
2. Validate file size: max 5MB (`spring.servlet.multipart.max-file-size`)
3. Generate storage path: `uploads/covers/yyyy/MM/{bookId}`
4. Resize + convert via Thumbnailator:
   - thumbnail: width 200px → `{bookId}-thumb.webp`
   - medium: width 500px → `{bookId}-medium.webp`
   - large: width 1200px → `{bookId}-large.webp`
5. Return base path for storage in `Book.coverPath`

### Serve (`ImageService.serve`)

1. Build full path: `{coverPath}-{size}.webp`
2. Read file from disk as `Resource`
3. Set response headers: `Content-Type: image/webp`, `Cache-Control: public, max-age=604800, immutable`
4. Generate `ETag` from file metadata (lastModified + size)
5. Return `304 Not Modified` on matching `If-None-Match`

### Cleanup

On soft delete: images kept on disk (restorable). Hard-delete logic deferred until needed.

## 6. Caching

| Layer | Target | Provider | TTL | Eviction |
|-------|--------|----------|-----|----------|
| In-memory | GET `/api/books` (list) | Caffeine | 2 min | On POST/PUT/DELETE of books |
| In-memory | GET `/api/books/search` | Caffeine | 2 min | On POST/PUT/DELETE of books |
| In-memory | GET `/api/books/{id}` | Caffeine | 5 min | On PUT/DELETE of that book |
| HTTP | GET `/api/books/{id}/cover` | Browser/CDN | 7 days | ETag-based conditional request |

Caffeine config: `maximumSize=500`, `expireAfterWrite`

## 7. Search

- Full-text search via `LIKE` on `title` and `author` columns
- BTREE index on `(title, author)` for query performance
- Same approach on H2 (dev) and PostgreSQL (prod) — no database-specific SQL

## 8. Bulk Import

- Synchronous processing (file parsed within request lifecycle)
- Supported formats: `.xlsx` (Apache POI), `.csv` (BufferedReader / Apache POI)
- Metadata only — no image upload in bulk import
- Row-by-row: valid rows create books (batch persist), invalid rows are skipped with reason recorded
- Response: `BulkImportResult { totalRows, successCount, failedCount, errors[] }`

## 9. Validation & Error Handling

### Validation Layers

1. **DTO** — Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@ISBN`)
2. **Entity** — JPA column constraints (`nullable`, `unique`)
3. **Service** — business rules (ISBN uniqueness check, valid category enum)
4. **ImageService** — file format, size limits

Only create custom validators when Jakarta built-ins are insufficient.

### Global Exception Handler

Single `@RestControllerAdvice` class catching all exceptions and returning `ErrorResponse`.

### Error Response Format

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found with id: 99",
  "timestamp": "2026-08-04T15:30:00",
  "path": "/api/books/99"
}
```

### Error Codes

| Exception | HTTP | Code |
|-----------|------|------|
| Book not found | 404 | `BOOK_NOT_FOUND` |
| Validation failure | 400 | `VALIDATION_ERROR` (includes `details[]` per field) |
| ISBN duplicate | 409 | `ISBN_ALREADY_EXISTS` |
| Invalid image format | 400 | `INVALID_IMAGE_FORMAT` |
| File too large | 413 | `FILE_TOO_LARGE` |
| Internal error | 500 | `INTERNAL_ERROR` |

Validation errors include per-field details:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [
    {"field": "title", "message": "must not be blank"},
    {"field": "year", "message": "must be >= 1000"}
  ],
  "timestamp": "...",
  "path": "..."
}
```

## 10. Database Configuration

- **Default:** H2 in-memory (zero-config dev)
- **Production:** PostgreSQL, configured via environment variables
- `application.yml` defaults to H2; PostgreSQL activated when `SPRING_DATASOURCE_URL` is set
- No profile-based config splitting — env var override is sufficient
- `spring.jpa.hibernate.ddl-auto=update` for both environments

### Connection

| Environment | Config |
|-------------|--------|
| Dev | H2 in-memory, H2 console enabled |
| Prod | PostgreSQL via `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |

## 11. Testing

- **Unit tests:** BookService, ImageService, BulkImportService — JUnit 5 + Mockito
- **Repository tests:** `@DataJpaTest` with H2
- **Controller tests:** `@WebMvcTest` with MockMvc
- **Coverage target:** Service layer only (as required by assignment)

## 12. Tech Stack Summary

| Dependency | Version | Purpose |
|------------|---------|---------|
| spring-boot-starter-web | 3.3.2 | REST API |
| spring-boot-starter-data-jpa | 3.3.2 | ORM + pagination + sort |
| spring-boot-starter-validation | 3.3.2 | Jakarta Validation |
| spring-boot-starter-cache | 3.3.2 | Cache abstraction |
| H2 | — | Dev database |
| PostgreSQL | — | Prod database |
| MapStruct | 1.5.5 | DTO ↔ Entity mapping |
| Springdoc OpenAPI | 2.6.0 | Swagger UI |
| Thumbnailator | 0.4.20 | Image resize + WebP |
| Apache POI | 5.3.0 | Excel/CSV parsing |
| Lombok | — | Boilerplate reduction |
| Caffeine | (via starter-cache) | In-memory cache — needs explicit dependency in pom.xml |
