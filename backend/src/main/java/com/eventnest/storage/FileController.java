package com.eventnest.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public file reads (posters and logos must render for logged-out visitors, so
 * this is on the anonymous side of the filter chain - see {@code SecurityConfig}
 * for the {@code /files/**} permit-all matcher).
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final LocalFileStorageService fileStorage;

    public FileController(LocalFileStorageService fileStorage) {
        this.fileStorage = fileStorage;
    }

    @GetMapping("/{subdir}/{year}/{month}/{name}")
    public ResponseEntity<Resource> serve(@PathVariable String subdir,
                                          @PathVariable String year,
                                          @PathVariable String month,
                                          @PathVariable String name) {
        Resource resource = fileStorage.load(subdir, year, month, name);
        String contentType = LocalFileStorageService.mediaTypeOf(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // Immutable-ish: the name embeds a UUID, so the browser may cache
                // hard. A replaced logo gets a brand-new URL, never a reused one.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(resource);
    }
}