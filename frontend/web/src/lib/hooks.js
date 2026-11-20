import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { authFetch } from "../api/client";
import { Engagement, Media, Users } from "../api/endpoints";
import { createBatchCache } from "./batch";

// ---- shared caches --------------------------------------------------------------

export const usersCache = createBatchCache((ids) => Users.batch(ids), (u) => u.userId, { ttlMs: 5 * 60 * 1000 });
// Pre-signed URLs expire after 1 hour: keep media entries for 30 minutes.
export const mediaCache = createBatchCache((ids) => Media.batch(ids), (m) => m.id, { ttlMs: 30 * 60 * 1000 });
const statsCaches = {
  POST: createBatchCache((ids) => Engagement.summary("POST", ids), (s) => s.targetId, { ttlMs: 60 * 1000 }),
  VIDEO: createBatchCache((ids) => Engagement.summary("VIDEO", ids), (s) => s.targetId, { ttlMs: 60 * 1000 }),
};

function useBatched(cache, ids) {
  const key = ids.filter((x) => x != null).join(",");
  useEffect(() => { cache.request(key ? key.split(",").map(Number) : []); }, [cache, key]);
  // useSyncExternalStore: re-render when the cache notifies, read a stable snapshot string.
  useSyncExternalStore(cache.subscribe, () => key.split(",").map((id) => (cache.get(Number(id)) === undefined ? 0 : 1)).join(""));
  return (id) => cache.get(id);
}

export const useUsers = (ids) => useBatched(usersCache, ids);
export const useMedia = (ids) => useBatched(mediaCache, ids);
export function useStats(type, ids) {
  const cache = statsCaches[type];
  return { get: useBatched(cache, ids), set: (id, v) => cache.set(id, v) };
}

// ---- generic data hooks ------------------------------------------------------------

/** Run an async loader when deps change; exposes data / error / loading / reload. */
export function useQuery(loader, deps) {
  const [state, setState] = useState({ data: undefined, error: null, loading: true });
  const counter = useRef(0);
  const run = useCallback(() => {
    const ticket = ++counter.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader().then(
      (data) => ticket === counter.current && setState({ data, error: null, loading: false }),
      (error) => ticket === counter.current && setState({ data: undefined, error, loading: false }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { run(); }, [run]);
  return { ...state, reload: run, setData: (data) => setState((s) => ({ ...s, data })) };
}

/** Cursor-paginated list ("load more"). fetchPage(cursor) must return { items, nextCursor }. */
export function useCursorList(fetchPage, deps) {
  const [items, setItems] = useState([]);
  const [cursor, setCursor] = useState(null);
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const generation = useRef(0);

  const load = useCallback(async (from, gen) => {
    setLoading(true);
    try {
      const page = await fetchPage(from);
      if (gen !== generation.current) return;
      setItems((prev) => (from == null ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setDone(!page.nextCursor);
      setError(null);
    } catch (e) {
      if (gen === generation.current) setError(e);
    } finally {
      if (gen === generation.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    const gen = ++generation.current;
    setItems([]);
    setDone(false);
    load(null, gen);
  }, [load]);

  return {
    items, setItems, loading, error, done,
    loadMore: () => !loading && !done && load(cursor, generation.current),
    reload: () => { const gen = ++generation.current; load(null, gen); },
  };
}

/** Calls onVisible when the element scrolls into view (infinite scroll sentinel). */
export function useInView(onVisible) {
  const ref = useRef(null);
  const cb = useRef(onVisible);
  cb.current = onVisible;
  useEffect(() => {
    if (!ref.current) return undefined;
    const io = new IntersectionObserver(([e]) => e.isIntersecting && cb.current(), { rootMargin: "400px" });
    io.observe(ref.current);
    return () => io.disconnect();
  }, []);
  return ref;
}

/**
 * Server-Sent Events over fetch. The browser's EventSource cannot send an Authorization header,
 * so the stream is read with fetch + ReadableStream and parsed by hand; reconnects with back-off.
 */
export function useEventStream(path, enabled, onEvent) {
  const handler = useRef(onEvent);
  handler.current = onEvent;
  useEffect(() => {
    if (!enabled) return undefined;
    const controller = new AbortController();
    let stopped = false;
    let delay = 1000;

    async function connect() {
      while (!stopped) {
        try {
          const res = await authFetch(path, { headers: { Accept: "text/event-stream" }, signal: controller.signal });
          if (!res.ok || !res.body) throw new Error(`stream ${res.status}`);
          delay = 1000;
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            let sep;
            while ((sep = buffer.indexOf("\n\n")) >= 0) {
              const block = buffer.slice(0, sep);
              buffer = buffer.slice(sep + 2);
              const event = /^event:(.*)$/m.exec(block)?.[1]?.trim() || "message";
              const data = block.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5)).join("\n");
              if (data) {
                try { handler.current(event, JSON.parse(data)); } catch { /* ignore malformed */ }
              }
            }
          }
        } catch (e) {
          if (stopped || e.name === "AbortError") return;
        }
        await new Promise((r) => setTimeout(r, delay));
        delay = Math.min(delay * 2, 30000);
      }
    }
    connect();
    return () => { stopped = true; controller.abort(); };
  }, [path, enabled]);
}

export function timeAgo(value) {
  if (!value) return "";
  const t = typeof value === "number" ? value : Date.parse(value);
  const s = Math.max(1, Math.round((Date.now() - t) / 1000));
  const units = [[31536000, "y"], [2592000, "mo"], [604800, "w"], [86400, "d"], [3600, "h"], [60, "m"]];
  for (const [secs, label] of units) if (s >= secs) return `${Math.floor(s / secs)}${label}`;
  return `${s}s`;
}

export function compact(n) {
  if (n == null) return "0";
  return Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(n);
}

export function duration(seconds) {
  if (seconds == null) return "";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const mm = h ? String(m).padStart(2, "0") : String(m);
  return `${h ? `${h}:` : ""}${mm}:${String(s).padStart(2, "0")}`;
}
