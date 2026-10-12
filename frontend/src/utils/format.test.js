import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { toQuery, fmtDate, fmtDateTime, fmtTime, timeUntil, money, initials } from "./format";

describe("toQuery", () => {
  it("serialises provided params", () => {
    expect(toQuery({ page: 1, size: 12 })).toBe("?page=1&size=12");
  });
  it("skips undefined / null / empty / false values but keeps zero", () => {
    expect(toQuery({ q: "", city: undefined, verified: false, limit: 20 })).toBe("?limit=20");
  });
  it("serialises page zero", () => {
    expect(toQuery({ page: 0 })).toBe("?page=0");
  });
  it("keeps truthy numbers and zero", () => {
    expect(toQuery({ page: 0, limit: 20 })).toBe("?page=0&limit=20");
  });
  it("returns empty string for no params", () => {
    expect(toQuery({})).toBe("");
    expect(toQuery()).toBe("");
  });
});

describe("fmtDate / fmtTime", () => {
  it("returns dash for empty input", () => {
    expect(fmtDate(null)).toBe("—");
    expect(fmtDateTime(undefined)).toBe("—");
    expect(fmtTime("")).toBe("—");
  });
  it("formats a real instant", () => {
    const out = fmtDate("2026-10-06T08:00:00Z");
    expect(out).toMatch(/2026/);
    expect(out).toContain("Oct");
  });
});

describe("timeUntil", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());
  it("returns day label for >24h", () => {
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    expect(timeUntil("2026-01-03T00:00:00Z")).toBe("2d 0h left");
  });
  it("returns hour label for <24h", () => {
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    expect(timeUntil("2026-01-01T05:30:00Z")).toBe("5h 30m left");
  });
  it("returns minute label for <1h", () => {
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    expect(timeUntil("2026-01-01T00:10:00Z")).toBe("10m left");
  });
  it("returns null for past instants", () => {
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    expect(timeUntil("2025-12-31T00:00:00Z")).toBeNull();
  });
});

describe("money & initials", () => {
  it("returns null for no amount", () => {
    expect(money(null)).toBeNull();
    expect(money(0)).toBeNull();
  });
  it("formats rupee amounts, INR-aware", () => {
    expect(money(50000)).toBe("₹50,000");
  });
  it("builds two-letter initials", () => {
    expect(initials("rahul patil")).toBe("RP");
    expect(initials("Sita")).toBe("S");
    expect(initials("")).toBe("");
  });
});