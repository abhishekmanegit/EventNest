package com.eventnest.storage;

import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.config.AppProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-filesystem storage for uploads (posters, college logos - see
 * {@code ARCHITECTURE.md §7.6}).
 *
 * <p>Files land under {@code app.storage.local-path}/{subdir}/{yyyy}/{MM}/{uuid}.ext}
 * and are served back through {@code GET /files/{yyyy}/{MM}/{name}} after a path
 * traversal guard: every segment is compared against the resolved root, so
 * {@code ../} in a URL is a 404, not an escape.
 *
 * <p>Deliberately filesystem, not S3: an MVP on one VM does not need a bucket,
 * and swapping to S3 later means re-implementing just {@link #store} and
 * {@link #load} behind this interface's shape - every caller here already deals
 * in URLs, never in paths.
 */
@Service
public class LocalFileStorageService {

    private final java.nio.file.Path root;
    private final String publicBaseUrl;
    private final long maxFileSizeBytes;
    private final Clock clock;

    public LocalFileStorageService(AppProperties properties, Clock clock) {
        this.root = java.nio.file.Path.of(properties.storage().localPath())
                .toAbsolutePath().normalize();
        this.publicBaseUrl = stripTrailingSlash(properties.storage().publicBaseUrl());
        this.maxFileSizeBytes = properties.storage().maxFileSizeBytes();
        this.clock = clock;
    }

    /**
     * Persists an uploaded image and returns the public URL to it. Only a URL
     * crosses the byte boundary upward: no caller should ever learn where on
     * disk anything lives.
     */
    public String store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("File is too large. Maximum is "
                    + maxFileSizeBytes / (1024 * 1024) + " MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BadRequestException("Only image uploads are allowed.");
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Month zero-padding keeps the URL lexicographically sane in a file dump.
        String relative = String.format(Locale.ROOT, "%s/%d/%02d/%s%s",
                sanitize(subdir, true),
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID().toString().replace("-", ""),
                sanitizedExtension(file.getOriginalFilename()));

        java.nio.file.Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("Invalid upload location.");
        }
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not store the uploaded file.", e);
        }
        return publicBaseUrl + "/" + relative;
    }

    /**
     * Serves a stored file with a content type guessed from its extension - the
     * browser needs one to render an {@code <img>}, and guessing can be
     * overridden by the frontend when a signature differs.
     */
    public Resource load(String subdir, String year, String month, String name) {
        String relative = String.format(Locale.ROOT, "%s/%s/%s/%s",
                sanitize(subdir, false),
                sanitize(year, false),
                sanitize(month, false),
                sanitize(name, false));
        java.nio.file.Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !java.nio.file.Files.isRegularFile(file)) {
            throw new NotFoundException("File not found.");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (java.io.IOException e) {
            throw new NotFoundException("File could not be read.");
        }
    }

    /** Media type for a served file, derived from the stored name. */
    public static String mediaTypeOf(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF_VALUE;
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * {@code ../} cannot be smuggled through any one segment (a segment is a
     * file name, so both dots are rejected outright), and the resolved-path
     * check above would catch any residual escape. Rejecting dots entirely is
     * the boring, defensible choice over a clever "allow one-dot-but-not-two"
     * rule.
     */
    private static String sanitize(String value, boolean allowSlash) {
        if (value == null || value.isBlank()) {
            throw new NotFoundException("File not found.");
        }
        String clean = value.trim().replace("\\", "/");
        if (clean.contains("..")) {
            throw new BadRequestException("Invalid file reference.");
        }
        return allowSlash ? clean : clean.split("/")[0];
    }

    private static String sanitizedExtension(String original) {
        if (original == null) {
            return "";
        }
        int dot = original.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,8}") ? "." + ext : "";
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "/api/v1/files" : value.replaceAll("/+$", "");
    }
}