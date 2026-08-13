# Postman Collection — BookVerse API

End-to-end tests for every endpoint, using Postman **variables**, **pre-request scripts** (dynamic data), **test scripts** (schema + value assertions), and **data-driven** iteration.

## Files

| File | Purpose |
|------|---------|
| `bookverse.postman_collection.json` | The collection (import this) |
| `bookverse.local.postman_environment.json` | Local env → `http://localhost:8080` |
| `bookverse.prod.postman_environment.json` | Template for a remote/prod host |
| `sample-books.csv` | 6 clean rows for bulk import |
| `sample-books-dup.csv` | 2 rows with a duplicate ISBN (must be skipped) |
| `sample-books-bad.csv` | 1 bad + 1 good row |
| `sample-cover.png` | Valid 1200×1600 PNG cover image |
| `sample-unsupported.txt` | Not an image — must be rejected |
| `100-books.csv` | Data file for the data-driven create test |

## Quick start

1. **Start the app**
   ```bash
   mvn spring-boot:run
   ```
   H2 in-memory DB seeds ~500 books on startup when empty.

2. **Import** (Postman top-left → Import → folder `postman/`):
   - `bookverse.postman_collection.json`
   - `bookverse.local.postman_environment.json`

3. **Select the environment** (top-right dropdown → *BookVerse - Local*).
   `{{baseUrl}}` now resolves to `http://localhost:8080`.

4. **Fix the multipart file paths** (Postman does not port file paths across machines). For the requests below, click the request, open the `body` tab, hover the file part and choose the matching file under `postman/`:
   - *Cover images → POST /api/books (create WITH cover)* → `cover` part → `sample-cover.png`
   - *Cover images → POST /api/books (create with INVALID cover)* → `cover` part → `sample-unsupported.txt`
   - *Cover images → PUT /api/books/{{coverId}}/cover (replace cover only)* → `cover` part → `sample-cover.png`
   - *Bulk import* → each `file` part → the matching `sample-*.csv`

5. **Run** — either:
   - Whole collection: collection ▸ ▸ **Run collection** (folders execute in order; CRUD uses the book id captured by the create test), **or**
   - Per request: click **Send** one at a time, or run each folder.

## What it covers

**Health** — OpenAPI spec + Swagger UI are served.

**Books CRUD** — paginated list (with `category`/`year` filters), create, `@Valid` validation error (400 `VALIDATION_ERROR`), get by id, **PUT update via multipart** (JSON `book` part + optional `cover` part), hard-delete → 404 `BOOK_NOT_FOUND`, delete missing → 404.

**Filter metadata** — `GET /api/books/categories` (returns `{name, count}`) and `GET /api/books/years` (returns `{year, count}`).

**Search** — title/author search (case-insensitive), category/year-scoped search, blank `q` rejected (400 `VALIDATION_ERROR`).

**Cover images** — multipart create with a valid PNG (server resizes to 3 sizes + WebP), serving `thumb` as `image/webp` (magic bytes `RIFF….WEBP` asserted), rejecting a non-image with 400 `INVALID_IMAGE_FORMAT`, **PUT `/api/books/{id}/cover`** replaces cover (cleans up old files), missing `cover` part → 400 `MISSING_PART`.

**Bulk import** — clean CSV (6/6), duplicate ISBN (reported in `errors` with row number), bad rows (reported with row number), unsupported extension (400 `BAD_REQUEST`), missing file part (400 `MISSING_PART`). Response structure: `{ totalRows, successCount, failedCount, errors[{row, reason}] }`.

**Data-driven: 100 books** — one `POST /api/books` run 100× via the Runner, with `postman/100-books.csv` as the data source. Each iteration asserts the created book echoes its row's ISBN/title/author/year.

## Design notes

- **Variables**: `{{baseUrl}}`, `{{page}}`, `{{size}}` live in the environment; `bookId`, `coverId`, `coverPath`, `isbn`, `title`, `author`, `year`, `category`, `rating`, `description`, `coverIsbn`, `coverTitle`, `invalidCoverIsbn`, `coverOnlyId` live in collection variables and are *captured* by test scripts for later requests in the folder.
- **Dynamic data**: pre-request scripts generate a fresh ISBN/title per run (`Date.now()`-based), so repeated runs never collide with the unique-ISBN constraint.
- **Deterministic bulk tests**: `sample-books-dup.csv` has an *internal* duplicate (both rows in the file), so the assertion `successCount = 1` (fresh DB) holds even if `sample-books.csv` was already imported on an earlier run.
- **Pagination invariant** checked: `numberOfElements === content.length`.
- **Error response contract**: all 400/404/409/413/500 responses include `{ code, message, timestamp, path }`. `VALIDATION_ERROR` additionally includes `{ details: [{field, message}] }`. Bulk import errors include `{ row, reason }`.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| All requests `Could not connect` | App not running, or wrong environment selected (`baseUrl`) |
| Multipart `file` part empty / not sent | File path not selected on that request's body part — re-pick it |
| Create returns 409 `ISBN_ALREADY_EXISTS` | `isbn` not unique — re-run the request (pre-request regenerates it) |
| Cover 400 `INVALID_IMAGE_FORMAT` on a real image | `Content-Type` header not `multipart/form-data`, or the file isn't actually an image |
| PUT `/api/books/{id}` returns 400 `MISSING_PART` | Missing required `book` part — must send multipart with a JSON part named `book` |
| Bulk 400 `BAD_REQUEST` | Wrong file chosen (e.g. `.txt` on a CSV request) |
| `data-driven` fails with `iterationData.get(...)` undefined | Data file not selected in the Runner → Data tab |

## Optional: run via Newman (CLI)

```bash
# if not installed
npm install -g newman

# CRUD folder
newman run postman/bookverse.postman_collection.json \
  -e postman/bookverse.local.postman_environment.json \
  --folder "Books CRUD" \
  --reporters cli,json --reporter-json-export newman-report.json

# Data-driven with the CSV
newman run postman/bookverse.postman_collection.json \
  -e postman/bookverse.local.postman_environment.json \
  -d postman/100-books.csv \
  --folder "Data-driven: 100 books"
```

## API contract summary

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/api/books` | Paginated, filter by `category`, `year`, sort by `title,year,rating,createdAt,updatedAt` |
| GET | `/api/books/{id}` | Single book |
| POST | `/api/books` | Multipart: `book` (JSON) + optional `cover` (file) |
| PUT | `/api/books/{id}` | **Multipart**: `book` (JSON) + optional `cover` (file) |
| DELETE | `/api/books/{id}` | Hard delete + cover cleanup |
| GET | `/api/books/search` | `q` required (not blank), optional `category`, `year` |
| GET | `/api/books/categories` | Metadata for category filter UI |
| GET | `/api/books/years` | Metadata for year filter UI |
| GET | `/api/books/{id}/cover` | Serve cover: `?size=thumb\|medium\|large` (default: large) |
| PUT | `/api/books/{id}/cover` | Replace cover: `cover` file part required |
| POST | `/api/books/bulk` | CSV upload via `file` part; response includes row-level errors |

## Validation rules (request body)

- `title`, `author`: `@NotBlank`
- `year`: `1000 <= year <= currentYear` (validated via `@Min(1000)` + `@AssertTrue`)
- `rating`: `0.0 <= rating <= 5.0` (`@DecimalMin("0.0")` + `@DecimalMax("5.0")`)
- `q` (search): `@NotBlank`
- `page`: `>= 0`
- `size`: `1..100`
- `sort`: property must be in `{title, year, rating, createdAt, updatedAt}`

All validation failures return **400 `VALIDATION_ERROR`** with `details` array.