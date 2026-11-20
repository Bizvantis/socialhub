import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Engagement, Users } from "../api/endpoints";
import { compact, duration, timeAgo, useMedia, useStats, usersCache } from "../lib/hooks";
import { Avatar, MediaImage, UserName } from "./ui";
import { Icon } from "./icons";

/** Thumbnail: custom thumbnail if set, else the auto-generated one from the transcoder. */
export function VideoThumb({ video, className = "" }) {
  const get = useMedia([video.thumbnailMediaId, video.mediaId]);
  const custom = video.thumbnailMediaId ? get(video.thumbnailMediaId)?.url : null;
  const auto = get(video.mediaId)?.thumbnailUrl;
  const url = custom || auto;
  return (
    <div className={`relative aspect-video overflow-hidden rounded-xl bg-zinc-200 dark:bg-zinc-800 ${className}`}>
      {url ? <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
        : <div className="grid h-full place-items-center text-sm text-zinc-500">{video.status === "PROCESSING" ? "Processing…" : <Icon name="video" size={28} />}</div>}
      {video.durationSeconds != null && (
        <span className="absolute bottom-1.5 right-1.5 rounded bg-black/75 px-1.5 py-0.5 text-xs font-semibold text-white">{duration(video.durationSeconds)}</span>
      )}
    </div>
  );
}

export function VideoCard({ video, compactLayout = false }) {
  const channel = video.channel;
  if (compactLayout) {
    return (
      <Link to={`/watch/${video.id}`} className="group flex gap-3">
        <VideoThumb video={video} className="w-40 shrink-0" />
        <div className="min-w-0">
          <h3 className="line-clamp-2 text-sm font-semibold group-hover:underline">{video.title}</h3>
          <p className="mt-1 text-xs text-zinc-500">{channel?.name}</p>
          <p className="text-xs text-zinc-500">{compact(video.viewCount)} views · {timeAgo(video.publishedAt)}</p>
        </div>
      </Link>
    );
  }
  return (
    <div className="group">
      <Link to={`/watch/${video.id}`}><VideoThumb video={video} className="transition group-hover:rounded-md" /></Link>
      <div className="mt-3 flex gap-3">
        {channel && <Link to={`/c/${channel.handle}`}><ChannelAvatar channel={channel} /></Link>}
        <div className="min-w-0">
          <Link to={`/watch/${video.id}`}><h3 className="line-clamp-2 font-semibold leading-snug">{video.title}</h3></Link>
          {channel && <Link to={`/c/${channel.handle}`} className="mt-0.5 block text-sm text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200">{channel.name}</Link>}
          <p className="text-sm text-zinc-500">
            {compact(video.viewCount)} views · {video.publishedAt ? timeAgo(video.publishedAt) : video.status?.toLowerCase()}
            {video.visibility && video.visibility !== "PUBLIC" && <span className="ml-1.5 rounded bg-zinc-200 px-1 text-xs dark:bg-zinc-800">{video.visibility.toLowerCase()}</span>}
          </p>
        </div>
      </div>
    </div>
  );
}

export function ChannelAvatar({ channel, size = 36 }) {
  return (
    <div className="shrink-0 overflow-hidden rounded-full" style={{ width: size, height: size }}>
      <MediaImage id={channel.avatarMediaId} className="h-full w-full"
        fallback={<div className="grid h-full w-full place-items-center bg-zinc-200 font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">{channel.name?.[0]?.toUpperCase()}</div>} />
    </div>
  );
}

