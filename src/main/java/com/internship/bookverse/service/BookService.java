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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public Page<BookResponse> getAll(Pageable pageable, String category, Integer year) {
        if (category != null) {
            return bookRepository.findByCategory(category, pageable)
                    .map(bookMapper::toResponse);
        }
        return bookRepository.findAll(pageable)
                .map(bookMapper::toResponse);
    }

    public BookResponse getById(Long id) {
        Book book = findBookOrThrow(id);
        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse create(BookCreateRequest request) {
        validateIsbnUniqueness(request.getIsbn());
        Book book = bookMapper.toEntity(request);
        Book saved = bookRepository.save(book);
        return bookMapper.toResponse(saved);
    }

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

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        bookRepository.deleteById(id);
    }

    public Page<BookResponse> search(String q, String category, Pageable pageable) {
        if (category != null) {
            return bookRepository
                    .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseAndCategory(q, q, category, pageable)
                    .map(bookMapper::toResponse);
        }
        return bookRepository
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(q, q, pageable)
                .map(bookMapper::toResponse);
    }

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
