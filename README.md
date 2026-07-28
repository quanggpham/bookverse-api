# BookVerse 📚 — E-Book Management Platform

[![Java](https://img.shields.io/badge/Java-17-%23ED8B00)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1)](https://www.postgresql.org/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI-85EA2D)](https://swagger.io/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED)](https://www.docker.com/)

A production-ready REST API for managing electronic books — with full CRUD, intelligent cover image processing (auto-resize to 3 sizes + WebP conversion), full-text search, pagination, and enterprise-grade architecture.

> **Stack:** Spring Boot 3 + Spring Data JPA + PostgreSQL/H2 + MapStruct + Thumbnailator + OpenAPI

## ✨ Features

### 📖 Book Management (CRUD)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/books` | List with pagination, sorting, filtering |
| `GET` | `/api/books/{id}` | Book details |
| `POST` | `/api/books` | Create book + optional cover upload |
| `PUT` | `/api/books/{id}` | Update book |
| `DELETE` | `/api/books/{id}` | Delete book |

### 🖼 Smart Cover Image Processing
- **Auto-upload** — Supports JPG, PNG, WebP
- **3 Resolutions** — Thumbnail (200px), Medium (500px), Large (1200px)
- **WebP Conversion** — Automatic format optimization
- **Organized Storage** — `uploads/covers/yyyy/MM/id-size.webp`

### 🔍 Search & Discovery
- **Full-text search** — Search by title and author
- **Filtering** — By category, year
- **Sorting** — By title, year, rating
- **Pagination** — Configurable page size

### 📋 Bulk Operations
- **CSV/Excel Import** — Bulk upload books from spreadsheet
- **Batch Image Upload** — Upload multiple covers at once

### 🏗 Architecture

```
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Controller Layer  │────►│   Service Layer   │────►│  Repository     │
│                     │     │                   │     │  Layer (JPA)    │
│  BookController     │     │  BookService      │     │                 │
│  SearchController   │     │  ImageService     │     │  BookRepository │
│  CoverController    │     │  BulkImportService│     │                 │
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

## 🚀 Getting Started

### Prerequisites

- Java 17+
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

Once running, visit: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Docker

```bash
docker compose up --build
```

## 🧪 Testing

```bash
mvn test
```

## 📁 Project Structure

```
src/
├── main/java/com/internship/bookverse/
│   ├── BookVerseApplication.java       # Entry point
│   ├── controller/
│   │   ├── BookController.java         # CRUD endpoints
│   │   ├── CoverController.java        # Image serve endpoints
│   │   └── SearchController.java       # Search endpoints
│   ├── service/
│   │   ├── BookService.java            # Business logic
│   │   ├── ImageService.java           # Image processing
│   │   └── BulkImportService.java      # CSV/Excel import
│   ├── repository/
│   │   └── BookRepository.java         # JPA data access
│   ├── model/entity/
│   │   └── Book.java                   # JPA entity
│   ├── model/dto/
│   │   └── BookDTO.java                # Data transfer object
│   ├── mapper/
│   │   └── BookMapper.java             # MapStruct mapper
│   ├── config/
│   │   ├── CacheConfig.java            # Caching configuration
│   │   └── SwaggerConfig.java          # OpenAPI configuration
│   └── validation/
│       └── BookValidator.java          # Input validation
└── test/java/com/internship/bookverse/
    └── ...                             # Service layer tests
```

## 🛠 Tech Stack

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
| Docker | Containerization |

---

*Built as part of a backend engineering internship program — demonstrating enterprise Spring Boot development practices.*
