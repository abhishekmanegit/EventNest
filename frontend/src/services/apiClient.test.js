import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  apiClient, ApiError, getAuth, isAuthed, setAuth, onAuthChange, rehydrateSession,
} from "./apiClient";

const json = (status, data, contentType = "application/json") => new Response(
  typeof data === "string" ? data : JSON.stringify(data),
  { status, headers: { "Content-Type": contentType } },
);

describe("auth helpers", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, {})));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    setAuth({});
  });

  it("setAuth persists to localStorage and notifies listeners", () => {
    const seen = [];
    const off = onAuthChange((a) => seen.push(a));
    setAuth({ accessToken: "at1", refreshToken: "rt1" });
    expect(getAuth()).toEqual({ accessToken: "at1", refreshToken: "rt1" });
    expect(isAuthed()).toBe(true);
    expect(localStorage.getItem("en_access")).toBe("at1");
    expect(seen.length).toBe(1);
    off();
  });

  it("setAuth({}) clears tokens and emits", () => {
    setAuth({ accessToken: "at", refreshToken: "rt" });
    setAuth({});
    expect(getAuth()).toEqual({ accessToken: null, refreshToken: null });
    expect(isAuthed()).toBe(false);
    expect(localStorage.getItem("en_access")).toBeNull();
    expect(localStorage.getItem("en_refresh")).toBeNull();
  });
});

describe("rehydrateSession", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    setAuth({});
  });

  it("returns false when no refresh token", async () => {
    expect(await rehydrateSession()).toBe(false);
  });

  it("swaps a stale access token for a new pair", async () => {
    setAuth({ accessToken: null, refreshToken: "rt" });
    const fetchMock = vi.fn().mockResolvedValue(json(200, { accessToken: "newAt", refreshToken: "newRt" }));
    vi.stubGlobal("fetch", fetchMock);
    expect(await rehydrateSession()).toBe(true);
    expect(getAuth()).toEqual({ accessToken: "newAt", refreshToken: "newRt" });
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body).toEqual({ refreshToken: "rt" });
  });

  it("is single-flight for concurrent callers", async () => {
    setAuth({ accessToken: null, refreshToken: "rt" });
    let calls = 0;
    const fetchMock = vi.fn().mockImplementation(async () => {
      calls += 1;
      await new Promise((r) => setTimeout(r, 20));
      return json(200, { accessToken: "n", refreshToken: "m" });
    });
    vi.stubGlobal("fetch", fetchMock);
    const [a, b] = await Promise.all([rehydrateSession(), rehydrateSession()]);
    expect(a).toBe(true);
    expect(b).toBe(true);
    expect(calls).toBe(1);
  });

  it("clears session and returns false when refresh fails", async () => {
    setAuth({ accessToken: null, refreshToken: "rt" });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(401, { code: "SESSION_EXPIRED" })));
    expect(await rehydrateSession()).toBe(false);
    expect(isAuthed()).toBe(false);
    expect(localStorage.getItem("en_refresh")).toBeNull();
  });
});

describe("apiClient request handling", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    setAuth({});
  });

  it("parses JSON bodies and attaches bearer token", async () => {
    setAuth({ accessToken: "tok", refreshToken: null });
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: 1 }));
    vi.stubGlobal("fetch", fetchMock);
    const out = await apiClient.get("/events");
    expect(out).toEqual({ ok: 1 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/events");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("returns null for 204 and empty bodies", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    expect(await apiClient.post("/events/1/registration")).toBeNull();
  });

  it("throws ApiError and retains non-JSON fallback message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 500 })));
    const err = await apiClient.get("/events").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
    expect(err.message).toContain("Request failed");
    expect(err.fieldErrors).toEqual([]);
  });

  it("maps backend field errors onto ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(400, {
      code: "VALIDATION_FAILED", message: "Fix the highlighted fields",
      fieldErrors: [{ field: "email", message: "is required" }],
    })));
    const err = await apiClient.post("/auth/register/student", {}).catch((e) => e);
    expect(err.code).toBe("VALIDATION_FAILED");
    expect(err.fieldErrors).toEqual([{ field: "email", message: "is required" }]);
  });

  it("refreshes once on 401 and retries the original call", async () => {
    setAuth({ accessToken: "expired", refreshToken: "rt" });
    let i = 0;
    const fetchMock = vi.fn().mockImplementation(async (url, init) => {
      i += 1;
      if (init?.method === "POST" && String(url).includes("/auth/refresh")) {
        return json(200, { accessToken: "fresh", refreshToken: "rt2" });
      }
      if (i === 1) return json(401, { code: "EXPIRED" });
      return json(200, { me: true });
    });
    vi.stubGlobal("fetch", fetchMock);
    const out = await apiClient.get("/me/profile");
    expect(out).toEqual({ me: true });
    expect(getAuth().accessToken).toBe("fresh");
    expect(fetchMock.mock.calls.length).toBe(3); // original, refresh, retry
  });

  it("does not refresh for auth endpoints (guard against loops)", async () => {
    setAuth({ accessToken: "expired", refreshToken: "rt" });
    const fetchMock = vi.fn().mockResolvedValue(json(401, { code: "BAD_CREDENTIALS" }));
    vi.stubGlobal("fetch", fetchMock);
    const err = await apiClient.post("/auth/login", {}).catch((e) => e);
    expect(err.code).toBe("BAD_CREDENTIALS");
    expect(fetchMock.mock.calls.length).toBe(1);
  });
});