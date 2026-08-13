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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ImageService {

    private static final Set<String> ALLOWED_SIZES = Set.of("thumb", "medium", "large");
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int MEDIUM_WIDTH = 500;
    private static final int LARGE_WIDTH = 1200;

    @Value("${app.upload.dir:uploads/covers}")
    private String uploadDir;

    public String upload(MultipartFile file, Long bookId) {
        if (file == null || file.isEmpty()) {
            log.debug("upload: no file provided for book id={}", bookId);
            return null;
        }

        validateImageContent(file);
        log.info("upload: bookId={} contentType={} size={} bytes",
                bookId, file.getContentType(), file.getSize());

        try {
            String datePath = LocalDate.now().toString().replace("-", "/");
            Path baseDir = Paths.get(uploadDir, datePath);
            Files.createDirectories(baseDir);

            // Write all three sizes to temporary paths first (same dir, prefixed tmp_)
            String baseName = String.valueOf(bookId);
            Path thumbTemp = baseDir.resolve("tmp_" + baseName + "-thumb.webp");
            Path mediumTemp = baseDir.resolve("tmp_" + baseName + "-medium.webp");
            Path largeTemp = baseDir.resolve("tmp_" + baseName + "-large.webp");

            resizeAndSave(file, thumbTemp, THUMBNAIL_WIDTH);
            resizeAndSave(file, mediumTemp, MEDIUM_WIDTH);
            resizeAndSave(file, largeTemp, LARGE_WIDTH);

            // All three generated successfully — promote to final names
            Path thumbFinal = baseDir.resolve(baseName + "-thumb.webp");
            Path mediumFinal = baseDir.resolve(baseName + "-medium.webp");
            Path largeFinal = baseDir.resolve(baseName + "-large.webp");

            Files.move(thumbTemp, thumbFinal);
            Files.move(mediumTemp, mediumFinal);
            Files.move(largeTemp, largeFinal);

            String coverPath = baseDir.resolve(baseName).toString().replace("\\", "/");
            log.info("upload: saved 3 sizes to {}", coverPath);
            return coverPath;
        } catch (IOException e) {
            log.error("Failed to process image for book {}", bookId, e);
            throw new RuntimeException("Failed to process image", e);
        }
    }

    public ResponseEntity<Resource> serve(String coverPath, String size) {
        if (!ALLOWED_SIZES.contains(size)) {
            log.warn("serve: invalid size '{}', allowed={}", size, ALLOWED_SIZES);
            throw new InvalidImageFormatException(
                    "Invalid size: " + size + ". Allowed: thumb, medium, large");
        }

        if (coverPath == null) {
            log.debug("serve: no coverPath — returning 404");
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(coverPath + "-" + size + ".webp");
        if (!Files.exists(filePath)) {
            log.warn("serve: file not found {}", filePath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        log.debug("serve: {} {}", size, filePath);
        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }

    /**
     * Deletes all three cover image sizes for the given base cover path.
     * Called by controller/service when a book is deleted or its cover is replaced.
     * Silently ignores missing files — idempotent.
     */
    public void deleteCover(String coverPath) {
        if (coverPath == null || coverPath.isBlank()) {
            log.debug("deleteCover: no coverPath provided");
            return;
        }
        Path baseDir = Paths.get(coverPath).getParent();
        String baseName = Paths.get(coverPath).getFileName().toString();

        Path thumb = baseDir.resolve(baseName + "-thumb.webp");
        Path medium = baseDir.resolve(baseName + "-medium.webp");
        Path large = baseDir.resolve(baseName + "-large.webp");

        for (Path p : new Path[]{thumb, medium, large}) {
            try {
                if (Files.deleteIfExists(p)) {
                    log.debug("deleteCover: deleted {}", p);
                }
            } catch (IOException e) {
                log.warn("deleteCover: failed to delete {}", p, e);
            }
        }

        // Optionally remove the now-empty date directory
        try {
            if (Files.exists(baseDir) && Files.list(baseDir).findAny().isEmpty()) {
                Files.deleteIfExists(baseDir);
                Path yearDir = baseDir.getParent();
                if (yearDir != null && Files.exists(yearDir) && Files.list(yearDir).findAny().isEmpty()) {
                    Files.deleteIfExists(yearDir);
                }
            }
        } catch (IOException e) {
            log.debug("deleteCover: directory cleanup skipped: {}", e.getMessage());
        }
    }

    private void validateImageContent(MultipartFile file) {
        // First, content-type guard (cheap pre-check)
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            log.warn("validateImageContent: rejected type={}", contentType);
            throw new InvalidImageFormatException(
                    "Invalid image format: " + contentType + ". Allowed: JPG, PNG, WebP");
        }

        // Second, decode the actual image bytes — this validates the file is a real image
        // and not a malicious file masquerading with an image content-type.
        try (InputStream in = file.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                throw new InvalidImageFormatException("Empty image file");
            }
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                BufferedImage image = ImageIO.read(bis);
                if (image == null) {
                    log.warn("validateImageContent: ImageIO could not decode bytes");
                    throw new InvalidImageFormatException("Invalid image content: not a recognized image format");
                }
                // Basic sanity: ensure non-zero dimensions
                if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new InvalidImageFormatException("Invalid image dimensions");
                }
            }
        } catch (InvalidImageFormatException e) {
            throw e;
        } catch (IOException e) {
            // ImageIO throws IIOException for corrupt/truncated images — surface as invalid image
            log.warn("validateImageContent: I/O error reading image: {}", e.getMessage());
            throw new InvalidImageFormatException("Invalid image content: " + e.getMessage());
        }
    }

    private boolean isAllowedContentType(String contentType) {
        return contentType != null
                && (contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"));
    }

    private void resizeAndSave(MultipartFile file, Path outputPath, int width) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(outputPath.getParent());

        Thumbnails.of(file.getInputStream())
                .width(width)
                .outputFormat("webp")
                .toFile(outputPath.toFile());

        if (!Files.exists(outputPath)) {
            throw new IOException("Thumbnailator failed to create output file: " + outputPath);
        }
        log.debug("resizeAndSave: ({}px) -> {} ({} bytes)", width, outputPath, Files.size(outputPath));
    }
}