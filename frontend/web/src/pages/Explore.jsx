import { useState } from "react";
import { Link } from "react-router";
import { Posts } from "../api/endpoints";
import { PostGrid } from "../components/grids";
import { LoadMore, Tabs } from "../components/ui";
import { useCursorList, useInView, useQuery } from "../lib/hooks";

export default function Explore() {
  const [tab, setTab] = useState("all");
  const list = useCursorList((cursor) => Posts.explore(tab === "reels" ? "REEL" : undefined, cursor), [tab]);
  const sentinel = useInView(() => list.loadMore());
  const tags = useQuery(() => Posts.trendingHashtags(), []);

  return (
    <div className="grid gap-5">
      <h1 className="font-serif text-3xl font-semibold tracking-tight">Explore</h1>
      {tags.data?.length > 0 && (
        <div className="flex flex-wrap gap-2" aria-label="Trending hashtags">
          {tags.data.map((t) => (
            <Link key={t.tag} to={`/tags/${t.tag}`} className="btn-secondary">#{t.tag} <span className="text-xs text-zinc-500">{t.postCount}</span></Link>
          ))}
        </div>
      )}
      <Tabs value={tab} onChange={setTab} tabs={[{ value: "all", label: "Posts" }, { value: "reels", label: "Reels" }]} />
      {(list.items.length > 0 || list.done) && <PostGrid posts={list.items} empty="Nothing here yet" />}
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}
