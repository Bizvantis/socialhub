import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { Search } from "../api/endpoints";
import { ChannelAvatar, VideoCard } from "../components/cards";
import { PostGrid } from "../components/grids";
import { Avatar, EmptyState, ErrorBox, Spinner } from "../components/ui";
import { compact, useQuery } from "../lib/hooks";
import { Chip } from "./Videos";

const toVideo = (d) => ({ id: d.videoId, title: d.title, mediaId: d.mediaId, thumbnailMediaId: d.thumbnailMediaId,
  durationSeconds: d.durationSeconds, viewCount: d.viewCount, publishedAt: d.publishedAt, status: "READY",
  channel: { id: d.channelId, handle: d.channelHandle, name: d.channelName } });

export default function SearchPage() {
  const [params] = useSearchParams();
  const q = params.get("q") || "";
  const [filters, setFilters] = useState({ sort: "relevance", uploadedWithin: "", duration: "" });
  const filtered = filters.sort !== "relevance" || filters.uploadedWithin || filters.duration;

  const all = useQuery(() => (q ? Search.all(q) : Promise.resolve(null)), [q]);
  const videos = useQuery(() => (q && filtered ? Search.videos({
    q, sort: filters.sort, uploadedWithin: filters.uploadedWithin || undefined,
    maxDuration: filters.duration === "short" ? 240 : undefined, minDuration: filters.duration === "long" ? 1200 : undefined,
  }) : Promise.resolve(null)), [q, filters.sort, filters.uploadedWithin, filters.duration]);

  if (!q) return <EmptyState icon="search" title="Search SocialHub">Find videos, channels, people and #hashtags.</EmptyState>;
  if (all.loading) return <Spinner />;
  if (all.error) return <ErrorBox error={all.error} onRetry={all.reload} />;
  const r = all.data;
  const videoHits = (filtered ? videos.data?.items : r.videos.items) ?? [];
  const nothing = !videoHits.length && !r.channels.items.length && !r.users.items.length && !r.posts.items.length;

  return (
    <div className="grid gap-8">
      <h1 className="text-xl">Results for <b>{q}</b></h1>
      <div className="flex flex-wrap gap-2" aria-label="Video filters">
        {[["relevance", "Relevance"], ["date", "Upload date"], ["views", "View count"]].map(([v, l]) => (
          <Chip key={v} active={filters.sort === v} onClick={() => setFilters({ ...filters, sort: v })}>{l}</Chip>))}
        <select className="input w-auto py-1.5" value={filters.uploadedWithin} onChange={(e) => setFilters({ ...filters, uploadedWithin: e.target.value })} aria-label="Upload date">
          <option value="">Any time</option><option value="day">Today</option><option value="week">This week</option><option value="month">This month</option><option value="year">This year</option>
        </select>
        <select className="input w-auto py-1.5" value={filters.duration} onChange={(e) => setFilters({ ...filters, duration: e.target.value })} aria-label="Duration">
          <option value="">Any length</option><option value="short">Under 4 minutes</option><option value="long">Over 20 minutes</option>
        </select>
      </div>
      {nothing && <EmptyState icon="search" title="No results">Try different keywords. Search tolerates small typos.</EmptyState>}
      {r.channels.items.length > 0 && (
        <Section title="Channels">
          {r.channels.items.map(({ doc }) => (
            <Link key={doc.channelId} to={`/c/${doc.handle}`} className="flex items-center gap-4 rounded-xl p-2 hover:bg-zinc-100 dark:hover:bg-zinc-900">
              <ChannelAvatar channel={doc} size={64} />
              <div><p className="font-semibold">{doc.name}</p><p className="text-sm text-zinc-500">@{doc.handle} · {compact(doc.subscriberCount)} subscribers</p></div>
            </Link>
          ))}
        </Section>
      )}
      {videoHits.length > 0 && (
        <Section title="Videos">
          <div className="grid gap-4">{videoHits.map(({ doc, highlight }) => (
            <div key={doc.videoId}>
              <VideoCard video={toVideo(doc)} compactLayout />
              {highlight?.description && <p className="ml-44 mt-1 text-xs text-zinc-500" dangerouslySetInnerHTML={{ __html: sanitize(highlight.description[0]) }} />}
            </div>
          ))}</div>
        </Section>
      )}
      {r.users.items.length > 0 && (
        <Section title="People">
          <div className="flex flex-wrap gap-4">{r.users.items.map(({ doc }) => (
            <Link key={doc.userId} to={`/u/${doc.username}`} className="flex items-center gap-3 rounded-xl p-2 hover:bg-zinc-100 dark:hover:bg-zinc-900">
              <Avatar userId={doc.userId} size={44} />
              <div className="text-sm"><p className="font-semibold">{doc.username}</p><p className="text-zinc-500">{doc.displayName} · {compact(doc.followerCount)} followers</p></div>
            </Link>
          ))}</div>
        </Section>
      )}
      {r.posts.items.length > 0 && (
        <Section title="Posts"><PostGrid posts={r.posts.items.map(({ doc }) => ({ id: doc.postId, type: doc.type, mediaIds: doc.mediaIds }))} /></Section>
      )}
    </div>
  );
}

/** Highlights come from Elasticsearch with <em> tags around matches; allow only those. */
function sanitize(html) {
  return html.replace(/</g, "&lt;").replace(/&lt;em>/g, "<em class='not-italic font-semibold text-zinc-800 dark:text-zinc-200'>").replace(/&lt;\/em>/g, "</em>");
}

function Section({ title, children }) {
  return <section className="grid gap-3"><h2 className="font-semibold text-zinc-500">{title}</h2>{children}</section>;
}
