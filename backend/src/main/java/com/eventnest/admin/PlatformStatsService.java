package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.DailyCount;
import com.eventnest.admin.AdminDtos.PlatformStats;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin dashboard numbers (ARCHITECTURE.md §7.5).
 *
 * <p><b>Entirely SQL, with no repository calls.</b> Every number on this page is
 * a pure aggregate - a COUNT over a table or a GROUP BY over thirty days - and
 * none of them has an entity worth hydrating. Going through the JPA object graph
 * to count rows means paying for object construction and field mapping just to
 * throw the objects away. {@link JdbcTemplate} reads the aggregate directly and
 * returns tuned boxes. This is the named seam: <b>OLAP-shaped reads do not need
 * the persistence layer</b>; repositories are for transactional logic.
 *
 * <p>The whole dashboard is also one {@code queryForMap} round trip plus one
 * trend query, because a stats page that fires eleven queries to render eleven
 * numbers is a stats page that is slow for a reason nobody can find later.
 */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    /** 31 buckets: the 30 previous days plus today, so the chart's last point is now. */
    private static final int TREND_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public PlatformStats stats() {
        Map<String, Object> counts = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT count(*) FROM users)                                      AS users,
                    (SELECT count(*) FROM users WHERE role = 'STUDENT')               AS students,
                    (SELECT count(*) FROM users WHERE role = 'ORGANIZER')             AS organizers,
                    (SELECT count(*) FROM users WHERE role = 'ADMIN')                 AS admins,
                    (SELECT count(*) FROM colleges)                                   AS colleges,
                    (SELECT count(*) FROM colleges WHERE verification_status = 'VERIFIED')
                                                                                      AS verified_colleges,
                    (SELECT count(*) FROM events)                                     AS events,
                    (SELECT count(*) FROM events WHERE status = 'PUBLISHED')          AS published_events,
                    (SELECT count(*) FROM registrations)                              AS registrations,
                    (SELECT count(*) FROM registrations
                       WHERE status IN ('CONFIRMED','ATTENDED'))                      AS confirmed_registrations,
                    (SELECT count(*) FROM organizers
                       WHERE verification_status = 'PENDING')                         AS pending_organizers,
                    (SELECT count(*) FROM colleges
                       WHERE verification_status = 'PENDING')                         AS pending_colleges,
                    (SELECT count(*) FROM events WHERE status = 'PENDING_REVIEW')     AS pending_review_events,
                    (SELECT count(*) FROM reports WHERE status = 'OPEN')              AS open_reports
                """);

        return new PlatformStats(
                longOf(counts.get("users")),
                longOf(counts.get("students")),
                longOf(counts.get("organizers")),
                longOf(counts.get("admins")),
                longOf(counts.get("colleges")),
                longOf(counts.get("verified_colleges")),
                longOf(counts.get("events")),
                longOf(counts.get("published_events")),
                longOf(counts.get("registrations")),
                longOf(counts.get("confirmed_registrations")),
                longOf(counts.get("pending_organizers")),
                longOf(counts.get("pending_colleges")),
                longOf(counts.get("pending_review_events")),
                longOf(counts.get("open_reports")),
                trend());
    }

    /**
     * A zero-filled 31-day series. The zero-filling happens in Java rather than
     * with a {@code generate_series} CTE: an empty day is a fact about the
     * platform, not about PostgreSQL, and a chart that skips days silently
     * misrepresents "no activity" as "no data".
     *
     * <p>The three counts arrive as one UNION ALL rather than three queries so
     * the window is consistent - three separate round trips could straddle
     * midnight and disagree about which day a row belongs to.
     */
    private static long longOf(Object value) {
        return ((Number) value).longValue();
    }

    private List<DailyCount> trend() {
        String sql = """
                SELECT d::date AS day, kind, cnt FROM (
                    SELECT date_trunc('day', created_at)::date AS d, 'EVENTS' AS kind,
                           count(*) AS cnt
                      FROM events
                     WHERE created_at >= now() - interval '31 days'
                     GROUP BY 1
                    UNION ALL
                    SELECT date_trunc('day', created_at)::date, 'REGISTRATIONS', count(*)
                      FROM registrations
                     WHERE created_at >= now() - interval '31 days'
                     GROUP BY 1
                    UNION ALL
                    SELECT date_trunc('day', created_at)::date, 'USERS', count(*)
                      FROM users
                     WHERE created_at >= now() - interval '31 days'
                     GROUP BY 1
                ) t
                ORDER BY day
                """;

        Map<LocalDate, long[]> byDay = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS; i >= 0; i--) {
            byDay.put(today.minusDays(i), new long[3]);
        }

        jdbcTemplate.query(sql, rs -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            String kind = rs.getString("kind");
            long count = rs.getLong("cnt");
            long[] slot = byDay.get(day);
            if (slot == null) {
                return;
            }
            switch (kind) {
                case "EVENTS" -> slot[0] = count;
                case "REGISTRATIONS" -> slot[1] = count;
                default -> slot[2] = count;
            }
        });

        List<DailyCount> trend = new ArrayList<>(byDay.size());
        byDay.keySet().stream().sorted().forEach(day -> {
            long[] counts = byDay.get(day);
            trend.add(new DailyCount(day, counts[0], counts[1], counts[2]));
        });
        return trend;
    }
}