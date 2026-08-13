# BookVerse API — Frontend Developer Guide

This document describes the **entire HTTP API** of BookVerse so a frontend team
can build against it without reading backend code. It covers the data model,
every endpoint, request/response shapes, error handling, and the frontend
gotchas that commonly trip people up.

> **Interactive alternative:** the running app also exposes Swagger UI at
> `http://localhost:8080/swagger-ui.html` (OpenAPI spec at `/api-docs`).
> A ready-made **Postman collection** lives in [`postman/`](../postman/README.md).

---

## 1. Big picture

BookVerse is an **e-book catalog management API**. Frontend screens typically
need:

- a **browse / list** screen (paginated, filterable, sortable),
- a **detail** screen,
- a **create / edit** form,
- a **search** box (by title / author),
- **cover thumbnails** everywhere.

There is **no authentication** on any endpoint today — every call is public.
That may change; expect an auth header in the future.

| Aspect | Value |
|--------|-------|
| Protocol | HTTP / JSON (multipart only for create-with-cover & bulk import) |
| Base URL (dev) | `http://localhost:8080` |
| Base URL (prod) | configurable; see the Postman *prod* environment |
| Data format | JSON (`application/json`), UTF-8 |
| IDs | Server-assigned `Long`, auto-increment |
| Dates | ISO-8601 `LocalDateTime`, e.g. `2026-08-07T15:30:00.123` |

---

