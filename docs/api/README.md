# API reference

All responses use `{ "success": bool, "message": string, "data": ... }`. Lists use either
offset pages `{content, page, size, totalElements, hasNext}` or cursor pages `{items, nextCursor}`
(pass `cursor`/`before` back for the next page). Auth: `Authorization: Bearer <accessToken>`.
"public" = works anonymously (a token, if sent, is still validated).

## auth-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/auth/register | public | `{email, username, password}` |
| POST | /api/auth/login | public | `{emailOrUsername, password}` -> access + refresh token |
| POST | /api/auth/refresh | public | `{refreshToken}` -> rotated pair |
| POST | /api/auth/logout | public | `{refreshToken}` revokes the session family |
| POST | /api/auth/password/forgot | public | `{email}` (always 200) |
| POST | /api/auth/password/reset | public | `{token, newPassword}` |

## user-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET / PUT | /api/users/me | user | own profile / update `{displayName, bio, privateAccount}` |
| GET | /api/users/{id}, /api/users/by-username/{name} | user | profile with `followedByMe` |
| GET | /api/users/batch?ids= | user | author cards (max 200) |
| POST / DELETE | /api/users/{id}/follow | user | follow / unfollow (idempotent) |
| GET | /api/users/{id}/followers, /following | user | paged lists |

## media-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/media/uploads | user | `{purpose, contentType, sizeBytes}` -> pre-signed PUT |
| POST | /api/media/{id}/complete | owner | verify the upload |
| GET | /api/media/{id} | public | signed URLs and status |
| POST | /api/media/batch | public | `{ids}` |
| GET | /api/media/{id}/hls/** | public | HLS playlists with signed segment URLs |
| DELETE | /api/media/{id} | owner/admin | delete |

## video-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/channels | user | create channel `{handle, name, description}` |
| GET | /api/channels/{id}, /handle/{h}, /me, /me/subscriptions | public / user | channels |
| PATCH | /api/channels/{id} | owner | name, description, avatar/banner media |
| POST / DELETE | /api/channels/{id}/subscribe | user | subscribe / unsubscribe |
| GET | /api/channels/{id}/videos, /playlists | public | listings |
| POST | /api/videos | channel owner | publish an uploaded file |
| GET | /api/videos?category&sort, /{id}, /{id}/related | public | catalogue, watch page |
| PATCH / DELETE | /api/videos/{id} | owner/admin | edit / delete |
| POST | /api/videos/{id}/views | public | count a view + save resume position |
| * | /api/playlists/**, /api/history/**, /api/watch-later/** | user | library |

## post-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/posts | user | `{type: IMAGE / CAROUSEL / REEL, mediaIds, caption, visibility}` |
| GET / PATCH / DELETE | /api/posts/{id} | user / owner | post |
| GET | /api/posts/user/{id}, /explore?type, /mentions/me | user | grids (cursor) |
| GET | /api/hashtags/trending, /suggest?q, /{tag}/posts | public | hashtags |
| POST | /api/stories | user | `{mediaId, caption}` |
| GET | /api/stories/feed, /user/{id}, /{id}/viewers | user | tray, stories, viewers (author only) |
| POST | /api/stories/{id}/view | user | mark seen |

## engagement-service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/comments | user | `{targetType, targetId, body, parentId?}` |
| GET | /api/comments?targetType&targetId&sort, /{id}/replies | public | threads (cursor) |
| PATCH / DELETE | /api/comments/{id} | author / creator / admin | edit / delete |
| PUT / DELETE | /api/reactions | user | LIKE (everything), DISLIKE (videos) |
| GET | /api/reactions/summary?targetType&ids | public | counts + my state |
| POST / DELETE / GET | /api/bookmarks | user | saved items |
| POST | /api/shares | user | share counter |

## feed, search, notifications, moderation
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | /api/feed/home, /following, /subscriptions | user | feeds (cursor) |
| GET | /api/search?q, /videos, /posts, /users, /channels, /autocomplete, /hashtags, /videos/{id}/related | public | search |
| GET | /api/notifications, /unread-count, /stream (SSE) | user | inbox, live stream |
| POST / PUT | /api/notifications/{id}/read, /read-all, /preferences | user | read state, email preference |
| POST | /api/reports | user | `{targetType, targetId, reason, details}` |
| GET / POST | /api/admin/queue, /stats, /actions, /content/remove, /content/restore, /reports/dismiss, /users/{id}/suspend, /reinstate | ADMIN | moderation |
