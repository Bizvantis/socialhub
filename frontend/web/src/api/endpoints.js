// One function per backend endpoint, grouped by service. Components never build URLs themselves.
import { api } from "./client";

const q = (params) => {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== "") s.set(k, v); });
  const str = s.toString();
  return str ? `?${str}` : "";
};

export const Auth = {
  login: (emailOrUsername, password) => api("/api/auth/login", { method: "POST", body: { emailOrUsername, password } }),
  register: (email, username, password) => api("/api/auth/register", { method: "POST", body: { email, username, password } }),
  logout: (refreshToken) => api("/api/auth/logout", { method: "POST", body: { refreshToken } }),
  forgot: (email) => api("/api/auth/password/forgot", { method: "POST", body: { email } }),
  reset: (token, newPassword) => api("/api/auth/password/reset", { method: "POST", body: { token, newPassword } }),
};

export const Users = {
  me: () => api("/api/users/me"),
  updateMe: (body) => api("/api/users/me", { method: "PUT", body }),
  get: (id) => api(`/api/users/${id}`),
  byUsername: (name) => api(`/api/users/by-username/${encodeURIComponent(name)}`),
  batch: (ids) => api(`/api/users/batch${q({ ids: ids.join(",") })}`),
  follow: (id) => api(`/api/users/${id}/follow`, { method: "POST" }),
  unfollow: (id) => api(`/api/users/${id}/follow`, { method: "DELETE" }),
  followers: (id, page = 0) => api(`/api/users/${id}/followers${q({ page })}`),
  following: (id, page = 0) => api(`/api/users/${id}/following${q({ page })}`),
};

export const Media = {
  startUpload: (purpose, file) => api("/api/media/uploads", {
    method: "POST", body: { purpose, contentType: file.type, sizeBytes: file.size, fileName: file.name },
  }),
  complete: (id) => api(`/api/media/${id}/complete`, { method: "POST" }),
  get: (id) => api(`/api/media/${id}`),
  batch: (ids) => api("/api/media/batch", { method: "POST", body: { ids } }),
};

export const Channels = {
  create: (body) => api("/api/channels", { method: "POST", body }),
  mine: () => api("/api/channels/me"),
  get: (id) => api(`/api/channels/${id}`),
  byHandle: (handle) => api(`/api/channels/handle/${encodeURIComponent(handle)}`),
  update: (id, body) => api(`/api/channels/${id}`, { method: "PATCH", body }),
  subscribe: (id) => api(`/api/channels/${id}/subscribe`, { method: "POST" }),
  unsubscribe: (id) => api(`/api/channels/${id}/subscribe`, { method: "DELETE" }),
  videos: (id, page = 0) => api(`/api/channels/${id}/videos${q({ page })}`),
  subscriptions: () => api("/api/channels/me/subscriptions?size=50"),
};

export const Videos = {
  create: (body) => api("/api/videos", { method: "POST", body }),
  list: (params) => api(`/api/videos${q(params)}`),
  get: (id) => api(`/api/videos/${id}`),
  update: (id, body) => api(`/api/videos/${id}`, { method: "PATCH", body }),
  remove: (id) => api(`/api/videos/${id}`, { method: "DELETE" }),
  related: (id) => api(`/api/videos/${id}/related`),
  view: (id, watchedSeconds) => api(`/api/videos/${id}/views`, { method: "POST", body: { watchedSeconds } }),
};

export const Library = {
  history: () => api("/api/history"),
  clearHistory: () => api("/api/history", { method: "DELETE" }),
  watchLater: () => api("/api/watch-later"),
  addWatchLater: (id) => api(`/api/watch-later/${id}`, { method: "POST" }),
  removeWatchLater: (id) => api(`/api/watch-later/${id}`, { method: "DELETE" }),
  myPlaylists: () => api("/api/playlists/me"),
  playlist: (id) => api(`/api/playlists/${id}`),
  createPlaylist: (body) => api("/api/playlists", { method: "POST", body }),
  addToPlaylist: (id, videoId) => api(`/api/playlists/${id}/videos`, { method: "POST", body: { videoId } }),
  removeFromPlaylist: (id, videoId) => api(`/api/playlists/${id}/videos/${videoId}`, { method: "DELETE" }),
};

