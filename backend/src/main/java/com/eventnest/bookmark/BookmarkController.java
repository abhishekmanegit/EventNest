package com.eventnest.bookmark;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Save/unsave an event (ARCHITECTURE.md 7.3).
 *
 * <p>Both verbs are idempotent and both say so through the status code:
 * {@code 201 Created} the first time, {@code 200 OK} if it was already saved,
 * {@code 204} on removal whether or not anything was removed. A bookmark button
 * that reports an error when tapped twice is a button users learn to distrust.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/bookmark")
@PreAuthorize("hasRole('STUDENT')")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping
    public ResponseEntity<Void> save(@PathVariable Long eventId) {
        boolean created = bookmarkService.save(eventId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable Long eventId) {
        bookmarkService.remove(eventId);
        return ResponseEntity.noContent().build();
    }
}
