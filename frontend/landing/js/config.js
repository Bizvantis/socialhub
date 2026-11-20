// Where the API gateway and the React app live. In production both are served by Nginx from
// the same origin (landing at /, app at /app/, API at /api/), so these become relative paths.
const sameOrigin = !["localhost", "127.0.0.1"].includes(location.hostname) || location.port === "" || location.port === "80";

export const API_BASE = sameOrigin ? "" : "http://localhost:8080";
export const APP_URL = sameOrigin ? "/app/" : "http://localhost:5173/";
