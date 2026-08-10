# BookVerse Frontend + API Enhancements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a polished React + Vite + TypeScript frontend (`bookverse-frontend/`) as a sibling repo that fully tests the BookVerse backend, and add 4 backend API enhancements plus filter-composition fixes to support the UI.

**Architecture:** Two repos. Backend (Spring Boot 3.3 / Java 21) gains composite filters (category+year), metadata endpoints (categories/years), a cover-update endpoint, and a multipart-aligned PUT. Frontend (React 19 + Vite + TS + React Router v7) is a plain-fetch, plain-CSS SPA proxying `/api` → `localhost:8080`. Both repos are committed independently.

**Tech Stack:** Backend: Spring Boot 3.3.2, JPA, MapStruct, JUnit 5 + Mockito + MockMvc. Frontend: Vite 6, React 19, TypeScript, React Router v7, Vitest + React Testing Library, plain CSS.

## Global Constraints

- Backend response shapes are unchanged: list/search return `Page<BookResponse>`, errors follow `ErrorResponse`/`ValidationErrorResponse`.
- No entity/DB changes. No new dependencies in the backend pom.
- Frontend repo lives at `C:\Workspace\VCCORP\bookverse-frontend\` — a NEW git repo sibling to `bookverse-api\`. Never commit frontend files into `bookverse-api\`.
- Frontend port `5173`, Vite proxy `/api` → `http://localhost:8080`. CORS already allows `http://localhost:5173`.
- No axios, no Tailwind. Plain `fetch` + plain CSS.
- Debuggability rules: every API request logged (`[API] METHOD /path → status in Nms`), errors are typed `ApiError`, errors are never swallowed (always surfaced to the UI).
- Visual design tokens are fixed in Task 4 (`tokens.css`): palette `#F6F3EE / #FDFCFA / #211E1A / #6E675E / #A67C3D / #8C652B / #E6DFD2`, fonts **Fraunces** (display) + **Manrope** (body) + **IBM Plex Mono** (data), signature "lamplight glow" shadow.
- Vietnamese UI copy. Buttons/actions keep the same verb in button and toast ("Xóa" button → "Đã xóa" toast).
- Always run `mvn test` (backend) / `npm run build` + `npm test` (frontend) before completing a task.

---

## Part A — Backend API Enhancements (`bookverse-api\`)

### Task 1: Metadata queries + service methods + service tests

**Files:**
- Create: `src/main/java/com/internship/bookverse/dto/response/CategoryCount.java`
- Create: `src/main/java/com/internship/bookverse/dto/response/YearCount.java`
- Modify: `src/main/java/com/internship/bookverse/repository/BookRepository.java`
- Modify: `src/main/java/com/internship/bookverse/service/BookService.java`
- Modify: `src/test/java/com/internship/bookverse/service/BookServiceTest.java`

**Interfaces:**
- Produces: records `CategoryCount(String name, long count)` and `YearCount(Integer year, long count)`.
- Produces: `Page<Book> findByFilters(String category, Integer year, Pageable pageable)`, `Page<Book> searchBooks(String title, String author, String category, Integer year, Pageable pageable)`, `List<CategoryCount> findDistinctCategories()`, `List<YearCount> findDistinctYears()` on `BookRepository`.
- Produces: `List<CategoryCount> getCategories()`, `List<YearCount> getYears()` on `BookService`; changed `search(String q, String category, Integer year, Pageable pageable)`.
- Consumes: existing `Page<BookResponse>` and `BookMapper.toResponse`.

- [ ] **Step 1: Write the failing service tests**

Open `src/test/java/com/internship/bookverse/service/BookServiceTest.java`. Update the two existing tests that mock now-removed repository methods, and add new tests:

Replace `getAll_shouldReturnPageOfBookResponses` body's mock line:
```java
when(bookRepository.findByFilters(null, null, PageRequest.of(0, 10))).thenReturn(page);
```
Replace `getAll_shouldFilterByCategory_whenCategoryProvided` mock line:
```java
when(bookRepository.findByFilters("Technology", null, PageRequest.of(0, 10))).thenReturn(page);
```
Update the two search tests to the new 4-arg signature:
```java
when(bookRepository.searchBooks(any(), any(), any(), any(), any())).thenReturn(page);
Page<BookResponse> result = bookService.search("Spring", null, null, PageRequest.of(0, 10));
// and
Page<BookResponse> result = bookService.search("Spring", "Technology", null, PageRequest.of(0, 10));
```
Add these new tests before the closing brace:
```java
@Test
void getAll_shouldFilterByCategoryAndYear_whenBothProvided() {
    Page<Book> page = new PageImpl<>(List.of(book));
    when(bookRepository.findByFilters("Technology", 2016, PageRequest.of(0, 10))).thenReturn(page);

    Page<BookResponse> result = bookService.getAll(PageRequest.of(0, 10), "Technology", 2016);

    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(bookRepository).findByFilters("Technology", 2016, PageRequest.of(0, 10));
}

@Test
void search_shouldFilterByYear_whenYearProvided() {
    Page<Book> page = new PageImpl<>(List.of(book));
    when(bookRepository.searchBooks(any(), any(), any(), any(), any())).thenReturn(page);

    Page<BookResponse> result = bookService.search("Spring", null, 2016, PageRequest.of(0, 10));

    assertThat(result.getTotalElements()).isEqualTo(1);
}

@Test
void getCategories_shouldReturnCategoryCounts() {
    when(bookRepository.findDistinctCategories())
            .thenReturn(List.of(new CategoryCount("Technology", 5), new CategoryCount("Fiction", 3)));

    var result = bookService.getCategories();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("Technology");
    assertThat(result.get(0).count()).isEqualTo(5);
}

@Test
void getYears_shouldReturnYearCounts() {
    when(bookRepository.findDistinctYears())
            .thenReturn(List.of(new YearCount(2024, 8), new YearCount(2020, 2)));

    var result = bookService.getYears();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).year()).isEqualTo(2024);
    assertThat(result.get(0).count()).isEqualTo(8);
}
```
Add imports: `java.util.List`, `com.internship.bookverse.dto.response.CategoryCount`, `com.internship.bookverse.dto.response.YearCount`. (Note `verify` and `when` already imported.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BookServiceTest`
Expected: COMPILATION ERROR — `findByFilters`, `searchBooks` (5 args), `getCategories`, `getYears` don't exist yet.

- [ ] **Step 3: Create the two records**

`src/main/java/com/internship/bookverse/dto/response/CategoryCount.java`:
```java
package com.internship.bookverse.dto.response;

public record CategoryCount(String name, long count) {}
```
`src/main/java/com/internship/bookverse/dto/response/YearCount.java`:
```java
package com.internship.bookverse.dto.response;

public record YearCount(Integer year, long count) {}
```

- [ ] **Step 4: Update the repository**

Open `src/main/java/com/internship/bookverse/repository/BookRepository.java`. Replace the `findByCategory` and `findByYear` methods and the `searchBooks` query with:
```java
@Query("SELECT b FROM Book b WHERE (:category IS NULL OR b.category = :category) AND (:year IS NULL OR b.year = :year)")
Page<Book> findByFilters(@Param("category") String category, @Param("year") Integer year, Pageable pageable);

@Query("SELECT b FROM Book b WHERE (LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND (:category IS NULL OR b.category = :category) AND (:year IS NULL OR b.year = :year)")
Page<Book> searchBooks(@Param("title") String title, @Param("author") String author, @Param("category") String category, @Param("year") Integer year, Pageable pageable);

@Query("SELECT b.category AS name, COUNT(b) AS count FROM Book b WHERE b.category IS NOT NULL AND b.category <> '' GROUP BY b.category ORDER BY COUNT(b) DESC")
List<CategoryCount> findDistinctCategories();

@Query("SELECT b.year AS year, COUNT(b) AS count FROM Book b WHERE b.year IS NOT NULL GROUP BY b.year ORDER BY b.year DESC")
List<YearCount> findDistinctYears();
```
Update imports: add `java.util.List`, `com.internship.bookverse.dto.response.CategoryCount`, `com.internship.bookverse.dto.response.YearCount`.

- [ ] **Step 5: Update the service**

Open `src/main/java/com/internship/bookverse/service/BookService.java`. Update imports to add `java.util.List`, `com.internship.bookverse.dto.response.CategoryCount`, `com.internship.bookverse.dto.response.YearCount`.

Replace the body of `getAll` (drop the if/else chain — always use the composite query):
```java
@Cacheable(value = "books", key = "{#pageable.pageNumber, #pageable.pageSize, #pageable.sort, #category, #year}")
public Page<BookResponse> getAll(Pageable pageable, String category, Integer year) {
    log.debug("getAll: page={} size={} category={} year={}",
            pageable.getPageNumber(), pageable.getPageSize(), category, year);
    return bookRepository.findByFilters(category, year, pageable).map(bookMapper::toResponse);
}
```
Replace the `search` method:
```java
@Cacheable(value = "bookSearch", key = "{#q, #category, #year, #pageable.pageNumber, #pageable.pageSize, #pageable.sort}")
public Page<BookResponse> search(String q, String category, Integer year, Pageable pageable) {
    log.debug("search: q='{}' category={} year={} page={}", q, category, year, pageable.getPageNumber());
    return bookRepository.searchBooks(q, q, category, year, pageable).map(bookMapper::toResponse);
}
```
Add two new methods after `getById`:
```java
@Cacheable(value = "categories")
public List<CategoryCount> getCategories() {
    log.debug("getCategories");
    return bookRepository.findDistinctCategories();
}

@Cacheable(value = "years")
public List<YearCount> getYears() {
    log.debug("getYears");
    return bookRepository.findDistinctYears();
}
```
Update the cache eviction on `create`, `update`, and `delete` from `value = {"books", "bookSearch"}` to `value = {"books", "bookSearch", "categories", "years"}` (create currently lacks `bookById` too — add `bookById` for consistency on all three). Keep `updateCoverPath` as `value = {"books", "bookSearch", "bookById"}`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=BookServiceTest`
Expected: ALL PASS.

- [ ] **Step 7: Commit**

```bash
cd C:\Workspace\VCCORP\bookverse-api
git add -A
git commit -m "feat(api): composite category+year filters, categories & years metadata endpoints"
```

---

### Task 2: Controller endpoints + MockMvc tests

**Files:**
- Modify: `src/main/java/com/internship/bookverse/controller/BookController.java`
- Modify: `src/main/java/com/internship/bookverse/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/internship/bookverse/controller/BookControllerTest.java`

**Interfaces:**
- Consumes: `bookService.search(q, category, year, pageable)`, `bookService.getCategories()`, `bookService.getYears()`, `bookService.updateCoverPath(id, path)`.
- Produces: `GET /api/books/categories`, `GET /api/books/years`, `PUT /api/books/{id}/cover` (multipart `cover`), and `PUT /api/books/{id}` now multipart (`book` part + optional `cover` part). `GET /api/books/search` gains optional `year` param.
- Produces: `@ExceptionHandler(MissingServletRequestPartException.class)` → 400.

- [ ] **Step 1: Write the failing MockMvc tests**

Open `src/test/java/com/internship/bookverse/controller/BookControllerTest.java`. Update imports: add `com.internship.bookverse.dto.response.CategoryCount`, `com.internship.bookverse.dto.response.YearCount`, `org.springframework.http.HttpMethod`, `org.springframework.web.multipart.support.MissingServletRequestPartException`.

Update `search_shouldReturnPage` mock to 4 args:
```java
when(bookService.search(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(bookResponse)));
```
Replace `update_shouldReturn200` (JSON body → multipart):
```java
@Test
void update_shouldReturn200_whenMultipart() throws Exception {
    BookUpdateRequest request = BookUpdateRequest.builder()
            .title("Updated Title")
            .author("Updated Author")
            .build();
    when(bookService.update(eq(1L), any())).thenReturn(bookResponse);

    MockMultipartFile bookPart = new MockMultipartFile(
            "book", "", "application/json", objectMapper.writeValueAsString(request).getBytes());

    mockMvc.perform(multipart(HttpMethod.PUT, "/api/books/1")
                    .file(bookPart))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
}
```
Add these tests before the closing brace:
```java
@Test
void getCategories_shouldReturnList() throws Exception {
    when(bookService.getCategories())
            .thenReturn(List.of(new CategoryCount("Technology", 5)));

    mockMvc.perform(get("/api/books/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Technology"))
            .andExpect(jsonPath("$[0].count").value(5));
}

@Test
void getYears_shouldReturnList() throws Exception {
    when(bookService.getYears())
            .thenReturn(List.of(new YearCount(2024, 8)));

    mockMvc.perform(get("/api/books/years"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].year").value(2024))
            .andExpect(jsonPath("$[0].count").value(8));
}

@Test
void updateCover_shouldReturn200() throws Exception {
    when(bookService.getById(1L)).thenReturn(bookResponse);
    when(bookService.updateCoverPath(eq(1L), any())).thenReturn(bookResponse);
    MockMultipartFile cover = new MockMultipartFile(
            "cover", "cover.png", "image/png", new byte[]{1, 2, 3});

    mockMvc.perform(multipart(HttpMethod.PUT, "/api/books/1/cover").file(cover))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
}

@Test
void updateCover_shouldReturn400_whenMissingPart() throws Exception {
    mockMvc.perform(multipart(HttpMethod.PUT, "/api/books/1/cover"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_PART"));
}
```
Note: in the `updateCover_shouldReturn200` unit test `imageService.upload` is a `@MockBean`, so the existence pre-check `bookService.getById(1L)` and `imageService.upload(...)` and `bookService.updateCoverPath(...)` are stubbed/return defaults — the test asserts the controller wiring and 200 status. The missing-part test never reaches the controller method body, so no stubs are needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BookControllerTest`
Expected: COMPILATION ERROR — controller methods missing / search signature mismatch.

- [ ] **Step 3: Update the controller**

Open `src/main/java/com/internship/bookverse/controller/BookController.java`. Update imports: add `java.util.List`, `com.internship.bookverse.dto.response.CategoryCount`, `com.internship.bookverse.dto.response.YearCount`.

Update `search` to accept `year`:
```java
@GetMapping("/search")
public ResponseEntity<Page<BookResponse>> search(
        @RequestParam String q,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer year,
        @PageableDefault(size = 20) Pageable pageable) {
    log.info("GET /api/books/search?q='{}'&category={}&year={}&page={}", q, category, year, pageable.getPageNumber());
    return ResponseEntity.ok(bookService.search(q, category, year, pageable));
}
```
Change `PUT /{id}` from JSON to multipart:
```java
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
```
Add the two metadata endpoints and the cover-update endpoint after `search` / before the cover-serve endpoint:
```java
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
```

- [ ] **Step 4: Add the missing-part handler**

Open `src/main/java/com/internship/bookverse/exception/GlobalExceptionHandler.java`. Add import `org.springframework.web.multipart.support.MissingServletRequestPartException` and this handler next to `handleFileTooLarge`:
```java
@ExceptionHandler(MissingServletRequestPartException.class)
public ResponseEntity<ErrorResponse> handleMissingPart(
        MissingServletRequestPartException ex, HttpServletRequest request) {
    log.warn("400 MISSING_PART: {} {}", request.getMethod(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                    .code("MISSING_PART")
                    .message("Required part is missing: " + ex.getRequestPartName())
                    .timestamp(LocalDateTime.now())
                    .path(request.getRequestURI())
                    .build());
}
```

- [ ] **Step 5: Run the controller tests**

Run: `mvn test -Dtest=BookControllerTest`
Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(api): categories/years endpoints, cover update endpoint, multipart PUT"
```

---

### Task 3: Cache config, integration test, full verify

**Files:**
- Modify: `src/main/java/com/internship/bookverse/config/CacheConfig.java`
- Create: `src/test/java/com/internship/bookverse/integration/FilterCompositionIntegrationTest.java`
- Test: `src/test/java/com/internship/bookverse/integration/SoftDeleteIntegrationTest.java`

**Interfaces:**
- Consumes: `bookService.getAll(pageable, category, year)`, `bookService.getCategories()`, `bookService.getYears()`.
- Produces: registered Caffeine caches `categories`, `years` (5 min TTL).

- [ ] **Step 1: Register the new caches first**

Register the caches BEFORE running the integration test — `getCategories()`/`getYears()` are `@Cacheable`, and `CaffeineCacheManager` throws `Unsupported cache name` on any unregistered cache.

Open `src/main/java/com/internship/bookverse/config/CacheConfig.java` and add before the `return`:
```java
cacheManager.registerCustomCache("categories",
        Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200)
                .build());
cacheManager.registerCustomCache("years",
        Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build());
```

- [ ] **Step 2: Write the integration test**

Create `src/test/java/com/internship/bookverse/integration/FilterCompositionIntegrationTest.java`:
```java
package com.internship.bookverse.integration;

import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FilterCompositionIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void cleanUp() {
        bookRepository.deleteAll();
    }

    private Book book(String title, String category, Integer year) {
        return Book.builder().title(title).author("Author").category(category).year(year).build();
    }

    @Test
    void getAll_shouldComposeCategoryAndYear() {
        bookRepository.saveAll(List.of(
                book("Alpha", "Science", 2020),
                book("Beta", "Science", 2015),
                book("Gamma", "Fiction", 2020)));

        Page<BookResponse> page = bookService.getAll(PageRequest.of(0, 20), "Science", 2020);

        assertThat(page.getContent())
                .extracting(BookResponse::getTitle)
                .containsExactly("Alpha");
    }

    @Test
    void getCategories_shouldReturnCountsSortedDesc() {
        bookRepository.saveAll(List.of(
                book("A", "Science", 2020),
                book("B", "Science", 2015),
                book("C", "Fiction", 2020)));

        var counts = bookService.getCategories();

        assertThat(counts).extracting(c -> c.name()).containsExactly("Science", "Fiction");
        assertThat(counts.get(0).count()).isEqualTo(2);
    }

    @Test
    void getYears_shouldReturnYearsSortedDesc() {
        bookRepository.saveAll(List.of(
                book("A", "Science", 2020),
                book("B", "Science", 2015),
                book("C", "Fiction", 2015)));

        var years = bookService.getYears();

        assertThat(years).extracting(y -> y.year()).containsExactly(2020, 2015);
        assertThat(years.get(1).count()).isEqualTo(2);
    }
}
```

- [ ] **Step 3: Run the integration test**

Run: `mvn test -Dtest=FilterCompositionIntegrationTest`
Expected: PASS.

- [ ] **Step 4: Run the full backend test suite**

Run: `mvn test`
Expected: ALL PASS (all unit + controller + integration + existing soft-delete/bulk tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(api): register categories/years caches, add filter-composition integration tests"
```

---

## Part B — Frontend (`bookverse-frontend\`)

> Work in `C:\Workspace\VCCORP\bookverse-frontend\` from Task 4 onward. Initialize a git repo there. Commits happen inside `bookverse-frontend\`.

### Task 4: Scaffold Vite + React + TS, design system, routing shell

**Files:**
- Create: scaffold via `npm create vite`
- Create: `vite.config.ts`, `index.html`, `src/styles/tokens.css`, `src/styles/global.css`
- Modify: `src/main.tsx`
- Create: `src/App.tsx`

**Interfaces:**
- Produces: `tokens.css` CSS variables consumed by every component (must not be renamed).
- Produces: `App.tsx` exporting the router shell with `<Layout>` and placeholder routes.

- [ ] **Step 1: Scaffold the project**

Run from `C:\Workspace\VCCORP`:
```bash
npm create vite@latest bookverse-frontend -- --template react-ts
cd bookverse-frontend
npm install
npm install react-router-dom
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
git init
git add -A && git commit -m "chore: scaffold Vite React TS app"
```

- [ ] **Step 2: Configure Vite (proxy + vitest)**

Replace `vite.config.ts`:
```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

- [ ] **Step 3: Add Google Fonts to `index.html`**

Inside `<head>`, before the module script, add:
```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700&family=IBM+Plex+Mono:wght@400;500&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet" />
```
Set `<html lang="vi">` and `<title>BookVerse</title>`.

- [ ] **Step 4: Write the design tokens**

Create `src/styles/tokens.css`:
```css
:root {
  /* Palette — AI minimalism, library-warm */
  --bg: #F6F3EE;
  --surface: #FDFCFA;
  --ink: #211E1A;
  --ink-secondary: #6E675E;
  --accent: #A67C3D;
  --accent-hover: #8C652B;
  --accent-soft: rgba(166, 124, 61, 0.12);
  --hairline: #E6DFD2;
  --danger: #B5452B;
  --success: #2E5E4E;

  /* Warm lamplight shadows */
  --shadow-card: 0 2px 10px rgba(33, 30, 26, 0.06);
  --shadow-glow: 0 6px 28px rgba(166, 124, 61, 0.22);
  --shadow-pop: 0 12px 40px rgba(33, 30, 26, 0.14);

  --radius-sm: 6px;
  --radius: 12px;
  --radius-lg: 18px;

  --font-display: 'Fraunces', Georgia, serif;
  --font-body: 'Manrope', system-ui, -apple-system, sans-serif;
  --font-mono: 'IBM Plex Mono', ui-monospace, monospace;

  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px;
  --space-5: 24px; --space-6: 32px; --space-7: 40px; --space-8: 56px;

  --ease-out: cubic-bezier(0.22, 1, 0.36, 1);
  --dur-fast: 150ms;
  --dur-base: 260ms;
}
```

- [ ] **Step 5: Write global base styles**

Create `src/styles/global.css`:
```css
@import './tokens.css';

* { box-sizing: border-box; }

html, body, #root { min-height: 100%; }

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

h1, h2, h3, h4 {
  font-family: var(--font-display);
  line-height: 1.2;
  margin: 0;
  letter-spacing: -0.01em;
}

a { color: var(--accent); text-decoration: none; }
a:hover { color: var(--accent-hover); }

button {
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid var(--hairline);
  background: var(--surface);
  color: var(--ink);
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  transition: border-color var(--dur-fast), color var(--dur-fast), background var(--dur-fast), box-shadow var(--dur-base) var(--ease-out);
}
button:hover { border-color: var(--accent); color: var(--accent-hover); }
button:disabled { opacity: 0.5; cursor: not-allowed; }

input, select, textarea {
  font-family: inherit;
  font-size: 14px;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-sm);
  padding: 9px 12px;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}
input:focus, select:focus, textarea:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-5);
}

.btn-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.btn-primary:hover { background: var(--accent-hover); border-color: var(--accent-hover); color: #fff; }
.btn-danger { color: var(--danger); }
.btn-danger:hover { border-color: var(--danger); color: var(--danger); }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
}
```

- [ ] **Step 6: Write main + router shell**

Create `src/test/setup.ts`:
```ts
import '@testing-library/jest-dom/vitest';
```
Replace `src/main.tsx`:
```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './styles/global.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```
Create `src/App.tsx` with placeholder routes (Layout/Task 6 will fill the real pages):
```tsx
import { createBrowserRouter, RouterProvider } from 'react-router-dom';

const router = createBrowserRouter([
  {
    path: '/',
    element: <div className="container">BookVerse — layout & pages coming in Task 6</div>,
    children: [
      { index: true, element: <div>List (Task 8)</div> },
      { path: 'books/new', element: <div>Create (Task 10)</div> },
      { path: 'books/:id', element: <div>Detail (Task 9)</div> },
      { path: 'books/:id/edit', element: <div>Edit (Task 10)</div> },
      { path: 'import', element: <div>Import (Task 11)</div> },
      { path: '*', element: <div>Not found</div> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
```
Delete the template demo files: `src/App.css`, `src/index.css`, `src/assets/react.svg`, `public/vite.svg`.

- [ ] **Step 7: Verify build**

Run: `npm run build`
Expected: PASS (tsc + vite build, no errors). Delete any unused template imports.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore(fe): scaffold, design tokens, routing shell"
```

---

### Task 5: API layer + query builder (+ unit tests)

**Files:**
- Create: `src/api/types.ts`
- Create: `src/api/client.ts`
- Create: `src/api/books.ts`
- Create: `src/lib/query.ts`
- Test: `src/api/client.test.ts`, `src/lib/query.test.ts`

**Interfaces:**
- Produces: `interface Book`, `Page<T>`, `CategoryCount`, `YearCount`, `BulkImportResult`, `BookInput`, `BooksQuery`.
- Produces: `class ApiError` (`status`, `code`, `message`, `fieldErrors`, `path`, `rawBody`); `request<T>(path, init?): Promise<T>`; `toBookJson(input: BookInput): Record<string, unknown>`.
- Produces: `buildBooksQuery(query: BooksQuery): string` — pure, tested.
- Produces: `listBooks`, `searchBooks`, `getBook`, `deleteBook`, `createBook`, `updateBook`, `updateCover`, `getCategories`, `getYears`, `bulkImport`.
- Consumes: no other app modules.

- [ ] **Step 1: Write the failing tests**

`src/lib/query.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { buildBooksQuery } from './query';

describe('buildBooksQuery', () => {
  it('omits empty values', () => {
    expect(buildBooksQuery({})).toBe('');
  });
  it('builds a full query string', () => {
    expect(buildBooksQuery({ q: 'spring', category: 'Tech', year: 2020, sort: 'title,asc', page: 2, size: 12 }))
      .toBe('q=spring&category=Tech&year=2020&sort=title%2Casc&page=2&size=12');
  });
  it('drops undefined year but keeps page 0', () => {
    expect(buildBooksQuery({ page: 0 })).toBe('page=0');
  });
});
```
`src/api/client.test.ts`:
```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';

afterEach(() => vi.unstubAllGlobals());

describe('request', () => {
  it('parses JSON on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 })));
    const out = await request<{ ok: boolean }>('/api/x');
    expect(out.ok).toBe(true);
  });

  it('throws ApiError with code/message/fieldErrors on error', async () => {
    const body = JSON.stringify({ code: 'VALIDATION_ERROR', message: 'Validation failed', details: [{ field: 'title', message: 'must not be blank' }] });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 400, headers: { 'Content-Type': 'application/json' } })));
    await expect(request('/api/books')).rejects.toMatchObject({
      status: 400, code: 'VALIDATION_ERROR', message: 'Validation failed',
      fieldErrors: [{ field: 'title', message: 'must not be blank' }],
    } as ApiError);
  });

  it('returns undefined on 204', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    const out = await request<void>('/api/books/1', { method: 'DELETE' });
    expect(out).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test`
Expected: FAIL — modules don't exist.

- [ ] **Step 3: Write `types.ts`**

```ts
export interface Book {
  id: number;
  title: string;
  author: string;
  isbn: string | null;
  year: number | null;
  category: string | null;
  rating: number | null;
  description: string | null;
  coverPath: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CategoryCount { name: string; count: number; }
export interface YearCount { year: number; count: number; }

export interface BulkImportResult {
  totalRows: number;
  successCount: number;
  failedCount: number;
  errors: { row: number; reason: string }[];
}

/** Raw form-field values (all strings from inputs). Convert with toBookJson before sending. */
export interface BookInput {
  title: string;
  author: string;
  isbn: string;
  year: string;
  category: string;
  rating: string;
  description: string;
}

export interface BooksQuery {
  q?: string;
  category?: string;
  year?: number;
  sort?: string;
  page?: number;
  size?: number;
}
```

- [ ] **Step 4: Write `client.ts`**

```ts
import type { BookInput } from './types';

export interface FieldError { field: string; message: string; }

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: FieldError[];
  path?: string;
  rawBody?: unknown;

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], path?: string, rawBody?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.path = path;
    this.rawBody = rawBody;
  }
}