## 2. Quick reference

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/books` | Paginated list + filter by `category` / `year` + sort |
| 2 | `GET` | `/api/books/{id}` | Single book detail |
| 3 | `POST` | `/api/books` | Create book (**multipart**, optional cover file) |
| 4 | `PUT` | `/api/books/{id}` | Update book (**multipart**, optional cover file) |
| 5 | `DELETE` | `/api/books/{id}` | Soft-delete book |
| 6 | `GET` | `/api/books/search?q=…` | Search by title / author (LIKE) |
| 7 | `GET` | `/api/books/{id}/cover?size=…` | Serve a cover image (WebP) |

---

## 3. The `Book` data model

Every book has these fields. All responses use exactly this shape (JSON
`camelCase`).

| Field | Type | Nullable | Meaning / notes |
|-------|------|----------|-----------------|
| `id` | `Long` | no | Server-assigned unique id |
| `title` | `String` | **no** | Book title (required on create/update) |
| `author` | `String` | **no** | Author name (required on create/update) |
| `isbn` | `String` | yes | Unique. **Nulled on soft-delete** → can be reused |
| `year` | `Integer` | yes | Publication year |
| `category` | `String` | yes | Category label. **Seeded data uses publisher as category** (exact-match filter) |
| `rating` | `Double` | yes | e.g. `4.5`. Seeded range 3.0–5.0, one decimal |
| `description` | `String` | yes | Long-form text |
| `coverPath` | `String` | yes | **Server-internal storage path — do NOT use directly** (see §8) |
| `createdAt` | `DateTime` | no | ISO-8601 local datetime |
| `updatedAt` | `DateTime` | no | ISO-8601 local datetime |

### Example book object

```json
{
  "id": 512,
  "title": "Flu: The Story of the Great Influenza Pandemic of 1918",
  "author": "Gina Bari Kolata",
  "isbn": "0374157065",
  "year": 1999,
  "category": "Farrar Straus Giroux",
  "rating": 4.2,
  "description": "A sample book: Flu: The Story of … (published 1999).",
  "coverPath": "uploads/covers/2026/08/512",
  "createdAt": "2026-08-07T15:53:52.84",
  "updatedAt": "2026-08-07T15:53:52.84"
}
```

> ⚠️ **`coverPath` is internal.** It is a file-system path like
> `uploads/covers/2026/08/512`, not a URL your client can fetch. To show a
> cover you must call `GET /api/books/{id}/cover?size=…` (§8). Treat
> `coverPath` as opaque / for backend diagnostics only.

---

## 4. Endpoints

### 4.1 `GET /api/books` — paginated list with filtering & sorting

Returns a Spring Data **page** (see §5) of books, newest first by default.

**Query parameters**

| Param | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `page` | `int` | no | `0` | Zero-based page index. Must be ≥ 0. |
| `size` | `int` | no | `20` | Page size. Must be between 1 and 100. |
| `sort` | `String` | no | `createdAt,desc` | Property + `,asc`/`,desc`. Allowed properties: `title`, `year`, `rating`, `createdAt`, `updatedAt`. |
| `category` | `String` | no | — | **Exact-match** filter |
| `year` | `int` | no | — | **Exact-match** filter |

**Filters compose with AND:** if both `category` and `year` are given, **both
must match** (books that match the category AND the year). Same applies to
the `/search` endpoint.

**Examples**

```
GET /api/books?page=0&size=20&sort=createdAt,desc
GET /api/books?category=Classic&size=50
GET /api/books?year=2000
GET /api/books?sort=rating,desc&size=10
GET /api/books?category=Science+Fiction&year=1965
```

**Examples**

```
GET /api/books?page=0&size=20&sort=createdAt,desc
GET /api/books?category=Classic&size=50
GET /api/books?year=2000
GET /api/books?sort=rating,desc&size=10
```

**Response** — `200 OK`, `application/json`:

```json
{
  "content": [ /* array of Book objects, §3 */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 25,
  "totalElements": 500,
  "last": false,
  "size": 20,
  "number": 0,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "numberOfElements": 20,
  "first": true,
  "empty": false
}
```

---

### 4.2 `GET /api/books/{id}` — book detail

**Path params:** `id` (`Long`, required).

**Response** — `200 OK` with a single Book object (§3).

**Errors**

| Status | Code | When |
|--------|------|------|
| `404` | `BOOK_NOT_FOUND` | No book with this id, **or it was soft-deleted** |

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found with id: 999",
  "timestamp": "2026-08-07T15:38:33.2607465",
  "path": "/api/books/999"
}
```

---

### 4.3 `POST /api/books` — create book (with optional cover)

> 🔥 **Most important gotcha in this API:** this endpoint **only accepts
> `multipart/form-data`**, even when you are not uploading a cover. A plain
> JSON `POST` body gets a **415**. See the form-data example below.

**Multipart parts**

| Part | Type | Required | Content-Type | Value |
|------|------|----------|--------------|-------|
| `book` | text | **yes** | `application/json` | The book JSON (below) |
| `cover` | file | no | `image/jpeg` \| `image/png` \| `image/webp` | Cover image file |

**`book` part JSON**

```json
{
  "title": "Dune",
  "author": "Frank Herbert",
  "isbn": "9780441172719",
  "year": 1965,
  "category": "Science Fiction",
  "rating": 4.7,
  "description": "Set on the desert planet Arrakis."
}
```

Only `title` and `author` are **required** (`@NotBlank`). Everything else is
optional and can be `null` / omitted.

**cURL (no cover):**

```bash
curl -X POST http://localhost:8080/api/books \
  -F 'book={"title":"Dune","author":"Frank Herbert","isbn":"9780441172719","year":1965,"category":"Science Fiction","rating":4.7};type=application/json'
```

**cURL (with cover):**

```bash
curl -X POST http://localhost:8080/api/books \
  -F 'book={"title":"Dune","author":"Frank Herbert"};type=application/json' \
  -F 'cover=@dune.png;type=image/png'
```

> Note the `;type=application/json` on the `book` part. Without it the server
> rejects the part as `application/octet-stream`.

**JavaScript / fetch** (the part `Content-Type` must be set):

```js
const book = JSON.stringify({
  title: "Dune", author: "Frank Herbert",
  isbn: "9780441172719", year: 1965, category: "Science Fiction", rating: 4.7
});
const fd = new FormData();
fd.append("book", new Blob([book], { type: "application/json" }));
// optional:
// fd.append("cover", coverFile);   // coverFile: File (jpg/png/webp)

const res = await fetch("http://localhost:8080/api/books", { method: "POST", body: fd });
const created = await res.json();
```

**Response** — `201 Created` with the full created Book object (§3), including
`coverPath` if a cover was uploaded.

**Errors**

| Status | Code | When |
|--------|------|------|
| `400` | `VALIDATION_ERROR` | `title` and/or `author` blank/missing |
| `400` | `INVALID_IMAGE_FORMAT` | `cover` uploaded but not jpg/png/webp |
| `409` | `ISBN_ALREADY_EXISTS` | `isbn` already present in the DB |
| `415` | — | Body sent as `application/json` instead of multipart |
| `413` | `FILE_TOO_LARGE` | Cover exceeds 5 MB (upload limit) |

`VALIDATION_ERROR` body includes the offending fields:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-08-07T15:53:51.91",
  "path": "/api/books",
  "details": [
    { "field": "title",  "message": "Title must not be blank" },
    { "field": "author", "message": "Author must not be blank" }
  ]
}
```

---

### 4.4 `PUT /api/books/{id}` — update book

> This endpoint **also uses `multipart/form-data`** (like create). It accepts
> an optional cover file replacement alongside the book JSON.

**Path params:** `id` (`Long`, required).

**Multipart parts**

| Part | Type | Required | Content-Type | Value |
|------|------|----------|--------------|-------|
| `book` | text | **yes** | `application/json` | The book JSON (below) |
| `cover` | file | no | `image/jpeg` \| `image/png` \| `image/webp` | New cover image file |

**`book` part JSON** (same shape as create; `title` + `author` required):

```json
{
  "title": "Dune (Revised)",
  "author": "Frank Herbert",
  "isbn": "9780441172719",
  "year": 1965,
  "category": "Science Fiction",
  "rating": 4.9,
  "description": "Updated edition."
}
```

**cURL (no cover change):**

```bash
curl -X PUT http://localhost:8080/api/books/1 \
  -F 'book={"title":"Dune (Revised)","author":"Frank Herbert"};type=application/json'
