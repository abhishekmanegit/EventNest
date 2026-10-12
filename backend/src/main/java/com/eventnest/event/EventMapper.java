package com.eventnest.event;

import com.eventnest.event.dto.EventDtos.EventDetail;
import com.eventnest.event.dto.EventDtos.EventSummary;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity to DTO for events.
 *
 * <p>MapStruct generates the mechanical copying (decision D2), and this class is
 * where the three fields that are <em>not</em> a straight copy are declared:
 *
 * <ol>
 *   <li>{@code organizerName} - there is no {@code organizerName} property on
 *       {@link Event}; the name lives one hop away on the organizer's {@code User}.
 *       Writing the traversal here means the N+1 that would result from doing it
 *       in a loop is visible in one place, next to the mapping that causes it.</li>
 *   <li>{@code registrationOpen} - derived from several fields plus "now", so it
 *       is a computed value, not a mapped one.</li>
 *   <li>{@code bookmarked} / {@code registered} / {@code myRegistration} - they
 *       depend on <b>who is asking</b>, not on the event. They are ignored here
 *       and applied afterwards by {@link EventSummary#withViewerFlags}, because a
 *       mapper that silently returns {@code false} for "have I registered?" when
 *       no viewer was supplied is exactly the kind of default that turns into a
 *       production bug.</li>
 * </ol>
 *
 * <p>{@code unmappedTargetPolicy = IGNORE} overrides the project-wide
 * {@code ERROR} default (set via {@code -Amapstruct.unmappedTargetPolicy} in the
 * POM) for this mapper only, because {@link EventDetail} deliberately leaves
 * {@code myRegistration} null until the registrations module lands.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    @Mapping(target = "organizerName",
            expression = "java(event.getOrganizer().getUser().getFullName())")
    @Mapping(target = "registrationOpen",
            expression = "java(event.isRegistrationOpen(Instant.now()))")
    @Mapping(target = "bookmarked", ignore = true)
    @Mapping(target = "registered", ignore = true)
    EventSummary toSummary(Event event);

    List<EventSummary> toSummaryList(List<Event> events);

    @Mapping(target = "summary", expression = "java(toSummary(event))")
    @Mapping(target = "myRegistration", ignore = true)
    EventDetail toDetail(Event event);

    /** Null-safe variant: a detail call with no viewer must not NPE. */
    default EventDetail toDetailOrNull(Event event) {
        return event == null ? null : toDetail(event);
    }
}