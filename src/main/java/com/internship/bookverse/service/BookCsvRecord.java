package com.internship.bookverse.service;

/**
 * A parsed row from the Book-Crossing CSV dataset.
 *
 * @param isbn       identity field (nullable when blank)
 * @param title      book title
 * @param author     book author
 * @param year       publication year
 * @param publisher  publisher — mapped to the book's category
 * @param coverUrl   remote cover image URL (Image-URL-M column, nullable when blank)
 */
public record BookCsvRecord(String isbn, String title, String author, Integer year, String publisher, String coverUrl) {
}
