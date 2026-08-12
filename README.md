# BookVerse — E-Book Management Platform

[![Java](https://img.shields.io/badge/Java-21-%23ED8B00)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1)](https://www.postgresql.org/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI-85EA2D)](https://swagger.io/)

A production-ready REST API for managing electronic books — with full CRUD, intelligent cover image processing (auto-resize to 3 sizes + WebP conversion), full-text search, pagination, and enterprise-grade architecture.

> **Stack:** Spring Boot 3 + Spring Data JPA + PostgreSQL/H2 + MapStruct + Thumbnailator + Caffeine Cache + OpenAPI

## Features

### API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/books` | List books with pagination, sorting, category & year filtering |
| `GET` | `/api/books/{id}` | Get book details |
| `POST` | `/api/books` | Create book (supports optional cover image upload) |
| `PUT` | `/api/books/{id}` | Update book details (supports optional cover upload) |
| `DELETE` | `/api/books/{id}` | Soft-delete book (clears ISBN for reuse) |
| `GET` | `/api/books/search` | Full-text search by title/author with filtering |
| `GET` | `/api/books/{id}/cover` | Serve cover image file by size (`thumb`, `medium`, `large`) |
| `PUT` | `/api/books/{id}/cover` | Update cover image for an existing book |
| `POST` | `/api/books/bulk` | Bulk import books from CSV or Excel (`.xlsx`) |
| `GET` | `/api/books/categories` | Get distinct categories with book counts |
| `GET` | `/api/books/years` | Get distinct publication years with book counts |

### Smart Cover Image Processing

- **Auto-upload** — Supports JPG, PNG, WebP
- **3 Resolutions** — Thumbnail (200px), Medium (500px), Large (1200px)
- **WebP Conversion** — Automatic format optimization
- **Organized Storage** — `uploads/covers/yyyy/MM/id-size.webp`

### Search & Discovery

- **Full-text search** — Search by title and author
- **Filtering** — By category, publication year
- **Sorting** — By title, year, rating, createdAt
- **Pagination** — Configurable page number & size

### Bulk Operations

- **CSV/Excel Import** — Bulk upload books from `.csv` or `.xlsx` files
- **Row-level Error Reporting** — Continues import on row errors with detailed error feedback
- **ISBN Collision Guard** — Prevents duplicate ISBNs during bulk import

### Architecture

```
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Controller Layer  │────►│   Service Layer   │────►│  Repository     │
│                     │     │                   │     │  Layer (JPA)    │
│   BookController    │     │  BookService      │     │                 │
│                     │     │  ImageService     │     │  BookRepository │
│                     │     │  BulkImportService│     │                 │
└─────────────────────┘     └───┬───────────────┘     └────────┬────────┘
                                │                              │
                          ┌─────▼─────┐                 ┌──────▼──────┐
                          │  MapStruct │                 │             │
                          │  (DTO↔Entity)               │  H2 (dev)   │
                          └───────────┘                 │  PostgreSQL  │
                                                        │  (prod)      │
                          ┌─────────────────┐           └─────────────┘
                          │  Image Storage    │
                          │  uploads/covers/  │
                          │  yyyy/MM/id-*.webp│
                          └─────────────────┘
```

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16 (optional, H2 for development)

### Build

```bash
mvn clean package
```

### Run (Development — H2 in-memory DB)

```bash
mvn spring-boot:run
```

### Run (Production — PostgreSQL)

```bash
# Set database connection
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bookverse
set SPRING_DATASOURCE_USERNAME=postgres
set SPRING_DATASOURCE_PASSWORD=yourpassword

# Run
java -jar target/bookverse-1.0.0.jar
```

### API Documentation

- **Swagger UI** (interactive): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) once running
- **OpenAPI Specification**: `http://localhost:8080/v3/api-docs`
- **Postman collection** (ready-to-run with sample data): [`postman/README.md`](postman/README.md)
- **Assignment Spec**: [`docs/assignment.md`](docs/assignment.md)

## Testing

```bash
mvn test
```

## Project Structure

```
src/
├── main/java/com/internship/bookverse/
│   ├── BookVerseApplication.java       # Entry point
│   ├── config/
│   │   ├── BookSeeder.java             # Database startup seeder
│   │   ├── CacheConfig.java            # Caffeine cache configuration
│   │   ├── CorsConfig.java             # CORS configuration
│   │   └── OpenApiConfig.java          # OpenAPI / Swagger configuration
│   ├── controller/
│   │   └── BookController.java         # REST endpoints (CRUD, search, cover, bulk)
│   ├── dto/
│   │   ├── ErrorResponse.java          # Standard error DTO
│   │   ├── ValidationErrorResponse.java # Form validation error DTO
│   │   ├── request/                    # BookCreateRequest, BookUpdateRequest
│   │   └── response/                   # BookResponse, BulkImportResult, CategoryCount, YearCount
│   ├── entity/
│   │   └── Book.java                   # JPA entity with soft delete
│   ├── exception/
│   │   └── GlobalExceptionHandler.java # Centralized exception handling
│   ├── mapper/
│   │   └── BookMapper.java             # MapStruct DTO ↔ Entity mapper
│   ├── repository/
│   │   └── BookRepository.java         # Spring Data JPA repository
│   └── service/
│       ├── BookService.java            # Business logic & caching
│       ├── ImageService.java           # Cover image resize & WebP conversion
│       ├── BookCsvParser.java          # CSV parser helper
│       └── BulkImportService.java      # CSV/Excel bulk import service
└── test/java/com/internship/bookverse/
    ├── config/                         # Seeder & image probe tests
    ├── controller/                     # Controller layer tests
    ├── integration/                    # E2E & integration tests
    └── service/                        # Service layer unit tests
```

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Spring Boot 3.3 | Application framework |
| Spring Data JPA | Database access |
| PostgreSQL / H2 | Database |
| MapStruct | DTO ↔ Entity mapping |
| Thumbnailator | Image resize & WebP conversion |
| Springdoc OpenAPI | Swagger documentation |
| Apache POI | CSV/Excel parsing |
| Caffeine | Response caching |

---

*Built as part of a backend engineering internship program — demonstrating enterprise Spring Boot development practices.*