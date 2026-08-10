# BookVerse Frontend + API Enhancements — Design Spec

**Date:** 2026-08-10
**Status:** Approved (all 5 sections reviewed by user)

## 1. Overview

Build a new React + Vite + TypeScript frontend (`bookverse-frontend/`) as a **sibling repo** next to the existing Spring Boot backend (`bookverse-api/`). The frontend is primarily a **testing tool for the backend** but styled as a polished, library-warm, AI-minimalist 2026 SPA. Alongside the frontend, the backend gains 4 API enhancements to fill UI/UX gaps, plus fixes to make category/year filters compose correctly.

### Goals
- Full CRUD, search, filter, pagination, cover display, bulk import UI against the live backend
- 4 backend API enhancements: categories list, years list, cover update endpoint, multipart-aligned PUT
- Fix backend so category + year compose (both in list and search)
- Code that is **easy to debug and easy to fix** (log every request, typed ApiError, small modules, no swallowed errors)

### Non-goals
- No auth, no user accounts
- No entity/DB changes
- No change to existing response shapes (`Page<BookResponse>`)
- No Tailwind — plain CSS with a token system

## 2. Architecture

### Repo layout
```
C:\Workspace\VCCORP\
├── bookverse-api\            ← backend (Spring Boot, port 8080) — modified
└── bookverse-frontend\       ← NEW repo (React + Vite + TS, port 5173)
```

### Backend changes (4 API + 2 query fixes)

| # | Change | Endpoint | Notes |
|---|--------|----------|-------|
| 1 | **Add** | `GET /api/books/categories` | `[{name, count}]` distinct, non-soft-deleted, sorted by count desc. Optional `?q=` filter. |
| 2 | **Add** | `GET /api/books/years` | `[{year, count}]` distinct, sorted desc. |
| 3 | **Add** | `PUT /api/books/{id}/cover` | multipart `cover`. Replaces old cover (same filename → overwrite). Returns updated `BookResponse`. Evicts `bookById` cache. |
| 4 | **Fix** | `PUT /api/books/{id}` | Change from JSON to multipart (`book` part + optional `cover` part) — consistent with POST. |
| 5 | **Fix** | `GET /api/books` + `GET /api/books/search` | category + year compose together (repo query with nullable params). |

Repository fix for composition:
```java
@Query("SELECT b FROM Book b WHERE (:category IS NULL OR b.category = :category) AND (:year IS NULL OR b.year = :year)")
Page<Book> findByCategoryAndYear(...)
```
Search query gains `:year` param. Cache keys updated accordingly.

Note: `ImageService.upload` writes `{bookId}-{size}.webp` — re-uploading the same bookId overwrites the old files automatically, so no manual deletion is needed.

### Frontend stack
- React 19 + Vite 6 + TypeScript
- React Router v7
- Plain `fetch` (no axios)
- Plain CSS with design tokens (no Tailwind)
- Vitest + React Testing Library for unit tests

### Frontend structure
```
bookverse-frontend/
├── src/
│   ├── main.tsx / App.tsx        — Router + layout shell
│   ├── api/
│   │   ├── client.ts             — fetch wrapper (log, errors, multipart)
│   │   ├── types.ts              — Book, Page, CategoryCount, YearCount, BulkImportResult, ApiError
│   │   └── books.ts              — typed API functions
│   ├── pages/
│   │   ├── BookList.tsx          — grid + FilterBar + pagination
│   │   ├── BookDetail.tsx        — detail + covers
│   │   ├── BookForm.tsx          — shared create/edit
│   │   ├── BulkImport.tsx        — CSV/XLSX upload + report
│   │   └── NotFound.tsx
│   ├── components/
│   │   ├── BookCard.tsx / StarRating.tsx / CategoryBadge.tsx
│   │   ├── Pagination.tsx / FilterBar.tsx
│   │   ├── CoverImage.tsx        — lazy + 404 placeholder
│   │   ├── Toast.tsx / ConfirmDialog.tsx
│   └── styles/                   — tokens.css + per-component CSS
└── vite.config.ts               — proxy /api → http://localhost:8080
```

## 3. Pages & flows

