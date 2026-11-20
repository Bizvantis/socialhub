import { Link } from "react-router";
import { Channels, Feed } from "../api/endpoints";
import { ChannelAvatar } from "../components/cards";
import { VideoGrid, feedItemToVideo } from "../components/grids";
import { EmptyState, LoadMore } from "../components/ui";
import { useCursorList, useInView, useQuery } from "../lib/hooks";

/** Newest videos from subscribed channels (feed-service) plus the channel strip. */
export default function Subscriptions() {
  const channels = useQuery(() => Channels.subscriptions(), []);
  const list = useCursorList((cursor) => Feed.subscriptions(cursor), []);
  const sentinel = useInView(() => list.loadMore());
  const subs = channels.data?.content ?? [];

  return (
    <div className="grid gap-6">
      <h1 className="font-serif text-3xl font-semibold tracking-tight">Subscriptions</h1>
      {subs.length > 0 && (
        <div className="flex gap-5 overflow-x-auto pb-2">
          {subs.map((c) => (
            <Link key={c.id} to={`/c/${c.handle}`} className="flex w-20 shrink-0 flex-col items-center gap-1 text-center text-xs">
              <ChannelAvatar channel={c} size={56} /><span className="w-full truncate">{c.name}</span>
            </Link>
          ))}
        </div>
      )}
      {list.done && list.items.length === 0
        ? <EmptyState icon="subscriptions" title="No videos from your subscriptions">Subscribe to channels and their new videos will land here.</EmptyState>
        : <VideoGrid videos={list.items.map(feedItemToVideo)} />}
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}
