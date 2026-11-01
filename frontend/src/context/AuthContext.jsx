import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../services/api";
import { setAuth, onAuthChange, isAuthed, rehydrateSession, getAuth } from "../services/apiClient";

/**
 * Auth holds { user, ready }. Tokens live in apiClient (memory + localStorage);
 * the refresh token persists across reloads, and on first render the context
 * rehydrates by refreshing → /auth/me (docs §6.5 "Auth state").
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (isAuthed()) {
        try {
          const me = await api.auth.me();
          if (!cancelled) setUser(me);
        } catch { /* token invalid → silent logout below */ }
      } else if (getAuth().refreshToken) {
        const ok = await rehydrateSession();
        if (ok && !cancelled) {
          try { const me = await api.auth.me(); if (!cancelled) setUser(me); } catch { /* stayed anonymous */ }
        }
      }
      if (!cancelled) setReady(true);
    })();
    const unsub = onAuthChange(() => {
      if (!isAuthed()) setUser(null);
    });
    return () => { cancelled = true; unsub(); };
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await api.auth.login(email, password);
    setAuth({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
    return res.user;
  }, []);

  const register = useCallback(async (type, payload) => {
    const res = type === "student"
      ? await api.auth.registerStudent(payload)
      : await api.auth.registerOrganizer(payload);
    setAuth({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      const { refreshToken } = getAuth();
      if (refreshToken) await api.auth.logout(refreshToken);
    } catch { /* logout must not be able to fail visibly */ }
    setAuth({});
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, ready, login, register, logout, isAuthed: () => isAuthed() }),
    [user, ready, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}