```

**cURL (with cover replacement):**

```bash
curl -X PUT http://localhost:8080/api/books/1 \
  -F 'book={"title":"Dune (Revised)","author":"Frank Herbert"};type=application/json' \
  -F 'cover=@new-cover.png;type=image/png'
```

**Response** — `200 OK` with the updated Book object. `updatedAt` advances.  
If a new cover is uploaded, the previous cover files are cleaned up server-side.

**Errors**

| Status | Code | When |
|--------|------|------|
| `400` | `VALIDATION_ERROR` | `title`/`author` blank |
| `400` | `INVALID_IMAGE_FORMAT` | `cover` uploaded but not jpg/png/webp |
| `404` | `BOOK_NOT_FOUND` | Book missing / soft-deleted |
| `409` | `ISBN_ALREADY_EXISTS` | New `isbn` collides with **another** book (changing to your own current ISBN is fine) |

---

### 4.5 `DELETE /api/books/{id}` — delete (soft delete)

**Path params:** `id` (`Long`, required).

**What "soft delete" means for the frontend:**

- The row is flagged `deleted = true` and kept in the DB (recoverable by an
  admin, no endpoint exposes it yet).
- The book **disappears immediately** from all reads:
  - `GET /api/books` and `/search` no longer return it,
  - `GET /api/books/{id}` returns `404`.
- The **ISBN is released** (set to `NULL`), so a new book can reuse it.

**Response** — `204 No Content`, empty body.

**Errors** — `404 BOOK_NOT_FOUND` if the id doesn't exist (or was already deleted).

```bash
curl -X DELETE http://localhost:8080/api/books/512   # → 204
curl -X DELETE http://localhost:8080/api/books/512   # → 404 BOOK_NOT_FOUND (again)
```

---

### 4.6 `GET /api/books/search?q=…` — search by title / author

Case-insensitive **substring (LIKE) match** against `title` OR `author`. It is
a simple contains-search, not a relevance-ranked full-text search — results
are ordered by the page sort, not by score.

**Query parameters**

| Param | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `q` | `String` | **yes** | — | Term matched against title OR author. **Must not be blank** (`@NotBlank`). |
| `category` | `String` | no | — | Extra exact-match filter on top of `q` |
| `year` | `int` | no | — | Extra exact-match filter on top of `q` |
| `page` / `size` / `sort` | — | no | as §4.1 | Standard pagination |

**Examples**

```
GET /api/books/search?q=herbert
GET /api/books/search?q=the&category=Fiction
GET /api/books/search?q=dune&size=50
GET /api/books/search?q=spring&year=2020
```

**Response** — `200 OK`, a Spring Data page (§5) of Book objects.

> ⚠️ **Empty `q` returns `400 VALIDATION_ERROR`** (field `q`: "Search query must not be blank").

---

### 4.7 `GET /api/books/{id}/cover?size=…` — serve a cover image

Returns the book's cover as a **WebP** image (`Content-Type: image/webp`).
Every uploaded cover is stored at 3 sizes.

**Query parameters**

| Param | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `size` | `String` | no | `large` | `thumb` (200px) \| `medium` (500px) \| `large` (1200px) |

**Usage from `<img>`:**

```html
<!-- thumbnail for list rows / cards -->
<img src="/api/books/512/cover?size=thumb" alt="cover" />

