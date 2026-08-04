package com.internship.bookverse.service;

import com.internship.bookverse.exception.InvalidImageFormatException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ImageService {

    private static final Set<String> ALLOWED_FORMATS = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_SIZES = Set.of("thumb", "medium", "large");
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int MEDIUM_WIDTH = 500;
    private static final int LARGE_WIDTH = 1200;

    @Value("${app.upload.dir:uploads/covers}")
    private String uploadDir;

    public String upload(MultipartFile file, Long bookId) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateFormat(file);

        try {
            String datePath = LocalDate.now().toString().replace("-", "/");
            Path baseDir = Paths.get(uploadDir, datePath);
            Files.createDirectories(baseDir);

            // Resize and convert to WebP
            resizeAndSave(file, baseDir, bookId, THUMBNAIL_WIDTH, "thumb");
            resizeAndSave(file, baseDir, bookId, MEDIUM_WIDTH, "medium");
            resizeAndSave(file, baseDir, bookId, LARGE_WIDTH, "large");

            return baseDir.resolve(String.valueOf(bookId)).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("Failed to process image for book {}", bookId, e);
            throw new RuntimeException("Failed to process image", e);
        }
    }

    public ResponseEntity<Resource> serve(String coverPath, String size) {
        if (!ALLOWED_SIZES.contains(size)) {
            throw new InvalidImageFormatException(
                    "Invalid size: " + size + ". Allowed: thumb, medium, large");
        }

        if (coverPath == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(coverPath + "-" + size + ".webp");
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .body(resource);
    }

    private void validateFormat(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FORMATS.contains(contentType)) {
            throw new InvalidImageFormatException(
                    "Invalid image format: " + contentType + ". Allowed: JPG, PNG, WebP");
        }
    }

    private void resizeAndSave(MultipartFile file, Path baseDir, Long bookId, int width, String sizeLabel)
            throws IOException {
        String filename = bookId + "-" + sizeLabel + ".webp";
        Path outputPath = baseDir.resolve(filename);

        Thumbnails.of(file.getInputStream())
                .width(width)
                .outputFormat("webp")
                .toFile(outputPath.toFile());

        log.debug("Saved cover image: {}", outputPath);
    }
}
