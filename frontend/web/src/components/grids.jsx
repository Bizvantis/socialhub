import { Link } from "react-router";
import { useMedia } from "../lib/hooks";
import { VideoCard } from "./cards";
import { EmptyState, MediaImage } from "./ui";
import { Icon } from "./icons";

/** Square thumbnail grid (profile, explore, hashtag pages). */
export function PostGrid({ posts, empty = "No posts yet" }) {
  if (!posts.length) return <EmptyState icon="image" title={empty} />;
  return (
    <div className="grid grid-cols-3 gap-1 sm:gap-2">
      {posts.map((p) => <PostTile key={p.id} post={p} />)}
    </div>
  );
}

function PostTile({ post }) {
  const get = useMedia([post.mediaIds[0]]);
  const m = get(post.mediaIds[0]);
  return (
    <Link to={`/p/${post.id}`} className="group relative block aspect-square overflow-hidden bg-zinc-200 dark:bg-zinc-800">
      {post.type === "REEL"
        ? (m?.thumbnailUrl ? <img src={m.thumbnailUrl} alt="" loading="lazy" className="h-full w-full object-cover" /> : <div className="grid h-full place-items-center text-xs text-zinc-500">Processing…</div>)
        : <MediaImage id={post.mediaIds[0]} className="h-full w-full transition group-hover:scale-105" />}
      <span className="absolute right-1.5 top-1.5 text-sm text-white drop-shadow" aria-hidden="true">
        {post.type === "REEL" ? <Icon name="film" size={18} /> : post.type === "CAROUSEL" ? <Icon name="layers" size={18} /> : null}
      </span>
    </Link>
  );
}

export function VideoGrid({ videos, empty = "No videos yet" }) {
  if (!videos.length) return <EmptyState icon="film" title={empty} />;
  return (
    <div className="grid gap-x-4 gap-y-8 sm:grid-cols-2 xl:grid-cols-3">
      {videos.map((v) => <VideoCard key={v.id} video={v} />)}
    </div>
  );
}

/** Feed snapshots (feed-service) adapted to the card props used everywhere else. */
export function feedItemToPost(i) {
  return { id: i.id, authorId: i.authorId, type: i.postType, caption: i.caption, mediaIds: i.mediaIds || [],
    visibility: i.visibility, status: "READY", createdAt: i.createdAt };
}

export function feedItemToVideo(i) {
  return { id: i.id, title: i.title, mediaId: i.mediaId, thumbnailMediaId: i.thumbnailMediaId, durationSeconds: i.durationSeconds,
    viewCount: i.viewCount ?? 0, publishedAt: i.createdAt, status: "READY",
    channel: { id: i.channelId, handle: i.channelHandle, name: i.channelName } };
}
