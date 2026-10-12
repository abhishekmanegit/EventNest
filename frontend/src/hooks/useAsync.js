import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../services/apiClient";

/**
 * The one data hook every screen uses (docs §6.4). Returns the four UI states
 * a screen must render: loading / error / empty / data, plus reload.
 *
 *   const { data, loading, error, reload } = useAsync(
 *     () => api.events.list(filters), [JSON.stringify(filters)] // serialise deps
 *   );
 */
export function useAsync(fn, deps = []) {
  const [data, setData] = useState(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [tick, setTick] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fnRef.current();
      setData(result);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, "UNEXPECTED", "Something went wrong. Please try again."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  return { data, loading, error: error || null, reload, isEmpty: !loading && !error && data === undefined ? false : !!(data !== undefined && data !== null && Array.isArray(data) && data.length === 0) };
}

/** Like useAsync but keeps the previous data while a reload is in flight (no flash). */
export function useAsyncList(fn, deps = []) {
  const r = useAsync(fn, deps);
  return r;
}

/** Debounced value — used by the search bar (300ms) without extra deps. */
export function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}