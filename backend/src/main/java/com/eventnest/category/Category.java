package com.eventnest.category;

import com.eventnest.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The event taxonomy - database-driven (decision D16), so admins can add a
 * category from the admin UI without a code deploy. Discovery rails, filter chips
 * and {@code GET /api/v1/categories} all read this one table.
 *
 * <p>{@code icon} stores a component NAME ("Code2"), never markup. The frontend
 * maps the name to an icon component. If the database could hold markup we would
 * be storing untrusted HTML that the UI has to sanitise on every render.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 90)
    private String slug;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon = "Sparkles";

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#4338CA";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /**
     * Denormalised count of published events, refreshed by
     * {@code refresh_category_event_counts()}. Counting per category on every home
     * page load is 15 extra COUNT queries; this is one.
     */
    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;
}
