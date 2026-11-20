// Batching cache: components ask for ONE id at a time, but ids requested in the same tick are
// sent as ONE request (e.g. a feed of 20 cards -> 1 users call + 1 media call, not 40 calls).
// Results are cached for the session; subscribers re-render when their ids arrive.

export function createBatchCache(fetchMany, keyOf, { maxBatch = 100, ttlMs = 10 * 60 * 1000 } = {}) {
  const cache = new Map();      // id -> { value, at }
  const pending = new Set();    // ids waiting for the next flush
  const inflight = new Set();
  const listeners = new Set();
  let timer = null;
  let version = 0; // bumps on every change, so React re-renders on value updates too (not only arrivals)

  function notify() {
    version++;
    listeners.forEach((fn) => fn());
  }

  async function flush() {
    timer = null;
    const ids = [...pending].slice(0, maxBatch);
    ids.forEach((id) => { pending.delete(id); inflight.add(id); });
    if (pending.size) timer = setTimeout(flush, 0);
    try {
      const items = await fetchMany(ids);
      const now = Date.now();
      (items || []).forEach((it) => cache.set(keyOf(it), { value: it, at: now }));
      ids.forEach((id) => { if (!cache.has(id)) cache.set(id, { value: null, at: now }); }); // not found / no access
    } catch {
      // leave uncached: a later render retries
    } finally {
      ids.forEach((id) => inflight.delete(id));
      notify();
    }
  }

  return {
    get(id) {
      const hit = cache.get(id);
      return hit && Date.now() - hit.at < ttlMs ? hit.value : undefined;
    },
    request(ids) {
      let queued = false;
      for (const id of ids) {
        if (id == null) continue;
        const hit = cache.get(id);
        if ((hit && Date.now() - hit.at < ttlMs) || inflight.has(id)) continue;
        pending.add(id);
        queued = true;
      }
      if (queued && !timer) timer = setTimeout(flush, 0);
    },
    set(id, value) {
      cache.set(id, { value, at: Date.now() });
      notify();
    },
    invalidate(id) {
      cache.delete(id);
      notify();
    },
    subscribe(fn) {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },
    version: () => version,
  };
}
