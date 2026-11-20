import { useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Channels } from "../api/endpoints";
import { ChannelAvatar } from "../components/cards";
import { VideoGrid } from "../components/grids";
import { ErrorBox, LoadMore, MediaImage, Spinner } from "../components/ui";
import { compact, useCursorList, useInView, useQuery } from "../lib/hooks";

export default function Channel() {
  const { handle } = useParams();
  const { user } = useAuth();
  const channel = useQuery(() => Channels.byHandle(handle), [handle]);
  const c = channel.data;
  const videos = useCursorList(async (cursor) => {
    if (!c) return { items: [], nextCursor: null };
    const page = Number(cursor ?? 0);
    const r = await Channels.videos(c.id, page);
    return { items: r.content, nextCursor: r.hasNext ? String(page + 1) : null };
  }, [c?.id]);
  const sentinel = useInView(() => videos.loadMore());
  const [busy, setBusy] = useState(false);

  if (channel.loading) return <Spinner />;
  if (channel.error) return <ErrorBox error={channel.error.status === 404 ? new Error("Channel not found.") : channel.error} />;
  const mine = user?.userId === c.ownerId;

  return (
    <div className="grid gap-6">
      <div className="aspect-[6/1] overflow-hidden rounded-xl bg-zinc-200 dark:bg-zinc-800">
        {c.bannerMediaId && <MediaImage id={c.bannerMediaId} className="h-full w-full" />}
      </div>
      <div className="flex flex-wrap items-center gap-5">
        <ChannelAvatar channel={c} size={88} />
        <div className="flex-1">
          <h1 className="font-serif text-3xl font-semibold tracking-tight">{c.name}</h1>
          <p className="text-sm text-zinc-500">@{c.handle} · {compact(c.subscriberCount)} subscribers · {c.videoCount} videos</p>
          {c.description && <p className="mt-1 max-w-2xl text-sm text-zinc-600 dark:text-zinc-400">{c.description}</p>}
        </div>
        {mine ? <Link to="/studio" className="btn-secondary">Manage videos</Link> : user && (
          <button disabled={busy} className={c.subscribed ? "btn-secondary" : "btn bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"}
            onClick={async () => { setBusy(true); try { channel.setData(await (c.subscribed ? Channels.unsubscribe(c.id) : Channels.subscribe(c.id))); } finally { setBusy(false); } }}>
            {c.subscribed ? "Subscribed" : "Subscribe"}
          </button>
        )}
      </div>
      <h2 className="border-b border-zinc-200 pb-2 font-semibold dark:border-zinc-800">Videos</h2>
      {(videos.items.length > 0 || videos.done) && <VideoGrid videos={videos.items} empty="This channel has no videos yet" />}
      <LoadMore list={videos} sentinelRef={sentinel} />
    </div>
  );
}
