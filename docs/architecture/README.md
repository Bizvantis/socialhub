# Architecture

## Request path

```mermaid
sequenceDiagram
    participant B as Browser
    participant N as Nginx
    participant G as API Gateway
    participant S as Service
    participant DB as MySQL
    participant O as Outbox relay
    participant K as Kafka
    B->>N: HTTPS /api/... (Bearer access token)
    N->>G: proxy
    G->>G: verify JWT, strip client X-User-* headers, rate limit (Redis), suspension check
    G->>S: lb://service + X-User-Id / X-Username / X-User-Roles
    S->>DB: business change + outbox row (one transaction)
    S-->>B: 200 {success, message, data}
    O->>DB: poll unpublished rows (SKIP LOCKED)
    O->>K: publish (acks=all, idempotent producer)
```

## Upload and transcoding

```mermaid
sequenceDiagram
    participant B as Browser
    participant M as media-service
    participant S3 as S3
    participant K as Kafka
    participant Q as RabbitMQ
    participant W as video-worker
    participant V as video-service
    B->>M: POST /api/media/uploads {purpose, type, size}
    M-->>B: pre-signed PUT (type + length signed)
    B->>S3: PUT file (bytes never touch our servers)
    B->>M: POST /api/media/{id}/complete
    M->>S3: HeadObject (verify size/type)
    M->>K: MEDIA_UPLOADED (outbox)
    K->>M: dispatcher consumes
    M->>Q: transcode task (publisher confirm before offset commit)
    Q->>W: task (prefetch 1)
    W->>S3: download, ffmpeg, upload thumb + HLS
    W->>Q: result READY
    Q->>M: mark READY
    M->>K: MEDIA_READY (outbox)
    K->>V: video READY + published, VIDEO_PUBLISHED -> feed, search, notifications
```

## Data ownership

Each service has its own MySQL schema and database user (`infra/mysql-init/01-schemas.sql`);
no service reads another's tables. Cross-service data needs are met by events (read models in
Redis/Elasticsearch) or by small internal APIs used only for validation.

| Store | Holds |
|---|---|
| MySQL | source of truth: users, profiles, follows, media metadata, channels, videos, posts, comments, reactions, notifications, reports |
| Redis | caches (profiles, follow checks, targets), rate-limit buckets, view counters, story activity, feeds and follow graph (feed-service), SSE pub/sub, suspension denylist |
| Elasticsearch | search read model (videos, posts, users, channels) |
| S3 | originals, thumbnails, HLS renditions; private bucket, pre-signed URLs only |

## Security model

- Only Nginx is public. The gateway validates JWTs and forwards identity headers; services trust
  those headers because they are unreachable from outside (and the gateway strips client copies).
- `/internal/**` is rejected at Nginx and at the gateway.
- Access tokens: 15 min, held in memory by the SPA. Refresh tokens: 7 days, rotated on every use,
  stored hashed; reuse after a 30 s grace window revokes the whole session family.
- Suspension takes effect immediately through a Redis denylist checked by the gateway.
- Media: Content-Type/Content-Length signed into upload URLs, HeadObject verification, files of
  private videos restricted to their owner, bucket CORS limited to the site origin.
