import { useState } from "react";
import { Link } from "react-router";
import { Feed, Posts, Videos } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { PostCard, VideoCard, VideoThumb } from "../components/cards";
import { feedItemToPost, feedItemToVideo } from "../components/grids";
import { StoriesTray } from "../components/Stories";
import { Avatar, EmptyState, LoadMore, Tabs } from "../components/ui";
import { compact, useCursorList, useInView, useQuery } from "../lib/hooks";

/** Home: stories tray + "For you" (ranked) or "Following" (chronological) feed, with a side rail. */
export default function Home() {
  const [tab, setTab] = useState("home");
  const list = useCursorList((cursor) => (tab === "home" ? Feed.home(cursor) : Feed.following(cursor)), [tab]);
  const sentinel = useInView(() => list.loadMore());

  return (
    <div className="mx-auto flex max-w-[64rem] justify-center gap-10">
      <div className="grid w-full max-w-[38rem] content-start gap-5">
        <StoriesTray />
        <Tabs value={tab} onChange={setTab} tabs={[{ value: "home", label: "For you" }, { value: "following", label: "Following" }]} />
        {!list.loading && list.done && list.items.length === 0 && (
          <EmptyState icon="sparkle" title="Your feed is empty">
            Follow people and subscribe to channels to fill it. <Link to="/explore" className="text-accent">Explore</Link> to find some.
          </EmptyState>
        )}
        <div className="grid gap-6">
          {list.items.map((item) => (item.type === "VIDEO"
            ? <div key={`V${item.id}`} className="card p-3"><VideoCard video={feedItemToVideo(item)} /></div>
            : <PostCard key={`P${item.id}`} post={feedItemToPost(item)} />))}
        </div>
        <LoadMore list={list} sentinelRef={sentinel} />
      </div>
      <SideRail />
    </div>
  );
}

/** Right-hand column on wide screens: who you are, trending hashtags, popular videos. */
function SideRail() {
  const { user } = useAuth();
  const tags = useQuery(() => Posts.trendingHashtags(), []);
  const popular = useQuery(() => Videos.list({ sort: "popular", page: 0, size: 4 }), []);
  return (
    <aside className="sticky top-20 hidden h-fit w-72 shrink-0 gap-6 xl:grid" aria-label="Highlights">
      {user && (
        <Link to={`/u/${user.username}`} className="flex items-center gap-3">
          <Avatar userId={user.userId} size={44} />
          <div className="min-w-0 text-sm">
            <div className="truncate font-semibold">{user.username}</div>
            <div className="truncate text-zinc-500">{user.displayName || "View your profile"}</div>
          </div>
        </Link>
      )}

      {tags.data?.length > 0 && (
        <section>
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-zinc-500">Trending</h2>
          <ul className="grid gap-0.5">
            {tags.data.slice(0, 6).map((t) => (
              <li key={t.tag}>
                <Link to={`/tags/${t.tag}`} className="flex items-baseline justify-between rounded-lg px-2 py-1.5 text-sm hover:bg-zinc-100 dark:hover:bg-zinc-900">
                  <span className="font-medium">#{t.tag}</span>
                  <span className="text-xs text-zinc-500">{compact(t.postCount)} posts</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {popular.data?.content?.length > 0 && (
        <section>
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-zinc-500">Popular videos</h2>
          <ul className="grid gap-3">
            {popular.data.content.map((v) => (
              <li key={v.id}>
                <Link to={`/watch/${v.id}`} className="group flex gap-3">
                  <VideoThumb video={v} className="w-28 shrink-0 rounded-lg" />
                  <div className="min-w-0 text-sm">
                    <p className="line-clamp-2 font-medium leading-snug group-hover:underline">{v.title}</p>
                    <p className="mt-0.5 text-xs text-zinc-500">{compact(v.viewCount)} views</p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="text-xs leading-relaxed text-zinc-400">
        SocialHub · a portfolio project: Spring Boot microservices, Kafka, RabbitMQ, Elasticsearch, Redis and React.
      </p>
    </aside>
  );
}
