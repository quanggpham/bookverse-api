# Postman Collection — BookVerse API

End-to-end tests for every endpoint, using Postman **variables**, **pre-request
scripts** (dynamic data), **test scripts** (schema + value assertions), and
**data-driven** iteration.

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

4. **Fix the multipart file paths** (Postman does not port file paths across
   machines). For the three requests below, click the request, open the
   `body` tab, hover the file part and choose the matching file under `postman/`:
   - *Cover images → POST /api/books (create WITH cover)* → `cover` part → `sample-cover.png`
   - *Cover images → POST /api/books (create with INVALID cover)* → `cover` part → `sample-unsupported.txt`
   - *Bulk import* → each `file` part → the matching `sample-*.csv`

5. **Run** — either:
   - Whole collection: collection ▸ ▸ **Run collection** (folders execute in
     order; CRUD uses the book id captured by the create test), **or**
   - Per request: click **Send** one at a time, or run each folder.

## What it covers

**Health** — OpenAPI spec + Swagger UI are served.

**Books CRUD** — paginated list (with `category`/`year` filters), create,
`@Valid` validation error (400 `VALIDATION_ERROR`), get by id, PUT update,
soft-delete → 404 `BOOK_NOT_FOUND`, delete missing → 404.

**Search** — title/author search (case-insensitive), category-scoped search,
and a note that empty `q` currently matches everything (unvalidated — tighten
this test if you add `@NotBlank`).

**Cover images** — multipart create with a valid PNG (server resizes to 3
sizes + WebP), serving `thumb` as `image/webp` (magic bytes `RIFF….WEBP`
asserted), and rejecting a non-image with 400 `INVALID_IMAGE_FORMAT`.

**Bulk import** — clean CSV (6/6), duplicate ISBN (1 success + 1 error row),
bad rows (1 + 1), unsupported extension (400 `BAD_REQUEST`), missing file part
(400).

**Data-driven: 100 books** — one `POST /api/books` run 100× via the Runner,
with `postman/100-books.csv` as the data source. Each iteration asserts the
created book echoes its row's ISBN/title/author/year.

## Design notes

- **Variables**: `{{baseUrl}}`, `{{page}}`, `{{size}}` live in the
  environment; `bookId`, `coverId`, `coverPath`, `isbn`, `title`, `author`
  live in collection variables and are *captured* by test scripts for later
  requests in the folder.
- **Dynamic data**: pre-request scripts generate a fresh ISBN/title per run
  (`Date.now()`-based), so repeated runs never collide with the unique-ISBN
  constraint.
- **Deterministic bulk tests**: `sample-books-dup.csv` has an *internal*
  duplicate (both rows in the file), so the assertion `successCount = 1` holds
  even if `sample-books.csv` was already imported on an earlier run.
- **Pagination invariant** checked: `numberOfElements === content.length`.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| All requests `Could not connect` | App not running, or wrong environment selected (`baseUrl`) |
| Multipart `file` part empty / not sent | File path not selected on that request's body part — re-pick it |
| Create returns 409 `ISBN_ALREADY_EXISTS` | `isbn` not unique — re-run the request (pre-request regenerates it) |
| Cover 400 `INVALID_IMAGE_FORMAT` on a real image | `Content-Type` header not `multipart/form-data`, or the file isn't actually an image |
| Bulk 400 `BAD_REQUEST` | Wrong file chosen (e.g. `.txt` on a CSV request) |
| `data-driven` fails with `iterationData.get(...)` undefined | Data file not selected in the Runner → Data tab |

## Optional: run via Newman (CLI)

```bash
# if not installed
npm install -g newman

newman run postman/bookverse.postman_collection.json \
  -e postman/bookverse.local.postman_environment.json \
  --folder "Books CRUD" \
  --reporters cli,json --reporter-json-export newman-report.json

# data-driven with the CSV
newman run postman/bookverse.postman_collection.json \
  -e postman/bookverse.local.postman_environment.json \
  -d postman/100-books.csv \
  --folder "Data-driven: 100 books"
```