<!-- detail page -->
<img src="/api/books/512/cover?size=large" alt="cover" />
```

**Response** — `200 OK`, `image/webp`, with a **7-day cache** header
(`Cache-Control: max-age=604800, public`) — browsers may cache for a week.
The `immutable` directive is **not** used, so a browser may revalidate the URL
after a cover replacement and pick up the new image.

**Errors**

| Status | Code | When |
|--------|------|------|
| `400` | `INVALID_IMAGE_FORMAT` | `size` is not `thumb`/`medium`/`large` |
| `404` | `BOOK_NOT_FOUND` | Book missing / soft-deleted |
| `404` | — (empty body) | Book has no cover, or the image file is missing |

> **Frontend guidance:** a book without a cover returns `404`. Always render a
> placeholder on image error:
>
> ```js
> <img src={`/api/books/${id}/cover?size=thumb`}
>      onError={(e) => (e.currentTarget.src = "/images/no-cover.png")} />
> ```

---

## 5. Pagination, filtering, sorting

All list-style endpoints return the Spring Data `Page<T>` envelope (§4.1).
Use these fields in the UI:

| Field | Meaning |
|-------|---------|
| `content` | The books for this page |
| `totalElements` | Total books across all pages (for "Showing 1–20 of 500") |
| `totalPages` | `ceil(totalElements / size)` |
| `number` | Current page index (0-based) |
| `size` | Page size actually used |
| `numberOfElements` | Books on this page (last page may be smaller) |
| `first` / `last` | Booleans for prev/next button disabling |

**Server-side paging — don't fetch "all" client-side.** There is no endpoint
that returns everything. Use `page`/`size` and let the server paginate.
Sorting is done with `sort=<property>,<direction>`:
`sort=title,asc`, `sort=rating,desc`, `sort=createdAt,desc` (default).

---

## 6. Error model

Every error response is a JSON object (except `413` and the empty-body cover
`404`). Two shapes:

**Simple error:**

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found with id: 999",
  "timestamp": "2026-08-07T15:38:33.26",
  "path": "/api/books/999"
}
```

**Validation error** (adds `details`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-08-07T15:53:51.91",
  "path": "/api/books",
  "details": [
    { "field": "title",  "message": "Title must not be blank" },
    { "field": "author", "message": "Author must not be blank" }
  ]
}
```

### Error codes → HTTP status

| `code` | HTTP | Meaning |
|--------|------|---------|
| `VALIDATION_ERROR` | `400` | Field validation failed — read `details` |
| `BAD_REQUEST` | `400` | Generic bad request (e.g. bulk import of a non-csv/xlsx file) |
| `INVALID_IMAGE_FORMAT` | `400` | Wrong image type, or bad `size` on cover |
| `FILE_TOO_LARGE` | `413` | Upload exceeds 5 MB |
| `BOOK_NOT_FOUND` | `404` | Missing or soft-deleted book |
| `ISBN_ALREADY_EXISTS` | `409` | Duplicate ISBN on create/update |
| `INTERNAL_ERROR` | `500` | Server error — don't expect a useful message; log + show a generic "something went wrong" |

**Frontend rule of thumb:** branch on the **HTTP status**, and use `code` for
fine-grained messages. `details[].field` tells you exactly which form input is
invalid.

---

## 7. Bulk import

`POST /api/books/bulk` accepts a **CSV** (`.csv`) or **Excel** (`.xlsx`) file
and imports many books in one call. This is an admin/back-office feature, not
something a normal catalog screen needs.

CSV parsing uses a proper CSV parser (Apache Commons CSV), so **quoted commas
and escaped quotes inside fields are handled correctly** and do not split
columns. Each error reports the **original source row number** (including the
header row as 1), so you can map failures back to the spreadsheet/CSV exactly.

**Request** — multipart with one `file` part:

```bash
curl -X POST http://localhost:8080/api/books/bulk \
  -F 'file=@books.csv;type=text/csv'
