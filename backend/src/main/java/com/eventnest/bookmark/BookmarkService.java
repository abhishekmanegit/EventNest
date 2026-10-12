package com.eventnest.bookmark;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.event.Event;
import com.eventnest.event.EventQueryService;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.security.SecurityUtils;
import com.eventnest.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Save this for later" (ARCHITECTURE.md 7.3).
 *
 * <p><b>Save is idempotent by design.</b> Double-tapping a bookmark button is
 * not a user mistake worth an error message, so a duplicate POST returns
 * {@code 200} rather than {@code 409} - unlike registration, where a duplicate
 * means the user has misunderstood their own state and deserves to be told. The
 * distinction is about what the response is for: one confirms a toggle, the
 * other confirms a commitment.
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final EventRepository eventRepository;
    private final EventQueryService eventQueryService;
    private final UserRepository userRepository;

    /**
     * @return {@code true} if this request created the bookmark, {@code false} if
     *         it was already saved - which the controller turns into 201 vs 200.
     */
    @Transactional
    public boolean save(Long eventId) {
        Long userId = SecurityUtils.currentUserId();
        Event event = publishedEvent(eventId);

        // Pre-check, purely to answer 200 vs 201 cleanly. UNIQUE (user_id, event_id)
        // is what actually prevents the duplicate: if two requests race past this
        // line the constraint fires, the transaction rolls back, and the client
        // sees 409 DUPLICATE_RESOURCE. Reaching that needs a concurrent double-tap
        // of one save button, which is harmless either way.
        if (bookmarkRepository.existsByUserIdAndEventId(userId, eventId)) {
            return false;
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId)));
        bookmark.setEvent(event);
        bookmarkRepository.save(bookmark);
        return true;
    }

    @Transactional
    public void remove(Long eventId) {
        Long userId = SecurityUtils.currentUserId();
        publishedEvent(eventId);
        // Deleting a row that is not there is a no-op, not an error: the desired
        // end state is "not saved" and that is already true. 404 for "already
        // unsaved" would make an idempotent UI action non-idempotent.
        bookmarkRepository.deleteByUserIdAndEventId(userId, eventId);
    }

    /**
     * Saved events are <b>not</b> filtered by status. A saved event that later got
     * cancelled still appears, with its {@code CANCELLED} status - hiding it would
     * leave the student wondering whether they misremembered saving it. The status
     * field on the summary is what lets the UI badge it.
     */
    @Transactional(readOnly = true)
    public PageResponse<EventSummary> mine(int page, int size) {
        Long userId = SecurityUtils.currentUserId();
        Page<Bookmark> rows = bookmarkRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<Event> events = rows.getContent().stream().map(Bookmark::getEvent).toList();
        return PageResponse.of(rows, eventQueryService.summarize(events, userId));
    }

    /**
     * Saving a draft would let a student bookmark an event no one has announced
     * yet - reachable only by guessing an id, which is exactly the case
     * {@code 404} is for (see the same reasoning on event detail: a 403 would
     * confirm the id exists).
     */
    private Event publishedEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }
}
