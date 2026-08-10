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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    @Cacheable(value = "books", key = "{#pageable.pageNumber, #pageable.pageSize, #pageable.sort, #category, #year}")
    public Page<BookResponse> getAll(Pageable pageable, String category, Integer year) {
        log.debug("getAll: page={} size={} category={} year={}",
                pageable.getPageNumber(), pageable.getPageSize(), category, year);

        Page<Book> page;
        if (category != null) {
            page = bookRepository.findByCategory(category, pageable);
        } else if (year != null) {
            page = bookRepository.findByYear(year, pageable);
        } else {
            page = bookRepository.findAll(pageable);
        }

        log.debug("getAll returned {} results (total={})",
                page.getContent().size(), page.getTotalElements());
        return page.map(bookMapper::toResponse);
    }

    @Cacheable(value = "bookById", key = "#id")
    public BookResponse getById(Long id) {
        log.debug("getById: id={}", id);
        Book book = findBookOrThrow(id);
        log.debug("getById: found '{}' by {}", book.getTitle(), book.getAuthor());
        return bookMapper.toResponse(book);
    }

    @CacheEvict(value = {"books", "bookSearch"}, allEntries = true)
    @Transactional
    public BookResponse create(BookCreateRequest request) {
        log.info("create: title='{}' author='{}' isbn={}", request.getTitle(), request.getAuthor(), request.getIsbn());
        validateIsbnUniqueness(request.getIsbn());
        Book book = bookMapper.toEntity(request);
        Book saved = bookRepository.save(book);
        log.info("create: saved with id={}", saved.getId());
        return bookMapper.toResponse(saved);
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public BookResponse update(Long id, BookUpdateRequest request) {
        log.info("update: id={} title='{}' author='{}'", id, request.getTitle(), request.getAuthor());
        Book book = findBookOrThrow(id);
        String newIsbn = request.getIsbn();
        if (newIsbn != null && !newIsbn.equals(book.getIsbn())) {
            log.debug("update: isbn changed from {} to {}", book.getIsbn(), newIsbn);
            validateIsbnUniqueness(newIsbn);
        }
        bookMapper.updateEntity(request, book);
        Book saved = bookRepository.save(book);
        log.debug("update: saved id={}", saved.getId());
        return bookMapper.toResponse(saved);
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        log.info("delete: id={}", id);
        if (!bookRepository.existsById(id)) {
            log.warn("delete: book not found id={}", id);
            throw new BookNotFoundException(id);
        }
        bookRepository.deleteById(id);
        log.info("delete: soft-deleted id={}", id);
    }

    @Cacheable(value = "bookSearch", key = "{#q, #category, #pageable.pageNumber, #pageable.pageSize, #pageable.sort}")
    public Page<BookResponse> search(String q, String category, Pageable pageable) {
        log.debug("search: q='{}' category={} page={}", q, category, pageable.getPageNumber());
        Page<BookResponse> result = bookRepository.searchBooks(q, q, category, pageable)
                .map(bookMapper::toResponse);
        log.debug("search returned {} hits", result.getTotalElements());
        return result;
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public BookResponse updateCoverPath(Long id, String coverPath) {
        log.info("updateCoverPath: id={} path={}", id, coverPath);
        Book book = findBookOrThrow(id);
        book.setCoverPath(coverPath);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    private Book findBookOrThrow(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("findBookOrThrow: not found id={}", id);
                    return new BookNotFoundException(id);
                });
        return book;
    }

    private void validateIsbnUniqueness(String isbn) {
        if (isbn != null && !isbn.isBlank() && bookRepository.existsByIsbn(isbn)) {
            log.warn("validateIsbnUniqueness: duplicate isbn={}", isbn);
            throw new IsbnAlreadyExistsException(isbn);
        }
    }
}
