package com.eventnest.bookmark;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    void deleteByUserIdAndEventId(Long userId, Long eventId);

    /**
     * Projects straight to event ids instead of returning {@link Bookmark} rows.
     * Two reasons: it is one narrow query rather than fetching entities we only
     * need an id from, and it never materialises a lazy {@code event} proxy - with
     * {@code open-in-view: false} the repository's transaction has already closed
     * by the time the caller walks that association.
     */
    @Query("SELECT b.event.id FROM Bookmark b WHERE b.user.id = :userId AND b.event.id IN :eventIds")
    List<Long> findBookmarkedEventIds(@Param("userId") Long userId,
                                      @Param("eventIds") Collection<Long> eventIds);

    @Query("SELECT b.event.id FROM Bookmark b WHERE b.user.id = :userId")
    List<Long> findAllBookmarkedEventIds(@Param("userId") Long userId);

    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