| Screen | Route | APIs exercised |
|--------|-------|----------------|
| List | `/` | GET list (category/year/sort/page), GET `/search`, categories, years |
| Detail | `/books/:id` | GET `/{id}`, GET cover (thumb/medium/large) |
| Create | `/books/new` | POST multipart (book + cover?) |
| Edit | `/books/:id/edit` | PUT multipart (book + cover?), PUT `/{id}/cover` |
| Bulk import | `/import` | POST `/bulk` + report |
| Not found | `*` | — |

### Key flows
- **List page**: mount → fetch categories + years to fill dropdowns. Search → `GET /search?q=`. Filter-only (category/year) → `GET /books`. Sort + pagination → refetch with params. All filters compose.
- **Form**: shared create/edit. Builds `FormData` with `book` (JSON blob) + `cover` (File). Edit also offers "Change cover" → `PUT /{id}/cover`.
- **Delete**: card/detail button → ConfirmDialog → `DELETE` → toast → back to list.
- **Errors**: client parses `ErrorResponse`/`ValidationErrorResponse` into typed `ApiError { status, code, message, fieldErrors, path, rawBody }`. Forms map `fieldErrors` to inputs. 404/409/500 → user-friendly messages with dev-detail expander (in dev mode).

## 4. Debuggability (explicit requirement)

- ApiClient logs every request: `[API] GET /api/books?page=0 → 200 in 45ms`. Enabled by `VITE_API_DEBUG`, auto-on in dev.
- `ApiError` is a rich type — any screen can render detailed error info.
- Query builder (`buildBooksQuery`) extracted as a pure, testable function.
- Errors are always surfaced (toast/banner/field), never swallowed.
- Small single-purpose modules; no hidden global state.

## 5. Data flow

```
List: mount → categories+years → filter/search/sort/page → GET
Form: submit → FormData → POST (new) | PUT (edit) → toast → list
Cover change: PUT /{id}/cover → toast → refreshed cover
Detail: GET /{id} + cover sizes
Bulk: POST /bulk → BulkImportResult → report table (success/failed rows + reasons)
```

## 6. Testing

| Layer | Tool | Coverage |
|-------|------|----------|
| Backend unit | JUnit 5 + Mockito | `BookServiceTest` extended: category+year, search+year |
| Backend controller | Spring MockMvc | new endpoints + PUT multipart |
| Backend integration | @SpringBootTest | categories/years; update soft-delete test if broken |
| Frontend unit | Vitest + RTL | api client (error parse, multipart), BookForm validation, buildBooksQuery |

### Verification checklist
1. `mvn test` green (backend)
2. `npm run build` passes (tsc)
3. `npm run test` passes (frontend unit)
4. Dev smoke: backend 8080 + frontend 5173 — full CRUD + filter + search + import

## 7. Visual design — "AI Minimalism, library-warm" (2026)

Light, airy, warm-neutral. Lots of whitespace, hairline borders, soft shadows, brass accent evoking a reading lamp. Serif for book titles keeps the library feel; clean sans for UI.

### Palette
| Role | Hex |
|------|-----|
| Background | `#F6F3EE` (warm ivory) |
| Surface/card | `#FDFCFA` |
| Ink primary | `#211E1A` |
| Ink secondary | `#6E675E` |
| **Accent (brass)** | `#A67C3D` |
| Accent hover | `#8C652B` |
| Hairline | `#E6DFD2` |

### Typography
- Display/headings/book titles: **Fraunces** (soft modern serif, warm)
- UI/body: **Manrope** (clean, slightly rounded, warm sans)
- Data/technical (ISBN etc.): `IBM Plex Mono` for labels at small size

### Layout
- Slim header, generous whitespace: wordmark **BookVerse.** (Fraunces) + compact nav (Thư viện / Thêm sách / Nhập hàng loạt)
- Book grid 2–4 cols responsive; minimal cards — 2:3 cover with soft warm shadow, title, author, star rating, category badge
- Sticky filter bar (search + 2 dropdowns + sort) styled as one slim toolbar
- Detail: 2-col, cover left / info right, wide whitespace
- Buttons: minimal borders, accent on hover

### Signature element — "Lamplight glow"
Each cover's shadow is warm brown/amber-toned (not black). On hover, a subtle warm glow radiates around the cover — like a reading lamp. This is the single "performing" element; everything else stays quiet and disciplined.

### Motion
- Card entrance: very light fade-up stagger (2–3px, ~260ms)
- Hover: warm glow eases in ~150ms
- Toast: gentle slide
- Respect `prefers-reduced-motion`