export const Posts = {
  create: (body) => api("/api/posts", { method: "POST", body }),
  get: (id) => api(`/api/posts/${id}`),
  update: (id, body) => api(`/api/posts/${id}`, { method: "PATCH", body }),
  remove: (id) => api(`/api/posts/${id}`, { method: "DELETE" }),
  byUser: (id, before) => api(`/api/posts/user/${id}${q({ before })}`),
  explore: (type, before) => api(`/api/posts/explore${q({ type, before })}`),
  byHashtag: (tag, before) => api(`/api/hashtags/${encodeURIComponent(tag)}/posts${q({ before })}`),
  trendingHashtags: () => api("/api/hashtags/trending?limit=12"),
};

export const Stories = {
  create: (body) => api("/api/stories", { method: "POST", body }),
  tray: () => api("/api/stories/feed"),
  view: (id) => api(`/api/stories/${id}/view`, { method: "POST" }),
  viewers: (id) => api(`/api/stories/${id}/viewers`),
  remove: (id) => api(`/api/stories/${id}`, { method: "DELETE" }),
};

export const Engagement = {
  summary: (targetType, ids) => api(`/api/reactions/summary${q({ targetType, ids: ids.join(",") })}`),
  react: (targetType, targetId, kind) => api("/api/reactions", { method: "PUT", body: { targetType, targetId, kind } }),
  unreact: (targetType, targetId) => api(`/api/reactions${q({ targetType, targetId })}`, { method: "DELETE" }),
  comments: (targetType, targetId, sort, cursor) => api(`/api/comments${q({ targetType, targetId, sort, cursor })}`),
  replies: (id, cursor) => api(`/api/comments/${id}/replies${q({ cursor })}`),
  comment: (targetType, targetId, body, parentId) => api("/api/comments", { method: "POST", body: { targetType, targetId, body, parentId } }),
  deleteComment: (id) => api(`/api/comments/${id}`, { method: "DELETE" }),
  bookmark: (targetType, targetId) => api("/api/bookmarks", { method: "POST", body: { targetType, targetId } }),
  unbookmark: (targetType, targetId) => api(`/api/bookmarks/${targetType}/${targetId}`, { method: "DELETE" }),
  bookmarks: (type, cursor) => api(`/api/bookmarks${q({ type, cursor })}`),
  share: (targetType, targetId) => api("/api/shares", { method: "POST", body: { targetType, targetId, channel: "LINK" } }),
};

export const Feed = {
  home: (cursor) => api(`/api/feed/home${q({ cursor })}`),
  following: (cursor) => api(`/api/feed/following${q({ cursor })}`),
  subscriptions: (cursor) => api(`/api/feed/subscriptions${q({ cursor })}`),
};

export const Search = {
  all: (query) => api(`/api/search${q({ q: query })}`),
  videos: (params) => api(`/api/search/videos${q(params)}`),
  autocomplete: (query) => api(`/api/search/autocomplete${q({ q: query })}`),
};

export const Notifications = {
  list: (cursor) => api(`/api/notifications${q({ cursor })}`),
  unread: () => api("/api/notifications/unread-count"),
  read: (id) => api(`/api/notifications/${id}/read`, { method: "POST" }),
  readAll: () => api("/api/notifications/read-all", { method: "POST" }),
  preferences: (emailEnabled) => api("/api/notifications/preferences", { method: "PUT", body: { emailEnabled } }),
};

export const Moderation = {
  report: (targetType, targetId, reason, details) => api("/api/reports", { method: "POST", body: { targetType, targetId, reason, details } }),
  queue: () => api("/api/admin/queue"),
  stats: () => api("/api/admin/stats"),
  actions: () => api("/api/admin/actions"),
  remove: (targetType, targetId, reason) => api("/api/admin/content/remove", { method: "POST", body: { targetType, targetId, reason } }),
  restore: (targetType, targetId, reason) => api("/api/admin/content/restore", { method: "POST", body: { targetType, targetId, reason } }),
  dismiss: (targetType, targetId, reason) => api("/api/admin/reports/dismiss", { method: "POST", body: { targetType, targetId, reason } }),
  suspend: (userId, reason) => api(`/api/admin/users/${userId}/suspend`, { method: "POST", body: { reason } }),
  reinstate: (userId) => api(`/api/admin/users/${userId}/reinstate`, { method: "POST", body: {} }),
};
