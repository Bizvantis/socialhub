// Step 36 - fetch API, Promises and async/await.
//
// fetch() returns a Promise that resolves to a Response as soon as headers arrive, even for
// 4xx/5xx. So we must check response.ok ourselves; fetch only rejects on network failure.
import { API_BASE } from "./config.js";

/** Error with the HTTP status and the server's message, so the UI can react to each case. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/**
 * POST JSON and unwrap SocialHub's envelope { success, message, data }.
 * `async` makes the function return a Promise; `await` pauses until each Promise settles.
 */
export async function postJson(path, body) {
  let response;
  try {
    response = await fetch(API_BASE + path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    // TypeError from fetch = the request never reached the server (offline, CORS, server down).
    throw new ApiError(0, "Cannot reach the server. Check your connection and try again.");
  }

  // Some responses (e.g. 204) have no body; guard JSON parsing.
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, payload.message || `Request failed (${response.status})`);
  }
  return payload.data;
}
