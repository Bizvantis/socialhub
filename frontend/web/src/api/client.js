// HTTP client for the SocialHub API gateway.
//
// Token strategy:
//  - access token (15 min JWT) lives only in memory: an XSS bug cannot read it from storage;
//  - refresh token (7 days, rotated on every use) is kept in localStorage so a reload stays logged in;
//  - on a 401 the client refreshes ONCE and retries; concurrent 401s share one refresh request
//    ("single flight"), otherwise rotation would invalidate all but the first refresh.

export const API_BASE = import.meta.env.VITE_API_BASE ?? (import.meta.env.DEV ? "http://localhost:8080" : "");
const REFRESH_KEY = "sh.refreshToken";

let accessToken = null;
let refreshInFlight = null;
let onSessionEnded = () => {};

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const tokens = {
  get access() { return accessToken; },
  get refresh() { return localStorage.getItem(REFRESH_KEY); },
  set(access, refresh) {
    accessToken = access;
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    accessToken = null;
    localStorage.removeItem(REFRESH_KEY);
  },
  onSessionEnded(fn) { onSessionEnded = fn; },
};

/** Exchange the refresh token for a new pair. Shared by all callers while in flight. */
export function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = tokens.refresh;
      if (!refreshToken) throw new ApiError(401, "Not signed in");
      const res = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      const payload = await res.json().catch(() => null);
      if (!res.ok) {
        tokens.clear();
        onSessionEnded();
        throw new ApiError(res.status, payload?.message || "Session expired");
      }
      tokens.set(payload.data.accessToken, payload.data.refreshToken);
      return payload.data;
    })().finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

function headers(json) {
  const h = {};
  if (json) h["Content-Type"] = "application/json";
  if (accessToken) h.Authorization = `Bearer ${accessToken}`;
  return h;
}

/** Calls the API and unwraps { success, message, data }. Throws ApiError on non-2xx. */
export async function api(path, { method = "GET", body, signal } = {}) {
  const exec = () => fetch(`${API_BASE}${path}`, {
    method, signal, headers: headers(body !== undefined), body: body === undefined ? undefined : JSON.stringify(body),
  });
  let res;
  try {
    res = await exec();
    if (res.status === 401 && tokens.refresh && !path.startsWith("/api/auth/")) {
      await refreshSession();
      res = await exec();
    }
  } catch (err) {
    if (err instanceof ApiError || err.name === "AbortError") throw err;
    throw new ApiError(0, "Cannot reach SocialHub. Check your connection.");
  }
  const payload = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, payload?.message || `Request failed (${res.status})`);
  return payload?.data;
}

/** Raw authorised fetch (streams, non-JSON). */
export async function authFetch(path, init = {}) {
  const exec = () => fetch(`${API_BASE}${path}`, { ...init, headers: { ...headers(false), ...(init.headers || {}) } });
  let res = await exec();
  if (res.status === 401 && tokens.refresh) {
    await refreshSession();
    res = await exec();
  }
  return res;
}

/** Media playlist URLs come back relative (/api/media/...): make them absolute for the player. */
export const absolute = (url) => (url && url.startsWith("/") ? `${API_BASE}${url}` : url);

/** Decode the JWT payload (roles, username) without verifying: the server always verifies. */
export function claims() {
  if (!accessToken) return null;
  try {
    return JSON.parse(atob(accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}