const DEBUG = import.meta.env.VITE_API_DEBUG === 'true' || import.meta.env.DEV;

async function parseError(res: Response): Promise<ApiError> {
  let raw: Record<string, unknown> | null = null;
  try { raw = (await res.json()) as Record<string, unknown>; } catch { /* empty body */ }
  const code = (raw?.code as string) ?? `HTTP_${res.status}`;
  const message = (raw?.message as string) ?? `Request failed (${res.status})`;
  const fieldErrors = (raw?.details as FieldError[]) ?? [];
  return new ApiError(res.status, code, message, fieldErrors, raw?.path as string | undefined, raw);
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? 'GET';
  const started = performance.now();
  if (DEBUG) console.debug(`[API] ${method} ${path}`);
  const res = await fetch(path, init);
  const ms = Math.round(performance.now() - started);
  if (DEBUG) console.debug(`[API] ${method} ${path} → ${res.status} in ${ms}ms`);
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** Convert form inputs (strings) to the JSON shape the backend expects. */
export function toBookJson(input: BookInput): Record<string, unknown> {
  return {
    title: input.title,
    author: input.author,
    isbn: input.isbn || null,
    year: input.year ? Number(input.year) : null,
    category: input.category || null,
    rating: input.rating ? Number(input.rating) : null,
    description: input.description || null,
  };
}

/** Build a multipart body: `book` JSON part + optional `cover` file part. */
export function bookFormData(json: Record<string, unknown>, cover?: File): FormData {
  const fd = new FormData();
  fd.append('book', new Blob([JSON.stringify(json)], { type: 'application/json' }));
  if (cover) fd.append('cover', cover);
  return fd;
}
```

- [ ] **Step 5: Write `lib/query.ts`**

```ts
import type { BooksQuery } from '../api/types';

export function buildBooksQuery(query: BooksQuery): string {
  const params = new URLSearchParams();
  if (query.q) params.set('q', query.q);
  if (query.category) params.set('category', query.category);
  if (query.year != null) params.set('year', String(query.year));
  if (query.sort) params.set('sort', query.sort);
  if (query.page != null) params.set('page', String(query.page));
  if (query.size != null) params.set('size', String(query.size));
  return params.toString();
}
```

- [ ] **Step 6: Write `books.ts`**

```ts
import { request, bookFormData, toBookJson } from './client';
import { buildBooksQuery } from '../lib/query';
import type { Book, BooksQuery, BulkImportResult, CategoryCount, Page, YearCount, BookInput } from './types';

export const listBooks = (query: BooksQuery) =>
  request<Page<Book>>(`/api/books?${buildBooksQuery(query)}`);

export const searchBooks = (query: BooksQuery) =>
  request<Page<Book>>(`/api/books/search?${buildBooksQuery(query)}`);

export const getBook = (id: number) => request<Book>(`/api/books/${id}`);

export const deleteBook = (id: number) =>
  request<void>(`/api/books/${id}`, { method: 'DELETE' });

export const createBook = (input: BookInput, cover?: File) =>
  request<Book>('/api/books', { method: 'POST', body: bookFormData(toBookJson(input), cover) });

export const updateBook = (id: number, input: BookInput, cover?: File) =>
  request<Book>(`/api/books/${id}`, { method: 'PUT', body: bookFormData(toBookJson(input), cover) });

export const updateCover = (id: number, cover: File) => {
  const fd = new FormData();
  fd.append('cover', cover);
  return request<Book>(`/api/books/${id}/cover`, { method: 'PUT', body: fd });
};

export const getCategories = () => request<CategoryCount[]>(`/api/books/categories`);
export const getYears = () => request<YearCount[]>(`/api/books/years`);

export const bulkImport = (file: File) => {
  const fd = new FormData();
  fd.append('file', file);
  return request<BulkImportResult>('/api/books/bulk', { method: 'POST', body: fd });
};
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `npm test`
Expected: ALL PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(fe): typed API layer, request logging, pure query builder with tests"
```

---

### Task 6: Layout, toast, dialog, shared UI

**Files:**
- Create: `src/components/Layout.tsx`, `src/components/Layout.css`
- Create: `src/contexts/ToastContext.tsx`, `src/components/Toast.tsx`, `src/components/Toast.css`
- Create: `src/components/ConfirmDialog.tsx`, `src/components/ConfirmDialog.css`
- Create: `src/components/Spinner.tsx`, `src/components/EmptyState.tsx`, `src/components/Field.tsx`
- Modify: `src/App.tsx`
- Create: `src/pages/NotFound.tsx`

**Interfaces:**
- Produces: `useToast()` hook returning `{ push: (t: { type: 'success'|'error'|'info'; message: string }) => void }`.
- Produces: `<Layout/>` with `<Outlet/>`; used as the router root element.
- Produces: `<ConfirmDialog open title message confirmLabel onConfirm onCancel/>`.
- Produces: `<Field label error required children/>`, `<Spinner/>`, `<EmptyState title action/>`.

- [ ] **Step 1: Toast context**

`src/contexts/ToastContext.tsx`:
```tsx
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';

export interface ToastItem { id: number; type: 'success' | 'error' | 'info'; message: string; }

interface ToastContextValue {
  push: (t: Omit<ToastItem, 'id'>) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const idRef = useRef(0);

  const push = useCallback((t: Omit<ToastItem, 'id'>) => {
    const id = ++idRef.current;
    setToasts((prev) => [...prev, { ...t, id }]);
    window.setTimeout(() => setToasts((prev) => prev.filter((x) => x.id !== id)), 3500);
  }, []);

  const value = useMemo(() => ({ push }), [push]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-viewport" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast--${t.type}`}>{t.message}</div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
```

- [ ] **Step 2: Toast styles**

`src/components/Toast.css`:
```css
.toast-viewport {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.toast {
  background: var(--ink);
  color: var(--surface);
  border-radius: var(--radius-sm);
  padding: 10px 16px;
  font-size: 14px;
  box-shadow: var(--shadow-pop);
  animation: toast-in var(--dur-base) var(--ease-out) both;
  max-width: 340px;
}
.toast--success { background: var(--success); }
.toast--error { background: var(--danger); }
@keyframes toast-in {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: none; }
}
```

- [ ] **Step 3: Layout + header**

`src/components/Layout.tsx`:
```tsx
import { NavLink, Outlet } from 'react-router-dom';
import './Layout.css';

const links = [
  { to: '/', label: 'Thư viện', end: true },
  { to: '/books/new', label: 'Thêm sách' },
  { to: '/import', label: 'Nhập hàng loạt' },
];

export default function Layout() {
  return (
    <div className="layout">
      <header className="site-header">
        <div className="container site-header__inner">
          <NavLink to="/" className="wordmark">BookVerse<span>.</span></NavLink>
          <nav className="site-nav">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end}
                className={({ isActive }) => `site-nav__link${isActive ? ' is-active' : ''}`}>
                {l.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      <main className="site-main">
        <Outlet />
      </main>
    </div>
  );
}
```
`src/components/Layout.css`:
```css
.site-header { border-bottom: 1px solid var(--hairline); background: rgba(246, 243, 238, 0.8); backdrop-filter: blur(8px); position: sticky; top: 0; z-index: 50; }
.site-header__inner { display: flex; align-items: baseline; justify-content: space-between; padding-top: 16px; padding-bottom: 16px; }
.wordmark { font-family: var(--font-display); font-size: 24px; font-weight: 600; color: var(--ink); }
.wordmark span { color: var(--accent); }
.site-nav { display: flex; gap: 20px; }
.site-nav__link { color: var(--ink-secondary); font-size: 14px; padding: 6px 2px; border-bottom: 2px solid transparent; }
.site-nav__link:hover { color: var(--ink); }
.site-nav__link.is-active { color: var(--ink); border-bottom-color: var(--accent); }
.site-main { padding: var(--space-6) 0 var(--space-8); }
@media (max-width: 640px) {
  .site-header__inner { flex-direction: column; gap: 12px; align-items: flex-start; }
}
```

- [ ] **Step 4: ConfirmDialog, Spinner, EmptyState, Field**

`src/components/ConfirmDialog.tsx`:
```tsx
import './ConfirmDialog.css';

interface Props {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({ open, title, message, confirmLabel, busy, onConfirm, onCancel }: Props) {
  if (!open) return null;
  return (
    <div className="dialog-backdrop" onClick={onCancel}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h3>{title}</h3>
        <p className="dialog__message">{message}</p>
        <div className="dialog__actions">
          <button onClick={onCancel} disabled={busy}>Hủy</button>
          <button className="btn-danger" onClick={onConfirm} disabled={busy}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
```
`src/components/ConfirmDialog.css`:
```css
.dialog-backdrop { position: fixed; inset: 0; background: rgba(33, 30, 26, 0.4); display: grid; place-items: center; z-index: 90; padding: 16px; }
.dialog { background: var(--surface); border-radius: var(--radius); padding: 24px; max-width: 400px; width: 100%; box-shadow: var(--shadow-pop); }
.dialog__message { color: var(--ink-secondary); margin: 12px 0 20px; }
.dialog__actions { display: flex; justify-content: flex-end; gap: 8px; }
```

`src/components/Spinner.tsx`:
```tsx
import './Spinner.css';
export default function Spinner() {
  return (
    <div className="spinner-wrap" aria-label="Đang tải" role="status">
      <div className="spinner" />
    </div>
  );
}
```
`src/components/Spinner.css`:
```css
.spinner-wrap { display: grid; place-items: center; padding: 48px 0; }
.spinner { width: 28px; height: 28px; border: 3px solid var(--hairline); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
```

`src/components/EmptyState.tsx`:
```tsx
import type { ReactNode } from 'react';
import './EmptyState.css';
export default function EmptyState({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <div className="empty-state__mark">📖</div>
      <p>{title}</p>
      {action}
    </div>
  );
}
```
`src/components/EmptyState.css`:
```css
.empty-state { text-align: center; padding: 56px 24px; color: var(--ink-secondary); }
.empty-state__mark { font-size: 40px; margin-bottom: 8px; }
```

`src/components/Field.tsx`:
```tsx
import type { ReactNode } from 'react';
import './Field.css';
interface Props { label: string; required?: boolean; error?: string; htmlFor: string; children: ReactNode; }
export default function Field({ label, required, error, htmlFor, children }: Props) {
  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      <label className="field__label" htmlFor={htmlFor}>
        {label}{required && <span className="field__req"> *</span>}
      </label>
      {children}
      {error && <p className="field__error" role="alert">{error}</p>}
    </div>
  );
}
```
`src/components/Field.css`:
```css
.field { display: flex; flex-direction: column; gap: 6px; }
.field__label { font-size: 13px; font-weight: 600; color: var(--ink-secondary); }
.field__req { color: var(--danger); }
.field input, .field select, .field textarea { width: 100%; }
.field__error { color: var(--danger); font-size: 13px; margin: 0; }
.field--error input, .field--error select, .field--error textarea { border-color: var(--danger); }
```

- [ ] **Step 5: NotFound page**

`src/pages/NotFound.tsx`:
```tsx
import { Link } from 'react-router-dom';
import EmptyState from '../components/EmptyState';
export default function NotFound() {
  return (
    <div className="container">
      <EmptyState title="Không tìm thấy trang này."
        action={<Link to="/">Về trang chủ</Link>} />
    </div>
  );
}
```

- [ ] **Step 6: Wire layout + toast into the router**

Replace `src/App.tsx`:
```tsx
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { ToastProvider } from './contexts/ToastContext';
import Layout from './components/Layout';
import NotFound from './pages/NotFound';

const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <div className="container">List — Task 8</div> },
      { path: 'books/new', element: <div className="container">Create — Task 10</div> },
      { path: 'books/:id', element: <div className="container">Detail — Task 9</div> },
      { path: 'books/:id/edit', element: <div className="container">Edit — Task 10</div> },
      { path: 'import', element: <div className="container">Import — Task 11</div> },
      { path: '*', element: <NotFound /> },
    ],
  },
]);

export default function App() {
  return (
    <ToastProvider>
      <RouterProvider router={router} />
    </ToastProvider>
  );
}
```

- [ ] **Step 7: Verify build + smoke**

Run: `npm run build` → PASS.
Run: `npm run dev` and open `http://localhost:5173` → header renders with nav, routes render placeholders.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(fe): layout shell, toast system, confirm dialog, shared UI"
```

---

### Task 7: Book display components (card, cover, rating, pagination, filter bar)

**Files:**
- Create: `src/components/BookCard.tsx`, `BookCard.css`
- Create: `src/components/BookGrid.tsx`, `BookGrid.css`
- Create: `src/components/CoverImage.tsx`, `CoverImage.css`
- Create: `src/components/StarRating.tsx`, `StarRating.css`
- Create: `src/components/CategoryBadge.tsx`
- Create: `src/components/Pagination.tsx`, `Pagination.css`
- Create: `src/components/FilterBar.tsx`, `FilterBar.css`

**Interfaces:**
- Produces: `<CoverImage bookId size="thumb|medium|large" alt className/>` — 2:3 lazy image; shows a warm placeholder on 404/loading.
- Produces: `<StarRating value onValueChange?/>` — 5 stars, supports half ratings display, clickable when `onValueChange` provided.
- Produces: `<BookCard book/>` → links to `/books/{id}`.
- Produces: `<Pagination page totalPages onPageChange/>`.
- Produces: `<FilterBar value onFilter categories years/>` where `value: BooksQuery` and `onFilter: (patch: Partial<BooksQuery>) => void`.
- Consumes: `Book`, `BooksQuery`, `CategoryCount`, `YearCount` types.

- [ ] **Step 1: CoverImage**

`src/components/CoverImage.tsx`:
```tsx
import { useState } from 'react';
import './CoverImage.css';

interface Props {
  bookId: number;
  size?: 'thumb' | 'medium' | 'large';
  alt: string;
  className?: string;
}

export default function CoverImage({ bookId, size = 'medium', alt, className }: Props) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <div className={`cover-placeholder ${className ?? ''}`} role="img" aria-label={alt}>
        <span className="cover-placeholder__spine" />
        <span className="cover-placeholder__text">{alt}</span>
      </div>
    );
  }
  return (
    <img
      className={`cover-image ${className ?? ''}`}
      src={`/api/books/${bookId}/cover?size=${size}`}
      alt={alt}
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}
```
`src/components/CoverImage.css` — the signature "lamplight glow" lives here:
```css
.cover-image, .cover-placeholder {
  width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}
