package com.internship.bookverse.config;

import com.internship.bookverse.entity.Book;
import com.internship.bookverse.repository.BookRepository;
import com.internship.bookverse.service.ImageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BookSeederTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ImageService imageService(Path tempDir) {
        ImageService imageService = new ImageService();
        ReflectionTestUtils.setField(imageService, "uploadDir", tempDir.toString());
        return imageService;
    }

    private BookSeeder seeder(Path tempDir) {
        return new BookSeeder(mock(BookRepository.class), null, imageService(tempDir));
    }

    @Test
    void downloadCover_followsRedirects(@TempDir Path tempDir) throws IOException {
        byte[] png = pngBytes();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/final.jpg");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final.jpg", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });

        Book book = Book.builder().id(1L).title("Redirected Book").build();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect";

        Object ok = ReflectionTestUtils.invokeMethod(seeder(tempDir), "downloadCover", book, url);

        assertThat(ok).isEqualTo(true);
        assertThat(book.getCoverPath()).isNotNull();
        assertThat(Paths.get(book.getCoverPath() + "-thumb.webp")).exists();
    }

    @Test
    void downloadCover_downloadsUrl_andSetsCoverPath(@TempDir Path tempDir) throws IOException {
        byte[] png = pngBytes();
        server.createContext("/cover.jpg", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });

        Book book = Book.builder().id(1L).title("Test Book").build();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover.jpg";

        Object ok = ReflectionTestUtils.invokeMethod(seeder(tempDir), "downloadCover", book, url);

        assertThat(ok).isEqualTo(true);
        assertThat(book.getCoverPath()).isNotNull();
        assertThat(Paths.get(book.getCoverPath() + "-thumb.webp")).exists();
        assertThat(Paths.get(book.getCoverPath() + "-medium.webp")).exists();
        assertThat(Paths.get(book.getCoverPath() + "-large.webp")).exists();
    }

    @Test
    void downloadCover_toleratesHttpError(@TempDir Path tempDir) {
        server.createContext("/missing.jpg", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        Book book = Book.builder().id(1L).title("Test Book").build();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/missing.jpg";

        Object ok = ReflectionTestUtils.invokeMethod(seeder(tempDir), "downloadCover", book, url);

        assertThat(ok).isEqualTo(false);
        assertThat(book.getCoverPath()).isNull();
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}