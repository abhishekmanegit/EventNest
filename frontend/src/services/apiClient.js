/**
 * The one place that touches the wire (docs/ARCHITECTURE §6.5).
 *
 * - Base URL from VITE_API_URL (defaults to /api/v1, relative → same-origin
 *   through the Vite dev proxy, no CORS).
 * - Attaches the bearer token, normalises every failure into `ApiError`.
 * - 401 single-flight refresh: one in-flight refresh request; concurrent 401s
 *   queue on it; when it fails, everyone's session is cleared and callers get
 *   their original error. This is the auth behaviour that mostly decides
 *   whether "you got logged out and lost what you typed" can happen.
 */
const BASE = import.meta.env.VITE_API_URL || "/api/v1";

let accessToken = localStorage.getItem("en_access") || null;
let refreshToken = localStorage.getItem("en_refresh") || null;
let refreshing = null; // shared Promise<newToken> so 401s don't stampede
const authListeners = new Set();

export class ApiError extends Error {
  constructor(status, code, message, fieldErrors = [], traceId = "") {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : [];
    this.traceId = traceId;
  }
}

export function getAuth() {
  return { accessToken, refreshToken };
}

export function isAuthed() {
  return !!accessToken;
}

/** Restore a session on hard refresh: swap an (expired) access token for a new one. */
export async function rehydrateSession() {
  if (!refreshToken) return false;
  if (refreshing) {
    try { await refreshing; return !!accessToken; } catch { return false; }
  }
  refreshing = (async () => {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    const text = await res.text();
    if (!res.ok) {
      setAuth({});
      throw new ApiError(res.status, "SESSION_EXPIRED", "Your session has expired.");
    }
    const json = JSON.parse(text);
    setAuth({ accessToken: json.accessToken, refreshToken: json.refreshToken });
    return json.accessToken;
  })().finally(() => { refreshing = null; });
  try { await refreshing; return true; } catch { return false; }
}

export function setAuth(pair = {}) {
  if (pair.accessToken) {
    accessToken = pair.accessToken;
    localStorage.setItem("en_access", accessToken);
  } else {
    accessToken = null;
    localStorage.removeItem("en_access");
  }
  if (pair.refreshToken) {
    refreshToken = pair.refreshToken;
    localStorage.setItem("en_refresh", refreshToken);
  } else {
    refreshToken = null;
    localStorage.removeItem("en_refresh");
  }
  emit();
}

function emit() {
  authListeners.forEach((fn) => {
    try { fn({ accessToken, refreshToken }); } catch { /* listener error */ }
  });
}

export function onAuthChange(fn) {
  authListeners.add(fn);
  return () => authListeners.delete(fn);
}

async function parseError(response, body) {
  let code = "";
  let message = "";
  let fieldErrors = [];
  let traceId = "";
  try {
    if (body && typeof body === "string") {
      const json = JSON.parse(body);
      code = json.code || "";
      message = json.message || "";
      fieldErrors = json.fieldErrors || [];
      traceId = json.traceId || "";
    }
  } catch { /* non-JSON body */ }
  return new ApiError(response.status, code, message || `Request failed (${response.status})`, fieldErrors, traceId);
}

async function doFetch(path, { method = "GET", body, headers = {}, skipRefresh = false } = {}) {
  const send = async (token) => {
    const h = { ...headers };
    if (token) h.Authorization = `Bearer ${token}`;
    if (body !== undefined && !(body instanceof FormData) && !(body instanceof Blob)) {
      h["Content-Type"] = "application/json";
    }
    return fetch(`${BASE}${path}`, {
      method,
      headers: h,
      body: body instanceof FormData || body instanceof Blob ? body : body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  let res = await send(accessToken);

  // 401 + a refresh token + a call that makes sense to retry → refresh once.
  if (res.status === 401 && refreshToken && !skipRefresh && !path.startsWith("/auth/")) {
    if (!refreshing) {
      refreshing = (async () => {
        const r = await fetch(`${BASE}/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        const text = await r.text();
        if (!r.ok) {
          setAuth({}); // both cleared → every listener logs the user out
          throw new ApiError(r.status, "SESSION_EXPIRED", "Your session has expired. Please sign in again.");
        }
        const json = JSON.parse(text);
        setAuth({ accessToken: json.accessToken, refreshToken: json.refreshToken });
        return json.accessToken;
      })().finally(() => { refreshing = null; });
    }
    try {
      const fresh = await refreshing;
      res = await send(fresh);
    } catch (refreshErr) {
      // Session genuinely expired; surface the original 401 response.
      const text = await res.text();
      throw await parseError(res, text);
    }
  }

  const responseText = await res.text();
  if (res.status >= 400) throw await parseError(res, responseText);
  if (res.status === 204) return null;
  if (!responseText) return null;
  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) return JSON.parse(responseText);
  return responseText;
}

export const apiClient = {
  get: (path, opts) => doFetch(path, { ...opts, method: "GET" }),
  post: (path, body, opts) => doFetch(path, { ...opts, method: "POST", body }),
  put: (path, body, opts) => doFetch(path, { ...opts, method: "PUT", body }),
  patch: (path, body, opts) => doFetch(path, { ...opts, method: "PATCH", body }),
  delete: (path, opts) => doFetch(path, { ...opts, method: "DELETE" }),
};