.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(160deg, var(--surface), var(--hairline));
  color: var(--ink-secondary);
  padding: 16px;
  text-align: center;
  font-size: 12px;
}
.cover-placeholder__spine {
  position: absolute; left: 8px; top: 0; bottom: 0;
  width: 4px; border-radius: 2px;
  background: var(--accent-soft);
}
.cover-placeholder__text {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
```

- [ ] **Step 2: StarRating**

`src/components/StarRating.tsx`:
```tsx
import './StarRating.css';

interface Props {
  value: number | null;
  onValueChange?: (v: number) => void;
}

export default function StarRating({ value, onValueChange }: Props) {
  const rating = value ?? 0;
  return (
    <div className="stars" role="img" aria-label={value != null ? `Đánh giá ${rating} trên 5` : 'Chưa có đánh giá'}>
      {[1, 2, 3, 4, 5].map((i) => {
        const filled = rating >= i - 0.25;
        const half = !filled && rating >= i - 0.75;
        return (
          <span
            key={i}
            className={`star${filled ? ' is-filled' : ''}${half ? ' is-half' : ''}`}
            onClick={onValueChange ? () => onValueChange(i) : undefined}
            aria-hidden="true"
          >★</span>
        );
      })}
    </div>
  );
}
```
`src/components/StarRating.css`:
```css
.stars { display: inline-flex; gap: 2px; color: var(--hairline); font-size: 16px; line-height: 1; }
.star { cursor: default; }
.star.is-filled { color: var(--accent); }
.star.is-half { background: linear-gradient(90deg, var(--accent) 50%, var(--hairline) 50%); -webkit-background-clip: text; background-clip: text; color: transparent; }
.stars[data-clickable] .star { cursor: pointer; }
```

- [ ] **Step 3: CategoryBadge**

`src/components/CategoryBadge.tsx`:
```tsx
export default function CategoryBadge({ name }: { name: string }) {
  return <span className="badge">{name}</span>;
}
```
Add `.badge` to `src/components/BookCard.css`:
```css
.badge { display: inline-block; background: var(--accent-soft); color: var(--accent-hover); border-radius: 999px; padding: 2px 10px; font-size: 12px; font-weight: 600; }
```

- [ ] **Step 4: BookCard**

`src/components/BookCard.tsx`:
```tsx
import { Link } from 'react-router-dom';
import type { Book } from '../api/types';
import CoverImage from './CoverImage';
import StarRating from './StarRating';
import CategoryBadge from './CategoryBadge';
import './BookCard.css';

export default function BookCard({ book }: { book: Book }) {
  return (
    <article className="book-card">
      <Link to={`/books/${book.id}`} className="book-card__cover-link">
        <CoverImage bookId={book.id} size="medium" alt={book.title} />
      </Link>
      <div className="book-card__body">
        <h3 className="book-card__title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h3>
        <p className="book-card__author">{book.author}</p>
        <div className="book-card__meta">
          <StarRating value={book.rating} />
          {book.category && <CategoryBadge name={book.category} />}
        </div>
      </div>
    </article>
  );
}
```
`src/components/BookCard.css` (card entrance stagger + glow on hover):
```css
.book-card { display: flex; flex-direction: column; gap: 12px; animation: fade-up var(--dur-base) var(--ease-out) both; }
.book-card:hover .cover-image, .book-card:hover .cover-placeholder { box-shadow: var(--shadow-glow); }
.book-card__cover-link { display: block; }
.book-card__title { font-size: 17px; font-weight: 600; }
.book-card__title a { color: var(--ink); }
.book-card__title a:hover { color: var(--accent-hover); }
.book-card__author { color: var(--ink-secondary); font-size: 14px; margin: 2px 0 8px; }
.book-card__meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
@keyframes fade-up { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }
```

- [ ] **Step 5: BookGrid**

`src/components/BookGrid.tsx`:
```tsx
import type { Book } from '../api/types';
import BookCard from './BookCard';
import './BookGrid.css';

export default function BookGrid({ books }: { books: Book[] }) {
  return (
    <div className="book-grid">
      {books.map((b) => <BookCard key={b.id} book={b} />)}
    </div>
  );
}
```
`src/components/BookGrid.css`:
```css
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 28px 20px; }
@media (min-width: 1024px) { .book-grid { grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); } }
```

- [ ] **Step 6: Pagination**

`src/components/Pagination.tsx`:
```tsx
import './Pagination.css';

