package com.eventnest.report;

/** Mirrors {@code ck_reports_reason} in migration V8. */
public enum ReportReason {
    SPAM,
    FAKE_EVENT,
    SCAM,
    INAPPROPRIATE,
    DUPLICATE,
    OTHER
}
