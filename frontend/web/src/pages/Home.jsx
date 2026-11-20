import { useState } from "react";
import { Link } from "react-router";
import { Feed } from "../api/endpoints";
import { PostCard, VideoCard } from "../components/cards";
import { feedItemToPost, feedItemToVideo } from "../components/grids";
import { StoriesTray } from "../components/Stories";
import { EmptyState, LoadMore, Tabs } from "../components/ui";
import { useCursorList, useInView } from "../lib/hooks";

/** Home: stories tray + "For you" (ranked) or "Following" (chronological) feed. */
export default function Home() {
  const [tab, setTab] = useState("home");
  const list = useCursorList((cursor) => (tab === "home" ? Feed.home(cursor) : Feed.following(cursor)), [tab]);
  const sentinel = useInView(() => list.loadMore());

  return (
    <div className="mx-auto grid max-w-2xl gap-5">
      <StoriesTray />
      <Tabs value={tab} onChange={setTab} tabs={[{ value: "home", label: "For you" }, { value: "following", label: "Following" }]} />
      {!list.loading && list.done && list.items.length === 0 && (
        <EmptyState icon="👋" title="Your feed is empty">
          Follow people and subscribe to channels to fill it. <Link to="/explore" className="text-violet-brand">Explore</Link> to find some.
        </EmptyState>
      )}
      <div className="grid gap-6">
        {list.items.map((item) => (item.type === "VIDEO"
          ? <div key={`V${item.id}`} className="card p-3"><VideoCard video={feedItemToVideo(item)} /></div>
          : <PostCard key={`P${item.id}`} post={feedItemToPost(item)} />))}
      </div>
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}