```

**Response** — `200 OK` with an import report (not `201`):

```json
{
  "totalRows": 6,
  "successCount": 6,
  "failedCount": 0,
  "errors": []
}
```

Bad rows don't abort the import — they're reported with their source row
numbers:

```json
{
  "totalRows": 3,
  "successCount": 1,
  "failedCount": 2,
  "errors": [
    { "row": 2, "reason": "Title and author are required" },
    { "row": 4, "reason": "Duplicate ISBN in file or database: 9780060850524" }
  ]
}
```

After a successful import, all server-side read caches (list, detail, search,
categories, years) are evicted so subsequent reads reflect the new data
immediately.

**Errors** — `400 BAD_REQUEST` for unsupported extensions; `413` if the file is
too large.

---

## 8. Cover image pipeline (what happens on upload)

When a cover is uploaded with `POST /api/books` or `PUT /api/books/{id}`:

1. **Content-Type guard** — JPG, PNG, WebP only (fast reject for wrong types).
2. **Decoded image validation** — the actual image bytes are decoded with
   `ImageIO`. If the file is not a valid image (e.g., a text file renamed to
   `.png`), it is rejected with `400 INVALID_IMAGE_FORMAT`.
3. The image is resized to **three sizes**: `thumb` 200px, `medium` 500px,
   `large` 1200px.
4. Each size is converted to **WebP** and written to **temporary paths first**.
5. Only after **all three sizes succeed** are they atomically promoted to the
   final filenames. This ensures no partial covers are ever published.
6. Files are stored on the server at
   `uploads/covers/yyyy/MM/{id}-{size}.webp`.

On `PUT /api/books/{id}` with a new cover: the new cover is uploaded and
promoted first, the database record is updated, then the **old cover files are
cleaned up**. On `DELETE /api/books/{id}`: the book is removed from the database,
then its cover files are cleaned up.

For the frontend this means: **always ask for WebP**, accept `image/webp` in
`<img src>`, and let the server do the resizing — never download `large` for a
thumbnail. The thumbnail `<img>` is `?size=thumb`.

---

## 9. Caching & performance (frontend implications)

- **Covers** are served with `Cache-Control: max-age=604800, public`
  (7 days). The `immutable` directive is **not** used, so a browser may
  revalidate the URL after a cover replacement and pick up the new image.
- **List / detail / search** responses are cached **server-side** (Caffeine).
  A `POST`/`PUT`/`DELETE` invalidates the affected caches, so a stale list is
  transient (next read is fresh). The frontend sees no difference — just don't
  be surprised if a freshly created book doesn't appear instantly in a cached
  list on the very next request if the invalidation races; re-fetching is safe.
- **Bulk import** evicts all read caches (list, detail, search, categories, years)
  after a successful import.

---

## 10. End-to-end frontend workflows

**Browse catalog:**

```
GET /api/books?page=0&size=20&sort=createdAt,desc
→ render content[], wire prev/next to number±1, show totalElements
→ thumbnails: <img src="/api/books/{id}/cover?size=thumb">
```

**View detail:**

```
GET /api/books/{id}            → book JSON
GET /api/books/{id}/cover?size=large → big cover (handle 404 → placeholder)
```

**Create book form:**

```
multipart POST /api/books
  part "book"  = JSON (title, author required; isbn/year/category/rating/description optional)
  part "cover" = optional image file
→ 201 created book (redirect to detail; handle 409 duplicate isbn, 400 validation)
```

**Edit book:**

```
GET  /api/books/{id}                      → prefill form
PUT  /api/books/{id}  (multipart)         → 200 updated (handle 409/400/404)
  part "book"  = JSON (title, author required; isbn/year/category/rating/description optional)
  part "cover" = optional image file (replaces existing cover; old cover cleaned up)
```

**Delete:**

```
DELETE /api/books/{id}  → 204, remove from list, show toast.
```

**Search:**

```
GET /api/books/search?q={term}&page=0&size=20
→ same Page shape as browse; empty results when totalElements === 0.
```

---

## 11. Useful resources

| Resource | Location |
|----------|----------|
| Interactive docs (Swagger UI) | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api-docs` |
| Postman collection + env + sample data | [`postman/`](../postman/README.md) |
| Project overview | [`../README.md`](../README.md) |

---

## 12. Changelog / contract notes

- **Create is multipart-only** (`consumes = MULTIPART_FORM_DATA_VALUE`).
  Plain-JSON create fails with `415`. This is the #1 integration mistake.
- **Soft delete**: deleted books return `404`, and their ISBN becomes reusable.
- **`coverPath` is not a URL** — use the cover endpoint.
- **`category` filter is exact-match** and (for seeded data) equals the
  publisher. Prefix/fuzzy category matching is not supported.
- **Empty `q`** on search currently matches everything (unvalidated).
- No auth today. Everything is public.
