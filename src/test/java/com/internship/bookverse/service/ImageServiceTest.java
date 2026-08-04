package com.internship.bookverse.service;

import com.internship.bookverse.exception.InvalidImageFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageServiceTest {

    private ImageService imageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        imageService = new ImageService();
        ReflectionTestUtils.setField(imageService, "uploadDir", tempDir.toString());
    }

    @Test
    void upload_shouldReturnNull_whenFileIsNull() {
        String result = imageService.upload(null, 1L);
        assertThat(result).isNull();
    }

    @Test
    void upload_shouldThrow_whenInvalidFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "cover", "test.txt", "text/plain", "fake content".getBytes());

        assertThatThrownBy(() -> imageService.upload(file, 1L))
                .isInstanceOf(InvalidImageFormatException.class)
                .hasMessageContaining("Invalid image format");
    }

    @Test
    void upload_shouldSaveThreeFiles_whenValidImage() throws IOException {
        // Generate a valid 1x1 pixel PNG using Java ImageIO
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "cover", "test.png", "image/png", pngBytes);

        String coverPath = imageService.upload(file, 1L);

        assertThat(coverPath).isNotNull();
        assertThat(coverPath).contains("1");
        assertThat(Files.exists(Paths.get(coverPath + "-thumb.webp"))).isTrue();
        assertThat(Files.exists(Paths.get(coverPath + "-medium.webp"))).isTrue();
        assertThat(Files.exists(Paths.get(coverPath + "-large.webp"))).isTrue();
    }

    @Test
    void serve_shouldReturn404_whenCoverPathIsNull() {
        ResponseEntity<Resource> response = imageService.serve(null, "large");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void serve_shouldReturn404_whenFileNotFound() {
        ResponseEntity<Resource> response = imageService.serve("/nonexistent/path/1", "large");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