interface Props {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export default function Pagination({ page, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null;
  const pages = Array.from({ length: totalPages }, (_, i) => i);
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button disabled={page === 0} onClick={() => onPageChange(page - 1)}>‹ Trước</button>
      {pages.map((p) => (
        <button key={p} className={p === page ? 'is-current' : ''} onClick={() => onPageChange(p)}>
          {p + 1}
        </button>
      ))}
      <button disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>Sau ›</button>
    </nav>
  );
}
```
`src/components/Pagination.css`:
```css
.pagination { display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: 40px; flex-wrap: wrap; }
.pagination button { min-width: 36px; }
.pagination button.is-current { background: var(--accent); border-color: var(--accent); color: #fff; }
```

- [ ] **Step 7: FilterBar**

`src/components/FilterBar.tsx`:
```tsx
import type { BooksQuery, CategoryCount, YearCount } from '../api/types';
import './FilterBar.css';

interface Props {
  value: BooksQuery;
  categories: CategoryCount[];
  years: YearCount[];
  onFilter: (patch: Partial<BooksQuery>) => void;
}

export default function FilterBar({ value, categories, years, onFilter }: Props) {
  return (
    <div className="filter-bar">
      <input
        className="filter-bar__search"
        type="search"
        placeholder="Tìm theo tiêu đề hoặc tác giả…"
        value={value.q ?? ''}
        onChange={(e) => onFilter({ q: e.target.value })}
        aria-label="Tìm kiếm"
      />
      <select
        value={value.category ?? ''}
        onChange={(e) => onFilter({ category: e.target.value || undefined })}
        aria-label="Lọc theo thể loại"
      >
        <option value="">Mọi thể loại</option>
        {categories.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
      </select>
      <select
        value={value.year != null ? String(value.year) : ''}
        onChange={(e) => onFilter({ year: e.target.value ? Number(e.target.value) : undefined })}
        aria-label="Lọc theo năm"
      >
        <option value="">Mọi năm</option>
        {years.map((y) => <option key={y.year} value={y.year}>{y.year}</option>)}
      </select>
      <select
        value={value.sort ?? 'createdAt,desc'}
        onChange={(e) => onFilter({ sort: e.target.value })}
        aria-label="Sắp xếp"
      >
        <option value="createdAt,desc">Mới nhất</option>
        <option value="title,asc">Tiêu đề A–Z</option>
        <option value="year,desc">Năm mới trước</option>
        <option value="rating,desc">Đánh giá cao</option>
      </select>
    </div>
  );
}
```
`src/components/FilterBar.css`:
```css
.filter-bar { display: grid; grid-template-columns: 1fr auto auto auto; gap: 10px; align-items: center; padding: 14px; background: var(--surface); border: 1px solid var(--hairline); border-radius: var(--radius); margin-bottom: 28px; }
.filter-bar__search { min-width: 200px; }
@media (max-width: 720px) { .filter-bar { grid-template-columns: 1fr; } }
```

- [ ] **Step 8: Verify build**

Run: `npm run build`
Expected: PASS. (Components aren't mounted yet — that's fine, `tsc` still type-checks them.)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(fe): book display components — card, cover, stars, filter bar, pagination"
```

---

### Task 8: BookList page

**Files:**
- Create: `src/pages/BookList.tsx`, `BookList.css`

**Interfaces:**
- Consumes: `listBooks`, `searchBooks`, `getCategories`, `getYears`, `BooksQuery`, `buildBooksQuery` behavior, `FilterBar`, `BookGrid`, `Pagination`, `Spinner`, `EmptyState`, `ApiError`.
- Produces: route `/` — the library grid with filter/search/sort/pagination driven by URL search params.

- [ ] **Step 1: Write the page**

`src/pages/BookList.tsx`:
```tsx
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listBooks, searchBooks, getCategories, getYears } from '../api/books';
import type { Book, BooksQuery, CategoryCount, YearCount, Page } from '../api/types';
import FilterBar from '../components/FilterBar';
import BookGrid from '../components/BookGrid';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import { useToast } from '../contexts/ToastContext';
import './BookList.css';

export default function BookList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState<Page<Book> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [categories, setCategories] = useState<CategoryCount[]>([]);
  const [years, setYears] = useState<YearCount[]>([]);
  const { push } = useToast();

  const query: BooksQuery = {
    q: searchParams.get('q') ?? undefined,
    category: searchParams.get('category') ?? undefined,
    year: searchParams.get('year') ? Number(searchParams.get('year')) : undefined,
    sort: searchParams.get('sort') ?? 'createdAt,desc',
    page: Number(searchParams.get('page') ?? '0'),
    size: 24,
  };

  useEffect(() => {
    getCategories().then(setCategories).catch(() => { /* filters are best-effort */ });
    getYears().then(setYears).catch(() => { /* filters are best-effort */ });
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const fetcher = query.q ? searchBooks(query) : listBooks(query);
    fetcher
      .then((p) => { if (!cancelled) setPage(p); })
      .catch((e: Error) => { if (!cancelled) { setError(e); setPage(null); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [searchParams, query.q, query.category, query.year, query.sort, query.page]);

  const applyFilter = useCallback((patch: Partial<BooksQuery>) => {
    const next = { ...query, ...patch, page: 0 };
    const params = new URLSearchParams();
    if (next.q) params.set('q', next.q);
    if (next.category) params.set('category', next.category);
    if (next.year != null) params.set('year', String(next.year));
    if (next.sort && next.sort !== 'createdAt,desc') params.set('sort', next.sort);
    if (next.page > 0) params.set('page', String(next.page));
    setSearchParams(params);
  }, [query, setSearchParams]);

  const goToPage = useCallback((p: number) => applyFilter({ page: p }), [applyFilter]);

  const retry = () => {
    push({ type: 'info', message: 'Đang thử lại…' });
    setSearchParams(new URLSearchParams(searchParams));
  };

  return (
    <div className="container">
      <h1 className="list-title">Thư viện</h1>
      <FilterBar value={query} categories={categories} years={years} onFilter={applyFilter} />

      {error && (
        <div className="error-banner" role="alert">
          <strong>{error.message}</strong>
          <button onClick={retry}>Thử lại</button>
        </div>
      )}

      {loading && !page && <Spinner />}

      {!loading && page && page.content.length === 0 && (
        <EmptyState title="Không tìm thấy sách nào." />
      )}

      {page && page.content.length > 0 && (
        <>
          <p className="result-count">{page.totalElements} cuốn sách</p>
          <BookGrid books={page.content} />
          <Pagination page={page.number} totalPages={page.totalPages} onPageChange={goToPage} />
        </>
      )}
    </div>
  );
}
```
`src/pages/BookList.css`:
```css
.list-title { font-size: 30px; margin-bottom: 20px; }
.result-count { color: var(--ink-secondary); font-size: 13px; margin: 0 0 12px; }
.error-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; background: #FBEDE7; border: 1px solid var(--danger); color: var(--danger); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; }
```

- [ ] **Step 2: Wire the route**

In `src/App.tsx`, replace the placeholder `{ index: true, element: ... }` with:
```tsx
import BookList from './pages/BookList';
// ...
{ index: true, element: <BookList /> },
```

- [ ] **Step 3: Verify in dev**

With the backend running (`mvn spring-boot:run` in `bookverse-api\`), run `npm run dev` and open `http://localhost:5173`.
Check: grid renders seeded books, typing in search filters (via `/search`), category/year dropdowns filter (via `/books?category=`), sort + pagination work, console shows `[API] GET ... → 200` lines.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(fe): library list page with URL-driven filters, search, pagination"
```

---

### Task 9: BookDetail page

**Files:**
- Create: `src/pages/BookDetail.tsx`, `BookDetail.css`

**Interfaces:**
- Consumes: `getBook`, `deleteBook`, `updateCover`, `getCategories` (for suggested categories on edit link? no — simple), `useToast`, `ConfirmDialog`, `StarRating`, `CoverImage`, `ApiError`.
- Produces: route `/books/:id`.

- [ ] **Step 1: Write the page**

`src/pages/BookDetail.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { deleteBook, getBook, updateCover } from '../api/books';
import type { Book } from '../api/types';
import CoverImage from '../components/CoverImage';
import StarRating from '../components/StarRating';
import CategoryBadge from '../components/CategoryBadge';
import ConfirmDialog from '../components/ConfirmDialog';
import Spinner from '../components/Spinner';
import { useToast } from '../contexts/ToastContext';
import './BookDetail.css';

type CoverSize = 'thumb' | 'medium' | 'large';

export default function BookDetail() {
  const { id } = useParams();
  const bookId = Number(id);
  const navigate = useNavigate();
  const { push } = useToast();
  const [book, setBook] = useState<Book | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [size, setSize] = useState<CoverSize>('medium');
  const coverInputRef = useRef<HTMLInputElement>(null);

  const load = () => {
    setLoading(true);
    getBook(bookId)
      .then(setBook)
      .catch((e) => { if (e?.status === 404) setNotFound(true); else push({ type: 'error', message: e?.message ?? 'Không tải được sách.' }); })
      .finally(() => setLoading(false));
  };

  useEffect(load, [bookId]);

  async function handleDelete() {
    setDeleting(true);
    try {
      await deleteBook(bookId);
      push({ type: 'success', message: 'Đã xóa sách.' });
      navigate('/');
    } catch (e: any) {
      push({ type: 'error', message: e?.message ?? 'Xóa thất bại.' });
      setDeleting(false);
    }
  }

  async function handleCoverChange(file: File) {
    try {
      await updateCover(bookId, file);
      push({ type: 'success', message: 'Đã cập nhật ảnh bìa.' });
      load();
    } catch (e: any) {
      push({ type: 'error', message: e?.message ?? 'Cập nhật ảnh bìa thất bại.' });
    }
  }

  if (loading) return <div className="container"><Spinner /></div>;
  if (notFound) return <div className="container"><EmptyState title="Không tìm thấy sách này." action={<Link to="/">Về trang chủ</Link>} /></div>;
  if (!book) return null;

  return (
    <div className="container">
      <Link to="/" className="back-link">← Thư viện</Link>
      <div className="detail">
        <div className="detail__cover">
          <CoverImage bookId={book.id} size={size} alt={book.title} />
          <div className="cover-size" role="group" aria-label="Kích thước ảnh bìa">
            {(['thumb', 'medium', 'large'] as CoverSize[]).map((s) => (
              <button key={s} className={s === size ? 'is-current' : ''} onClick={() => setSize(s)}>{s}</button>
            ))}
          </div>
        </div>
        <div className="detail__info">
          <h1>{book.title}</h1>
          <p className="detail__author">{book.author}</p>
          <div className="detail__row">
            <StarRating value={book.rating} />
            {book.category && <CategoryBadge name={book.category} />}
          </div>
          <dl className="detail__facts">
            {book.isbn && <div><dt>ISBN</dt><dd className="mono">{book.isbn}</dd></div>}
            {book.year != null && <div><dt>Năm</dt><dd>{book.year}</dd></div>}
          </dl>
          {book.description && <p className="detail__desc">{book.description}</p>}
          <div className="detail__actions">
            <Link to={`/books/${book.id}/edit`} className="btn-primary">Sửa</Link>
            <button onClick={() => setConfirmOpen(true)} className="btn-danger">Xóa</button>
            <button onClick={() => coverInputRef.current?.click()}>Đổi ảnh bìa</button>
            <input
              ref={coverInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              hidden
              onChange={(e) => { const f = e.target.files?.[0]; if (f) handleCoverChange(f); e.target.value = ''; }}
            />
          </div>
        </div>
      </div>
      <ConfirmDialog
        open={confirmOpen}
        title="Xóa sách"
        message={`Bạn chắc chắn muốn xóa "${book.title}"?`}
        confirmLabel="Xóa"
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
```
(Add `import { useRef } from 'react';` and `import EmptyState from '../components/EmptyState';`.)

`src/pages/BookDetail.css`:
```css
.back-link { display: inline-block; margin-bottom: 20px; color: var(--ink-secondary); font-size: 14px; }
.detail { display: grid; grid-template-columns: 280px 1fr; gap: 40px; align-items: start; }
.detail__cover { display: flex; flex-direction: column; gap: 12px; }
.cover-size { display: flex; gap: 6px; }
.cover-size button { padding: 4px 10px; font-size: 12px; }
.cover-size button.is-current { background: var(--accent); border-color: var(--accent); color: #fff; }
.detail__info h1 { font-size: 34px; margin-bottom: 4px; }
.detail__author { color: var(--ink-secondary); font-size: 16px; margin: 0 0 16px; }
.detail__row { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.detail__facts { display: flex; flex-direction: column; gap: 6px; margin: 0 0 20px; font-size: 14px; }
.detail__facts div { display: flex; gap: 8px; }
.detail__facts dt { color: var(--ink-secondary); min-width: 60px; }
.detail__facts dd { margin: 0; }
.mono { font-family: var(--font-mono); font-size: 13px; }
.detail__desc { color: var(--ink-secondary); line-height: 1.7; margin: 0 0 24px; white-space: pre-wrap; }
.detail__actions { display: flex; gap: 10px; }
@media (max-width: 720px) { .detail { grid-template-columns: 1fr; } }
```

- [ ] **Step 2: Wire the route**

In `src/App.tsx`, replace the `books/:id` placeholder with `<BookDetail />`.

- [ ] **Step 3: Verify in dev**

Open a book's detail from the list. Check: cover renders and the size buttons switch `thumb`/`medium`/`large`; stars, badge, ISBN in mono; Edit navigates; Delete confirms then returns to list; "Đổi ảnh bìa" uploads a JPG/PNG and the cover refreshes.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(fe): book detail page with cover sizes, delete, cover change"
```

---

### Task 10: BookForm page (create/edit)

**Files:**
- Create: `src/pages/BookForm.tsx`, `BookForm.css`

**Interfaces:**
- Consumes: `getBook`, `createBook`, `updateBook`, `getCategories`, `ApiError`, `Field`, `useToast`, `CoverImage`.
- Produces: routes `/books/new` (create) and `/books/:id/edit` (edit). Reads `:id` from `useParams` — present = edit, absent = create.

- [ ] **Step 1: Write the page**

`src/pages/BookForm.tsx`:
```tsx
import { useEffect, useState, type ChangeEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createBook, getBook, getCategories, updateBook } from '../api/books';
import type { Book, BookInput } from '../api/types';
import Field from '../components/Field';
import Spinner from '../components/Spinner';
import CoverImage from '../components/CoverImage';
import { useToast } from '../contexts/ToastContext';
import { ApiError } from '../api/client';
import './BookForm.css';

const empty: BookInput = { title: '', author: '', isbn: '', year: '', category: '', rating: '', description: '' };

export default function BookForm() {
  const { id } = useParams();
  const isEdit = id != null;
  const bookId = isEdit ? Number(id) : 0;
  const navigate = useNavigate();
  const { push } = useToast();

  const [form, setForm] = useState<BookInput>(empty);
  const [cover, setCover] = useState<File | null>(null);
  const [coverPreview, setCoverPreview] = useState<string | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => { getCategories().then((cs) => setCategories(cs.map((c) => c.name))).catch(() => {}); }, []);

  useEffect(() => {
    if (!isEdit) return;
    getBook(bookId)
      .then((b: Book) => setForm({
        title: b.title, author: b.author, isbn: b.isbn ?? '', year: b.year != null ? String(b.year) : '',
        category: b.category ?? '', rating: b.rating != null ? String(b.rating) : '', description: b.description ?? '',
      }))
      .catch((e: ApiError) => { push({ type: 'error', message: e?.message ?? 'Không tải được sách.' }); navigate('/'); })
      .finally(() => setLoading(false));
  }, [bookId, isEdit, navigate, push]);

  const set = (key: keyof BookInput) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  function pickCover(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setCover(f);
    setCoverPreview(URL.createObjectURL(f));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      if (isEdit) {
        await updateBook(bookId, form, cover ?? undefined);
        push({ type: 'success', message: 'Đã lưu thay đổi.' });
      } else {
        const created = await createBook(form, cover ?? undefined);
        push({ type: 'success', message: 'Đã thêm sách.' });
        navigate(`/books/${created.id}`);
        return;
      }
      navigate(`/books/${bookId}`);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length) {
        const map: Record<string, string> = {};
        err.fieldErrors.forEach((fe) => { map[fe.field] = fe.message; });
        setErrors(map);
        push({ type: 'error', message: err.message });
      } else {
        push({ type: 'error', message: err instanceof Error ? err.message : 'Không thể lưu sách.' });
      }
      setSaving(false);
    }
  }

  if (loading) return <div className="container"><Spinner /></div>;

  return (
    <div className="container form-wrap">
      <h1>{isEdit ? 'Sửa sách' : 'Thêm sách'}</h1>
      <form onSubmit={handleSubmit} className="book-form">
        <div className="book-form__grid">
          <div className="book-form__fields">
            <Field label="Tiêu đề" required error={errors.title} htmlFor="title">
              <input id="title" value={form.title} onChange={set('title')} placeholder="Ví dụ: Dế Mèn Phiêu Lưu Ký" />
            </Field>
            <Field label="Tác giả" required error={errors.author} htmlFor="author">
              <input id="author" value={form.author} onChange={set('author')} placeholder="Ví dụ: Tô Hoài" />
            </Field>
            <Field label="ISBN" error={errors.isbn} htmlFor="isbn">
              <input id="isbn" className="mono" value={form.isbn} onChange={set('isbn')} placeholder="978-…" />
            </Field>
            <div className="book-form__row">
              <Field label="Năm xuất bản" error={errors.year} htmlFor="year">
                <input id="year" type="number" min="0" max="2100" value={form.year} onChange={set('year')} placeholder="2024" />
              </Field>
              <Field label="Đánh giá (0–5)" error={errors.rating} htmlFor="rating">
                <input id="rating" type="number" min="0" max="5" step="0.1" value={form.rating} onChange={set('rating')} placeholder="4.5" />
              </Field>
            </div>
            <Field label="Thể loại" error={errors.category} htmlFor="category">
              <input id="category" list="category-options" value={form.category} onChange={set('category')} placeholder="Chọn hoặc nhập thể loại" />
              <datalist id="category-options">
                {categories.map((c) => <option key={c} value={c} />)}
              </datalist>
            </Field>
            <Field label="Mô tả" error={errors.description} htmlFor="description">
              <textarea id="description" rows={5} value={form.description} onChange={set('description')} />
            </Field>
          </div>
          <div className="book-form__cover">
            <label className="field__label">Ảnh bìa</label>
            {coverPreview ? (
              <img className="cover-preview" src={coverPreview} alt="Xem trước ảnh bìa" />
            ) : isEdit ? (
              <CoverImage bookId={bookId} size="medium" alt={form.title || 'Ảnh bìa'} />
            ) : (
              <div className="cover-empty">Chưa có ảnh — chọn tệp bên dưới</div>
            )}
            <input type="file" accept="image/jpeg,image/png,image/webp" onChange={pickCover} />
            {isEdit && <p className="hint">Chọn ảnh mới để thay thế ảnh hiện tại khi lưu.</p>}
          </div>
        </div>
        <div className="book-form__actions">
          <button type="button" onClick={() => navigate(-1)}>Hủy</button>
          <button type="submit" className="btn-primary" disabled={saving}>{saving ? 'Đang lưu…' : (isEdit ? 'Lưu thay đổi' : 'Thêm sách')}</button>
        </div>
      </form>
    </div>
  );
}
```
`src/pages/BookForm.css`:
```css
.form-wrap h1 { font-size: 28px; margin-bottom: 24px; }
.book-form__grid { display: grid; grid-template-columns: 1fr 260px; gap: 32px; }
.book-form__fields { display: flex; flex-direction: column; gap: 16px; }
.book-form__row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.book-form__cover { display: flex; flex-direction: column; gap: 8px; }
.cover-preview, .cover-empty { width: 100%; aspect-ratio: 2 / 3; object-fit: cover; border-radius: var(--radius); }
.cover-empty { display: grid; place-items: center; background: var(--hairline); color: var(--ink-secondary); font-size: 13px; text-align: center; padding: 16px; border: 1px dashed var(--ink-secondary); }
.hint { color: var(--ink-secondary); font-size: 12px; margin: 0; }
.book-form__actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 28px; }
@media (max-width: 720px) { .book-form__grid { grid-template-columns: 1fr; } }
```

- [ ] **Step 2: Wire routes**

In `src/App.tsx`, replace `books/new` and `books/:id/edit` placeholders with `<BookForm />` (both routes use the same component; `:id` presence decides the mode).

- [ ] **Step 3: Verify in dev**

Create a book with an image → appears in list with cover. Edit it (change title + cover) → detail reflects changes. Try submitting with blank title → inline field error from backend `VALIDATION_ERROR` + toast. Edit an existing book and use the cover file input → PUT multipart updates cover.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(fe): shared create/edit book form with cover upload and field errors"
```

---

### Task 11: BulkImport page

**Files:**
- Create: `src/pages/BulkImport.tsx`, `BulkImport.css`

**Interfaces:**
- Consumes: `bulkImport`, `BulkImportResult`, `useToast`, `ApiError`, `Spinner`.
- Produces: route `/import`.

- [ ] **Step 1: Write the page**

`src/pages/BulkImport.tsx`:
```tsx
import { useRef, useState } from 'react';
import { bulkImport } from '../api/books';
import type { BulkImportResult } from '../api/types';
import { useToast } from '../contexts/ToastContext';
import Spinner from '../components/Spinner';
import './BulkImport.css';

const TEMPLATE_HEADER = 'title,author,isbn,year,category,rating,description';

export default function BulkImport() {
  const { push } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<BulkImportResult | null>(null);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function downloadTemplate() {
    const blob = new Blob([
      TEMPLATE_HEADER + '\n' +
      'Ví dụ sách 1,Tác giả A,978-TPL-1,2024,Tiểu thuyết,4.5,Mô tả ngắn\n' +
      'Ví dụ sách 2,Tác giả B,978-TPL-2,2020,Khoa học,4.0,Mô tả ngắn',
    ], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'books-template.csv'; a.click();
    URL.revokeObjectURL(url);
  }

  async function handleUpload() {
    if (!file) return;
    setBusy(true);
    setResult(null);
    try {
      const r = await bulkImport(file);
      setResult(r);
      push({ type: 'success', message: `Đã nhập ${r.successCount} sách.` });
    } catch (e: any) {
      push({ type: 'error', message: e?.message ?? 'Nhập hàng loạt thất bại.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container import-wrap">
      <h1>Nhập hàng loạt</h1>
      <p className="import-intro">
        Upload tệp <strong>.csv</strong> hoặc <strong>.xlsx</strong> theo mẫu. Dòng có ISBN trùng lặp sẽ bị bỏ qua và liệt kê trong kết quả.
      </p>
      <button onClick={downloadTemplate} className="import-template">Tải mẫu CSV</button>

      <div className="import-drop">
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.xlsx"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        {file && <p className="import-file">{file.name} · {(file.size / 1024).toFixed(1)} KB</p>}
      </div>

      <button className="btn-primary" onClick={handleUpload} disabled={!file || busy}>
        {busy ? 'Đang nhập…' : 'Nhập vào thư viện'}
      </button>

      {busy && <Spinner />}

      {result && (
        <section className="import-result" aria-label="Kết quả nhập">
          <div className="result-cards">
            <div className="result-card"><strong>{result.totalRows}</strong><span>Tổng dòng</span></div>
            <div className="result-card result-card--ok"><strong>{result.successCount}</strong><span>Thành công</span></div>
            <div className="result-card result-card--bad"><strong>{result.failedCount}</strong><span>Thất bại</span></div>
          </div>
          {result.errors.length > 0 && (
            <table className="error-table">
              <thead><tr><th>Dòng</th><th>Lý do</th></tr></thead>
              <tbody>
                {result.errors.map((err, i) => (
                  <tr key={i}><td>{err.row}</td><td>{err.reason}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
```
`src/pages/BulkImport.css`:
```css
.import-wrap h1 { font-size: 28px; margin-bottom: 8px; }
.import-intro { color: var(--ink-secondary); max-width: 560px; }
.import-template { margin: 16px 0; font-size: 13px; }
.import-drop { border: 1px dashed var(--hairline); border-radius: var(--radius); padding: 24px; margin: 8px 0 16px; background: var(--surface); }
.import-file { color: var(--ink-secondary); margin: 8px 0 0; }
.import-result { margin-top: 32px; }
.result-cards { display: flex; gap: 12px; margin-bottom: 20px; }
.result-card { flex: 1; background: var(--surface); border: 1px solid var(--hairline); border-radius: var(--radius); padding: 16px; display: flex; flex-direction: column; }
.result-card strong { font-family: var(--font-display); font-size: 28px; }
.result-card span { color: var(--ink-secondary); font-size: 13px; }
.result-card--ok strong { color: var(--success); }
.result-card--bad strong { color: var(--danger); }
.error-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.error-table th, .error-table td { border: 1px solid var(--hairline); padding: 8px 12px; text-align: left; }
.error-table th { background: var(--surface); }
```

- [ ] **Step 2: Wire the route**

In `src/App.tsx`, replace the `import` placeholder with `<BulkImport />`.

- [ ] **Step 3: Verify in dev**

Download the template, edit a row to duplicate an ISBN, upload → result cards show counts and the error table lists the duplicate row. Also test `.xlsx`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(fe): bulk import page with template download and result report"
```

---

### Task 12: Frontend unit tests + full verification

**Files:**
- Create: `src/pages/BookForm.test.tsx`
- Create: `src/pages/BookList.test.tsx`
- Create: `src/components/Pagination.test.tsx`

**Interfaces:**
- Consumes: components/pages from Tasks 5–11; `vi` + RTL.

- [ ] **Step 1: BookForm validation test**

`src/pages/BookForm.test.tsx`:
```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '../contexts/ToastContext';
import BookForm from './BookForm';

vi.mock('../api/books', () => ({
  createBook: vi.fn(),
  getBook: vi.fn(),
  getCategories: vi.fn().mockResolvedValue([]),
  updateBook: vi.fn(),
}));

import { createBook, getBook } from '../api/books';

describe('BookForm (create)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('maps backend field errors onto the form', async () => {
    const user = userEvent.setup();
    (createBook as ReturnType<typeof vi.fn>).mockRejectedValue({
      name: 'ApiError', status: 400, code: 'VALIDATION_ERROR',
      message: 'Validation failed',
      fieldErrors: [{ field: 'title', message: 'must not be blank' }],
    });

    render(
      <ToastProvider>
        <MemoryRouter initialEntries={['/books/new']}>
          <Routes>
            <Route path="/books/new" element={<BookForm />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'Thêm sách' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('must not be blank'));
  });
});
```
Note: the submit button is `type="submit"` with label "Thêm sách"; clicking it submits even with empty fields (title/author validation comes from backend). If the button resolves as disabled due to saving state timing, the test still asserts the error appears.

- [ ] **Step 2: BookList fetch choice test**

`src/pages/BookList.test.tsx`:
```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../contexts/ToastContext';
import BookList from './BookList';

const mockList = vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 24, numberOfElements: 0, first: true, last: true, empty: true });
const mockSearch = vi.fn();
const mockCats = vi.fn().mockResolvedValue([]);
const mockYears = vi.fn().mockResolvedValue([]);

vi.mock('../api/books', () => ({
  listBooks: (...a: unknown[]) => mockList(...a),
  searchBooks: (...a: unknown[]) => mockSearch(...a),
  getCategories: (...a: unknown[]) => mockCats(...a),
  getYears: (...a: unknown[]) => mockYears(...a),
}));

describe('BookList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls listBooks when there is no query', async () => {
    render(
      <ToastProvider>
        <MemoryRouter initialEntries={['/']}>
          <BookList />
        </MemoryRouter>
      </ToastProvider>,
    );
    await waitFor(() => expect(mockList).toHaveBeenCalled());
    expect(mockSearch).not.toHaveBeenCalled();
  });
});
```
Add a second test where `initialEntries={['/?q=spring']}` asserts `mockSearch` is called and `mockList` is not.

- [ ] **Step 3: Pagination test**

`src/components/Pagination.test.tsx`:
```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Pagination from './Pagination';

describe('Pagination', () => {
  it('renders page buttons and reports the next page', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onChange} />);
    expect(screen.getByText('2')).toBeInTheDocument();
    await user.click(screen.getByText('2'));
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('hides when there is a single page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPageChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
```

- [ ] **Step 4: Run the full frontend suite**

Run: `npm test` → ALL PASS.
Run: `npm run build` → PASS (tsc type-checks everything).

- [ ] **Step 5: End-to-end smoke test**

1. Start backend: in `bookverse-api\` run `mvn spring-boot:run` (seeds 500 books).
2. Start frontend: in `bookverse-frontend\` run `npm run dev`.
3. In browser at `http://localhost:5173` walk the full flow:
   - List loads 24 books, pagination works, `[API]` logs visible in devtools console.
   - Filter by category and year **together** → correct subset (proves Task 1 fix).
   - Search text → results via `/search`.
   - Create a book with a cover → appears in list.
   - Open detail, switch thumb/medium/large covers.
   - Edit title + replace cover → saved.
   - Delete → confirm → removed.
   - Bulk import template with a duplicate ISBN → report shows the failed row.
4. If any flow errors, debug via the logged request line + the `ApiError` fields shown in the UI before fixing.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test(fe): unit tests for form errors, list fetch choice, pagination"
```

---

## Final Checklist

- [ ] Backend: `mvn test` green; new endpoints: `/api/books/categories`, `/api/books/years`, `PUT /api/books/{id}/cover`, multipart `PUT /api/books/{id}`.
- [ ] Backend: category + year compose in both list and search.
- [ ] Frontend: `npm run build` and `npm test` green.
- [ ] Frontend: all five screens (list, detail, create/edit, bulk import, not-found) work against the live backend.
- [ ] Design: tokens/palette/fonts match `tokens.css`; lamplight-glow hover on covers; reduced-motion respected.
- [ ] Debuggability: every request logged; typed `ApiError` surfaced; no swallowed errors.
