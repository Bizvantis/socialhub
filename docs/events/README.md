# Event catalogue

## Kafka (domain events = facts that happened)

Every event uses the same envelope (`backend/common/.../EventEnvelope.java`):

```json
{ "eventId": "uuid", "type": "VIDEO_PUBLISHED", "version": 1,
  "occurredAt": "2026-11-20T10:00:00Z", "aggregateId": "42", "payload": { ... } }
```

- Written through the **transactional outbox** (`outbox_events` table, relay polls with
  `FOR UPDATE SKIP LOCKED`), so an event exists if and only if the change committed.
- Message key = `aggregateId`: all events of one entity stay ordered in one partition.
- Consumers are **idempotent** (`processed_events` marker in the same DB transaction, naturally
  idempotent Redis writes, or Elasticsearch external versioning).
- Failures: 3 attempts with exponential back-off (1 s, 2 s, 4 s), then `<topic>-dlt`.

| Topic | Producer | Types | Main consumers |
|---|---|---|---|
| `account-events` | auth-service | USER_REGISTERED | notification (contact email) |
| `user-events` | user-service | USER_FOLLOWED, USER_UNFOLLOWED, PROFILE_UPDATED | feed (graph), notification, search |
| `media-events` | media-service | MEDIA_UPLOADED, MEDIA_READY, MEDIA_FAILED, MEDIA_DELETED | video, post (status), media (transcode dispatch) |
| `video-events` | video-service | CHANNEL_CREATED/UPDATED/SUBSCRIBED/UNSUBSCRIBED, VIDEO_PUBLISHED, VIDEO_UPSERTED (versioned snapshot), VIDEO_DELETED | feed, search, notification, media (restricted flag), engagement (cache) |
| `video-views` | video-service | VIDEO_VIEWED (direct send, best effort) | search (view counts) |
| `post-events` | post-service | POST_CREATED, POST_UPSERTED (versioned snapshot), POST_DELETED, USER_MENTIONED, STORY_CREATED | feed, search, notification, engagement (cache) |
| `engagement-events` | engagement-service | COMMENT_CREATED, COMMENT_DELETED, REACTION_SET, REACTION_REMOVED, SHARED | notification, feed (ranking, affinity) |
| `moderation-events` | moderation-service | CONTENT_REMOVED, CONTENT_RESTORED, USER_SUSPENDED, USER_REINSTATED | video, post, engagement, auth, notification |

Consumer groups: one per service (e.g. `feed-service`), plus dedicated groups where a service
needs an independent cursor (`media-service.transcode-dispatcher`, `video-service.moderation`, ...).

## RabbitMQ (tasks = work to be done)

| Exchange / queue | Producer | Consumer | Retry |
|---|---|---|---|
| `media.tasks` -> `media.transcode` (key `transcode`) | media-service (from MEDIA_UPLOADED) | video-worker | 3 attempts, 15 s delay via `media.transcode.retry` (TTL), then `media.transcode.dlq` |
| `media.results` -> `media.results` | video-worker | media-service | idempotent by media status |
| `notify.tasks` -> `notify.email` (key `email`) | notification-service, auth-service (password reset) | notification-service mailer | 3 attempts, 30 s delay, then `notify.email.dlq` |

Why both brokers: Kafka is a replayable log of facts that many services read independently;
RabbitMQ is a work queue where one worker takes one job, acknowledges it, retries with delay
and parks poison messages. Secrets (password-reset links) go only through RabbitMQ, never into
the retained Kafka log.
