# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BookVerse is a Spring Boot 3.3 e-book management REST API with cover image processing (auto-resize to 3 sizes + WebP conversion), full-text search, pagination, and caching. Java 21, Maven build.

## Build & Run Commands

```bash
# Build
mvn clean package

# Run dev (H2 in-memory DB, no external dependencies)
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=BookServiceTest

# Run a single test method
mvn test -Dtest=BookServiceTest#methodName
```

The app starts on `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Architecture

The project follows **Layered Architecture** — start simple, let needs drive structure:

```
controller/  →  service/  →  repository/ (Spring Data JPA)
     ↕              ↕
model/dto/    model/entity/
     ↕
mapper/ (MapStruct — DTO ↔ Entity)
```

**Package layout** (`com.internship.bookverse`):
- `controller/` — REST controllers. Start with a single `BookController` for all `/api/books/*` endpoints (CRUD + search + cover serve). Only split into separate controllers when the class genuinely grows too large.
- `service/` — Business logic. `BookService` handles CRUD + search. Extract `ImageService` only when image processing logic becomes non-trivial (resize + WebP conversion + file I/O). `BulkImportService` for CSV/Excel import.
- `repository/` — `BookRepository` extends `JpaRepository`
- `model/entity/` — `Book` JPA entity
- `model/dto/` — `BookDTO` with `@Valid` annotations for input validation
- `mapper/` — `BookMapper` (MapStruct interface)
- `config/` — `CacheConfig`, `SwaggerConfig`
- `validation/` — Custom validators if Jakarta built-ins are insufficient

**Design principle:** Don't pre-split controllers or services. A single `BookController` with 7 endpoints is simpler and perfectly fine. Extract only when there's actual pain — a file that's hard to navigate or logic that's reused elsewhere.

**Key dependencies from pom.xml:**
- Spring Boot Starters: Web, Data JPA, Validation, Cache
- Databases: H2 (dev runtime), PostgreSQL (prod runtime)
- MapStruct 1.5.5 — compile-time DTO↔Entity mapping
- Springdoc OpenAPI 2.6.0 — Swagger UI
- Thumbnailator 0.4.20 — image resize + WebP conversion
- Apache POI 5.3.0 — Excel/CSV parsing for bulk upload
- Lombok — boilerplate reduction

## API Endpoints (per assignment spec)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/books?page=&size=&sort=` | Paginated list with filtering/sorting |
| GET | `/api/books/{id}` | Book details |
| POST | `/api/books` | Create book (with optional cover image upload) |
| PUT | `/api/books/{id}` | Update book |
| DELETE | `/api/books/{id}` | Delete book |
| GET | `/api/books/search?q=&category=` | Full-text search by title/author |
| GET | `/api/books/{id}/cover?size=large` | Serve cover image file |

## Image Processing Rules

- Accepted formats: JPG, PNG, WebP
- Auto-generate 3 sizes: thumbnail (200px), medium (500px), large (1200px)
- Convert to WebP format on upload
- Store to: `uploads/covers/yyyy/MM/id-size.webp`
- Use Thumbnailator for resize + WebP conversion

## Conventions to Follow

- MapStruct interfaces for DTO↔Entity mapping (not manual mapping)
- `@Valid` / Jakarta Validation annotations on DTOs for input validation
- Caffeine cache (via `spring-boot-starter-cache`) for images and frequently-hit APIs
- Service-layer unit tests (JUnit 5 + Mockito, via `spring-boot-starter-test`)
- Java naming conventions and Javadoc comments as required by the assignment
