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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    @Cacheable(value = "books", key = "{#pageable.pageNumber, #pageable.pageSize, #pageable.sort, #category, #year}")
    public Page<BookResponse> getAll(Pageable pageable, String category, Integer year) {
        if (category != null) {
            return bookRepository.findByCategory(category, pageable)
                    .map(bookMapper::toResponse);
        }
        if (year != null) {
            return bookRepository.findByYear(year, pageable)
                    .map(bookMapper::toResponse);
        }
        return bookRepository.findAll(pageable)
                .map(bookMapper::toResponse);
    }

    @Cacheable(value = "bookById", key = "#id")
    public BookResponse getById(Long id) {
        Book book = findBookOrThrow(id);
        return bookMapper.toResponse(book);
    }

    @CacheEvict(value = {"books", "bookSearch"}, allEntries = true)
    @Transactional
    public BookResponse create(BookCreateRequest request) {
        validateIsbnUniqueness(request.getIsbn());
        Book book = bookMapper.toEntity(request);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public BookResponse update(Long id, BookUpdateRequest request) {
        Book book = findBookOrThrow(id);
        String newIsbn = request.getIsbn();
        if (newIsbn != null && !newIsbn.equals(book.getIsbn())) {
            validateIsbnUniqueness(newIsbn);
        }
        bookMapper.updateEntity(request, book);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        bookRepository.deleteById(id);
    }

    @Cacheable(value = "bookSearch", key = "{#q, #category, #pageable.pageNumber, #pageable.pageSize, #pageable.sort}")
    public Page<BookResponse> search(String q, String category, Pageable pageable) {
        return bookRepository.searchBooks(q, q, category, pageable)
                .map(bookMapper::toResponse);
    }

    @CacheEvict(value = {"books", "bookSearch", "bookById"}, allEntries = true)
    @Transactional
    public BookResponse updateCoverPath(Long id, String coverPath) {
        Book book = findBookOrThrow(id);
        book.setCoverPath(coverPath);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

    private Book findBookOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    private void validateIsbnUniqueness(String isbn) {
        if (isbn != null && !isbn.isBlank() && bookRepository.existsByIsbn(isbn)) {
            throw new IsbnAlreadyExistsException(isbn);
        }
    }
}
