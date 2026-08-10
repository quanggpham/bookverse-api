package com.internship.bookverse.service;

import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.dto.response.CategoryCount;
import com.internship.bookverse.dto.response.YearCount;
import com.internship.bookverse.entity.Book;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.exception.IsbnAlreadyExistsException;
import com.internship.bookverse.mapper.BookMapperImpl;
import com.internship.bookverse.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    private BookService bookService;

    private Book book;

    @BeforeEach
    void setUp() {
        BookMapperImpl mapper = new BookMapperImpl();
        bookService = new BookService(bookRepository, mapper);

        book = Book.builder()
                .id(1L)
                .title("Spring Boot in Action")
                .author("Craig Walls")
                .isbn("978-1617292545")
                .year(2016)
                .category("Technology")
                .rating(4.5)
                .description("A comprehensive guide to Spring Boot")
                .build();
    }

    @Test
    void getById_shouldReturnBookResponse_whenBookExists() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookResponse result = bookService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Spring Boot in Action");
        assertThat(result.getAuthor()).isEqualTo("Craig Walls");
    }

    @Test
    void getById_shouldThrowBookNotFoundException_whenBookDoesNotExist() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getById(99L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAll_shouldReturnPageOfBookResponses() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.findByFilters(null, null, PageRequest.of(0, 10))).thenReturn(page);

        Page<BookResponse> result = bookService.getAll(PageRequest.of(0, 10), null, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Spring Boot in Action");
    }

    @Test
    void getAll_shouldFilterByCategory_whenCategoryProvided() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.findByFilters("Technology", null, PageRequest.of(0, 10))).thenReturn(page);

        Page<BookResponse> result = bookService.getAll(PageRequest.of(0, 10), "Technology", null);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void create_shouldReturnBookResponse_whenRequestValid() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("New Book")
                .author("New Author")
                .isbn("978-1234567890")
                .build();

        when(bookRepository.existsByIsbn("978-1234567890")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookResponse result = bookService.create(request);

        assertThat(result.getTitle()).isEqualTo("New Book");
        assertThat(result.getAuthor()).isEqualTo("New Author");
    }

    @Test
    void create_shouldThrowIsbnAlreadyExistsException_whenIsbnDuplicate() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("New Book")
                .author("New Author")
                .isbn("978-1234567890")
                .build();

        when(bookRepository.existsByIsbn("978-1234567890")).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(request))
                .isInstanceOf(IsbnAlreadyExistsException.class)
                .hasMessageContaining("978-1234567890");
    }

    @Test
    void create_shouldNotThrow_whenIsbnIsNull() {
        BookCreateRequest request = BookCreateRequest.builder()
                .title("Book Without ISBN")
                .author("Author")
                .build();

        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookResponse result = bookService.create(request);

        assertThat(result.getIsbn()).isNull();
    }

    @Test
    void update_shouldReturnUpdatedBookResponse_whenBookExists() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .isbn("978-1617292545") // same ISBN as existing
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.update(1L, request);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getAuthor()).isEqualTo("Updated Author");
    }

    @Test
    void update_shouldThrow_whenNewIsbnAlreadyExistsOnAnotherBook() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .isbn("978-NEW-ISBN")
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbn("978-NEW-ISBN")).thenReturn(true);

        assertThatThrownBy(() -> bookService.update(1L, request))
                .isInstanceOf(IsbnAlreadyExistsException.class);
    }

    @Test
    void updateCoverPath_shouldSetCoverPath_whenBookExists() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.updateCoverPath(1L, "/covers/test.webp");

        assertThat(result.getCoverPath()).isEqualTo("/covers/test.webp");
    }

    @Test
    void updateCoverPath_shouldThrowBookNotFoundException_whenBookDoesNotExist() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.updateCoverPath(99L, "/covers/test.webp"))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void delete_shouldDelete_whenBookExists() {
        when(bookRepository.existsById(1L)).thenReturn(true);

        bookService.delete(1L);

        verify(bookRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowBookNotFoundException_whenBookDoesNotExist() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> bookService.delete(99L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void search_shouldReturnMatchingBooks() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.searchBooks(any(), any(), any(), any(), any())).thenReturn(page);

        Page<BookResponse> result = bookService.search("Spring", null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Spring Boot in Action");
    }

    @Test
    void search_shouldFilterByCategory_whenCategoryProvided() {
        Page<Book> page = new PageImpl<>(List.of(book));
        when(bookRepository.searchBooks(any(), any(), any(), any(), any())).thenReturn(page);

        Page<BookResponse> result = bookService.search("Spring", "Technology", null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

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
}
