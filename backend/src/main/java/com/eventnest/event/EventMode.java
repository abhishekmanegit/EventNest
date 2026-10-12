package com.eventnest.event;

/**
 * Where an event happens. {@code HYBRID} is not "online but maybe in person" -
 * it means both a physical venue and a stream, and the detail page shows both.
 */
public enum EventMode {
    ONLINE,
    OFFLINE,
    HYBRID
}