/** Like / comment / share / save bar. Optimistic: the UI updates first, then the request. */
export function ActionBar({ type, id, onComment }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const stats = useStats(type, [id]);
  const s = stats.get(id);

  /**
   * Optimistic update: show the result immediately, then reconcile with the server's answer
   * (or roll back on failure). Both callbacks receive the LATEST cached state, not the state
   * captured at render time, so quick successive clicks never overwrite each other.
   */
  async function run(optimistic, request) {
    if (!user) { navigate("/login"); return; }
    const before = stats.latest(id);
    stats.set(id, optimistic(before));
    try {
      stats.set(id, await request(before));
    } catch {
      stats.set(id, before); // roll back on failure
    }
  }

  if (!s) return <div className="h-9" />;
  const liked = s.myReaction === "LIKE";
  const disliked = s.myReaction === "DISLIKE";
  const react = (kind) => run(
    (c) => {
      const on = c.myReaction === kind;
      const next = { ...c, myReaction: on ? null : kind };
      if (kind === "LIKE") { next.likes = c.likes + (on ? -1 : 1); if (c.myReaction === "DISLIKE") next.dislikes = c.dislikes - 1; }
      else { next.dislikes = c.dislikes + (on ? -1 : 1); if (c.myReaction === "LIKE") next.likes = c.likes - 1; }
      return next;
    },
    (c) => (c.myReaction === kind ? Engagement.unreact(type, id) : Engagement.react(type, id, kind)),
  );
  const act = "inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-zinc-600 transition hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100";
  return (
    <div className="-ml-2.5 flex flex-wrap items-center gap-0.5">
      <button className={`${act} ${liked ? "!text-heart" : ""}`} aria-pressed={liked} aria-label="Like" onClick={() => react("LIKE")}>
        <Icon name="heart" filled={liked} /> <span className="tabular-nums">{compact(s.likes)}</span>
      </button>
      {type === "VIDEO" && (
        <button className={`${act} ${disliked ? "!text-zinc-900 dark:!text-white" : ""}`} aria-pressed={disliked} aria-label="Dislike" onClick={() => react("DISLIKE")}>
          <Icon name="thumbDown" filled={disliked} size={19} />
        </button>
      )}
      <button className={act} onClick={onComment} aria-label="Comments"><Icon name="comment" /> <span className="tabular-nums">{compact(s.comments)}</span></button>
      <button className={act} aria-label="Share" onClick={() => run((c) => ({ ...c, shares: c.shares + 1 }), async () => {
        const path = type === "VIDEO" ? `/watch/${id}` : `/p/${id}`;
        await navigator.clipboard?.writeText(window.location.origin + import.meta.env.BASE_URL.replace(/\/$/, "") + path).catch(() => {});
        return Engagement.share(type, id);
      })}><Icon name="share" size={19} />{s.shares ? <span className="tabular-nums">{compact(s.shares)}</span> : <span>Share</span>}</button>
      <button className={`${act} ml-auto ${s.bookmarked ? "!text-zinc-900 dark:!text-white" : ""}`} aria-pressed={s.bookmarked}
        onClick={() => run((c) => ({ ...c, bookmarked: !c.bookmarked }), async (c) => {
          await (c.bookmarked ? Engagement.unbookmark(type, id) : Engagement.bookmark(type, id));
          return { ...stats.latest(id), bookmarked: !c.bookmarked };
        })}>
        <Icon name="bookmark" filled={s.bookmarked} size={19} />{s.bookmarked ? "Saved" : "Save"}
      </button>
    </div>
  );
}

