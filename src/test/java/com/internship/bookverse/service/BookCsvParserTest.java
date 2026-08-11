package com.internship.bookverse.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookCsvParserTest {

    private final BookCsvParser parser = new BookCsvParser();

    @Test
    void parseLine_stripsQuotesAndParsesFields() {
        String line = "\"0195153448\";\"Classical Mythology\";\"Mark P. O. Morford\";\"2002\";\"Oxford University Press\"";

        BookCsvRecord rec = parser.parseLine(line);

        assertThat(rec).isNotNull();
        assertThat(rec.isbn()).isEqualTo("0195153448");
        assertThat(rec.title()).isEqualTo("Classical Mythology");
        assertThat(rec.author()).isEqualTo("Mark P. O. Morford");
        assertThat(rec.year()).isEqualTo(2002);
        assertThat(rec.publisher()).isEqualTo("Oxford University Press");
        assertThat(rec.coverUrl()).isNull();
    }

    @Test
    void parseLine_readsCoverUrlColumn() {
        String line = "\"0195153448\";\"Classical Mythology\";\"Mark P. O. Morford\";\"2002\";\"Oxford University Press\""
                + ";\"http://images.amazon.com/images/P/0195153448.01.MZZZZZZZ.jpg\""
                + ";\"http://images.amazon.com/images/P/0195153448.01.LZZZZZZZ.jpg\"";

        BookCsvRecord rec = parser.parseLine(line);

        assertThat(rec).isNotNull();
        assertThat(rec.coverUrl()).isEqualTo("http://images.amazon.com/images/P/0195153448.01.MZZZZZZZ.jpg");
    }

    @Test
    void parseLine_unescapesHtmlEntities() {
        String line = "\"0060973129\";\"Decision in Normandy\";\"Carlo D'Este\";\"1991\";\"W. W. Norton &amp; Company\"";

        BookCsvRecord rec = parser.parseLine(line);

        assertThat(rec.publisher()).isEqualTo("W. W. Norton & Company");
    }

    @Test
    void parseLine_supportsSemicolonInsideQuotedField() {
        String line = "\"1\";\"Title; With Semicolon\";\"Author\";\"2000\";\"Pub\"";

        BookCsvRecord rec = parser.parseLine(line);

        assertThat(rec.title()).isEqualTo("Title; With Semicolon");
    }

    @Test
    void parseLine_returnsNull_forTooFewFields() {
        assertThat(parser.parseLine("\"1\";\"Title\"")).isNull();
    }

    @Test
    void parseLine_returnsNull_whenTitleBlank() {
        assertThat(parser.parseLine("\"1\";\"\";\"Author\";\"2000\";\"Pub\"")).isNull();
    }

    @Test
    void parseLine_returnsNull_whenYearInvalid() {
        assertThat(parser.parseLine("\"1\";\"T\";\"A\";\"abcd\";\"P\"")).isNull();
        assertThat(parser.parseLine("\"1\";\"T\";\"A\";\"0\";\"P\"")).isNull();
        assertThat(parser.parseLine("\"1\";\"T\";\"A\";\"9999\";\"P\"")).isNull();
    }

    @Test
    void parse_skipsHeaderAndReturnsRecords_withCoverUrl() {
        String csv = "\"ISBN\";\"Book-Title\";\"Book-Author\";\"Year\";\"Publisher\";\"Image-URL-S\";\"Image-URL-M\";\"Image-URL-L\"\n"
                + "\"1\";\"Book One\";\"Author One\";\"2000\";\"Pub One\";\"http://x/s.jpg\";\"http://x/m.jpg\";\"http://x/l.jpg\"\n"
                + "\"2\";\"Book Two\";\"Author Two\";\"2001\";\"Pub Two\"";

        InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BookCsvRecord> records = parser.parse(in);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).coverUrl()).isEqualTo("http://x/s.jpg");
        assertThat(records.get(1).coverUrl()).isNull();
    }

    @Test
    void parse_skipsHeaderAndReturnsRecords() {
        String csv = "\"ISBN\";\"Book-Title\";\"Book-Author\";\"Year\";\"Publisher\"\n"
                + "\"1\";\"Book One\";\"Author One\";\"2000\";\"Pub One\"\n"
                + "\"2\";\"Book Two\";\"Author Two\";\"2001\";\"Pub Two\"";

        InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BookCsvRecord> records = parser.parse(in);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).title()).isEqualTo("Book One");
        assertThat(records.get(1).author()).isEqualTo("Author Two");
    }

    @Test
    void parse_skipsMalformedLines() {
        String csv = "\"ISBN\";\"Book-Title\";\"Book-Author\";\"Year\";\"Publisher\"\n"
                + "\"1\";\"Good\";\"Author\";\"2000\";\"Pub\"\n"
                + "malformed line\n"
                + "\"2\";\"\";\"Author\";\"2001\";\"Pub\"\n"
                + "\"3\";\"Another\";\"Author\";\"2002\";\"Pub\"";

        InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BookCsvRecord> records = parser.parse(in);

        assertThat(records).hasSize(2);
    }
}
