package com.internship.bookverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.bookverse.dto.request.BookCreateRequest;
import com.internship.bookverse.dto.request.BookUpdateRequest;
import com.internship.bookverse.dto.response.BookResponse;
import com.internship.bookverse.exception.BookNotFoundException;
import com.internship.bookverse.exception.GlobalExceptionHandler;
import com.internship.bookverse.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
@Import(GlobalExceptionHandler.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    private BookResponse bookResponse;

    @BeforeEach
    void setUp() {
        bookResponse = BookResponse.builder()
                .id(1L)
                .title("Spring Boot in Action")
                .author("Craig Walls")
                .isbn("978-1617292545")
                .year(2016)
                .category("Technology")
                .rating(4.5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAll_shouldReturnPageOfBooks() throws Exception {
        when(bookService.getAll(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bookResponse)));

        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot in Action"))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getById_shouldReturnBook() throws Exception {
        when(bookService.getById(1L)).thenReturn(bookResponse);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"))
                .andExpect(jsonPath("$.author").value("Craig Walls"));
    }

    @Test
    void getById_shouldReturn404_whenBookNotFound() throws Exception {
        when(bookService.getById(99L)).thenThrow(new BookNotFoundException(99L));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Book not found with id: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/books/99"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        String bookJson = objectMapper.writeValueAsString(
                BookCreateRequest.builder()
                        .title("New Book")
                        .author("New Author")
                        .build());
        when(bookService.create(any())).thenReturn(bookResponse);

        MockMultipartFile bookPart = new MockMultipartFile(
                "book", "", "application/json", bookJson.getBytes());

        mockMvc.perform(multipart("/api/books")
                        .file(bookPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
    }

    @Test
    void create_shouldReturn400_whenValidationFails() throws Exception {
        String bookJson = objectMapper.writeValueAsString(
                BookCreateRequest.builder()
                        .title("")
                        .author("")
                        .build());

        MockMultipartFile bookPart = new MockMultipartFile(
                "book", "", "application/json", bookJson.getBytes());

        mockMvc.perform(multipart("/api/books")
                        .file(bookPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_shouldReturn200() throws Exception {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("Updated Title")
                .author("Updated Author")
                .build();
        when(bookService.update(eq(1L), any())).thenReturn(bookResponse);

        mockMvc.perform(put("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot in Action"));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());

        verify(bookService).delete(1L);
    }

    @Test
    void search_shouldReturnPage() throws Exception {
        when(bookService.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bookResponse)));

        mockMvc.perform(get("/api/books/search")
                        .param("q", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot in Action"));
    }

    @Test
    void getCover_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/books/1/cover"))
                .andExpect(status().isNotFound());
    }
}