/** Caption with clickable #hashtags and @mentions. */
export function RichText({ text }) {
  if (!text) return null;
  const parts = text.split(/([#@][\p{L}\p{M}\p{N}_]+)/u);
  return (
    <p className="whitespace-pre-wrap break-words">
      {parts.map((p, i) => {
        if (p.startsWith("#")) return <Link key={i} to={`/tags/${p.slice(1).toLowerCase()}`} className="font-medium text-accent hover:underline">{p}</Link>;
        if (p.startsWith("@") && p.length > 3) return <Link key={i} to={`/u/${p.slice(1)}`} className="font-medium text-accent hover:underline">{p}</Link>;
        return <span key={i}>{p}</span>;
      })}
    </p>
  );
}

/** Instagram-style post: header, media (with carousel), actions, caption. */
export function PostCard({ post }) {
  const [index, setIndex] = useState(0);
  const navigate = useNavigate();
  const get = useMedia(post.mediaIds);
  const media = get(post.mediaIds[index]);
  return (
    <article className="card overflow-hidden">
      <header className="flex items-center gap-3 p-3">
        <Avatar userId={post.authorId} size={36} />
        <div className="min-w-0 flex-1 text-sm">
          <UserName userId={post.authorId} />
          <div className="text-xs text-zinc-500">{timeAgo(post.createdAt)}{post.visibility === "FOLLOWERS" && " · Followers"}</div>
        </div>
        <Link to={`/p/${post.id}`} className="btn-ghost px-2" aria-label="Open post"><Icon name="more" /></Link>
      </header>
      <div className="relative bg-black">
        {post.type === "REEL" ? (
          media?.playbackUrl ? <ReelPlayer media={media} /> : <div className="grid aspect-[9/16] max-h-[70vh] w-full place-items-center text-sm text-zinc-400">{post.status === "PROCESSING" ? "Processing reel…" : "Loading…"}</div>
        ) : (
          <MediaImage id={post.mediaIds[index]} className="aspect-square w-full" />
        )}
        {post.mediaIds.length > 1 && (
          <>
            {index > 0 && <button onClick={() => setIndex(index - 1)} className="absolute left-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full bg-white/90 text-zinc-900 shadow" aria-label="Previous image"><Icon name="chevronLeft" size={16} strokeWidth={2.25} /></button>}
            {index < post.mediaIds.length - 1 && <button onClick={() => setIndex(index + 1)} className="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full bg-white/90 text-zinc-900 shadow" aria-label="Next image"><Icon name="chevronRight" size={16} strokeWidth={2.25} /></button>}
            <div className="absolute bottom-2 left-0 right-0 flex justify-center gap-1">
              {post.mediaIds.map((m, i) => <span key={m} className={`h-1.5 w-1.5 rounded-full ${i === index ? "bg-white" : "bg-white/40"}`} />)}
            </div>
          </>
        )}
      </div>
      <div className="grid gap-1.5 px-3 pb-3.5 pt-2">
        <ActionBar type="POST" id={post.id} onComment={() => navigate(`/p/${post.id}`)} />
        {post.caption && <div className="text-[0.925rem] leading-relaxed"><UserName userId={post.authorId} className="mr-1.5" /><RichText text={post.caption} /></div>}
      </div>
    </article>
  );
}

function ReelPlayer({ media }) {
  return <video className="mx-auto aspect-[9/16] max-h-[70vh] w-full bg-black object-contain" controls playsInline loop poster={media.thumbnailUrl} src={undefined}
    ref={(el) => { if (el && !el.dataset.ready) { el.dataset.ready = "1"; import("hls.js").then(({ default: Hls }) => {
      const url = media.playbackUrl.startsWith("/") ? `${import.meta.env.VITE_API_BASE ?? (import.meta.env.DEV ? "http://localhost:8080" : "")}${media.playbackUrl}` : media.playbackUrl;
      if (Hls.isSupported()) { const h = new Hls(); h.loadSource(url); h.attachMedia(el); } else el.src = url;
    }); } }} />;
}

/** Follow / unfollow toggle that keeps the shared user cache in sync. */
export function FollowButton({ userId, initiallyFollowing, onChange }) {
  const { user } = useAuth();
  const [following, setFollowing] = useState(initiallyFollowing);
  const [busy, setBusy] = useState(false);
  if (!user || user.userId === userId) return null;
  async function toggle() {
    setBusy(true);
    try {
      await (following ? Users.unfollow(userId) : Users.follow(userId));
      setFollowing(!following);
      usersCache.invalidate(userId);
      onChange?.(!following);
    } finally {
      setBusy(false);
    }
  }
  return (
    <button className={following ? "btn-secondary" : "btn-primary"} onClick={toggle} disabled={busy}>
      {following ? "Following" : "Follow"}
    </button>
  );
}
