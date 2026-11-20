import { useState } from "react";
import { Link } from "react-router";
import { Engagement, Library as LibraryApi, Posts, Videos } from "../api/endpoints";
import { VideoCard } from "../components/cards";
import { PostGrid } from "../components/grids";
import { EmptyState, ErrorBox, Spinner, Tabs } from "../components/ui";
import { timeAgo, useQuery } from "../lib/hooks";

export default function Library() {
  const [tab, setTab] = useState("history");
  return (
    <div className="grid gap-5">
      <h1 className="text-2xl font-bold">Library</h1>
      <Tabs value={tab} onChange={setTab} tabs={[
        { value: "history", label: "History" }, { value: "later", label: "Watch later" },
        { value: "playlists", label: "Playlists" }, { value: "saved", label: "Saved" },
      ]} />
      {tab === "history" && <History />}
      {tab === "later" && <WatchLater />}
      {tab === "playlists" && <Playlists />}
      {tab === "saved" && <Saved />}
    </div>
  );
}

function History() {
  const h = useQuery(() => LibraryApi.history(), []);
  if (h.loading) return <Spinner />;
  if (h.error) return <ErrorBox error={h.error} onRetry={h.reload} />;
  if (!h.data.length) return <EmptyState icon="🕘" title="No watch history">Videos you watch will appear here.</EmptyState>;
  return (
    <div className="grid gap-4">
      <button className="btn-secondary justify-self-end" onClick={async () => { if (window.confirm("Clear all watch history?")) { await LibraryApi.clearHistory(); h.reload(); } }}>Clear history</button>
      {h.data.map((i) => (
        <div key={i.video.id} className="grid gap-1">
          <VideoCard video={i.video} compactLayout />
          <p className="ml-44 text-xs text-zinc-500">Watched {timeAgo(i.watchedAt)} ago{i.progressSeconds > 0 && ` · stopped at ${Math.floor(i.progressSeconds / 60)}:${String(i.progressSeconds % 60).padStart(2, "0")}`}</p>
        </div>
      ))}
    </div>
  );
}

function WatchLater() {
  const w = useQuery(() => LibraryApi.watchLater(), []);
  if (w.loading) return <Spinner />;
  if (!w.data?.length) return <EmptyState icon="⏱" title="Nothing saved for later" />;
  return <div className="grid gap-4">{w.data.map((i) => (
    <div key={i.video.id} className="flex items-center gap-3">
      <div className="flex-1"><VideoCard video={i.video} compactLayout /></div>
      <button className="btn-ghost" onClick={async () => { await LibraryApi.removeWatchLater(i.video.id); w.reload(); }}>Remove</button>
    </div>
  ))}</div>;
}

function Playlists() {
  const p = useQuery(() => LibraryApi.myPlaylists(), []);
  const [title, setTitle] = useState("");
  if (p.loading) return <Spinner />;
  return (
    <div className="grid gap-4">
      <form className="flex gap-2" onSubmit={async (e) => {
        e.preventDefault();
        if (!title.trim()) return;
        await LibraryApi.createPlaylist({ title: title.trim(), visibility: "PUBLIC" });
        setTitle("");
        p.reload();
      }}>
        <input className="input" placeholder="New playlist" value={title} onChange={(e) => setTitle(e.target.value)} />
        <button className="btn-primary">Create</button>
      </form>
      {p.data?.content?.length === 0 && <EmptyState icon="🎵" title="No playlists yet" />}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {p.data?.content?.map((pl) => (
          <Link key={pl.id} to={`/playlist/${pl.id}`} className="card p-4 hover:shadow-md">
            <p className="font-semibold">{pl.title}</p>
            <p className="text-sm text-zinc-500">{pl.videoCount} videos · {pl.visibility.toLowerCase()}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}

/** Bookmarks store (type, id); hydrate each target from its own service. */
function Saved() {
  const saved = useQuery(async () => {
    const page = await Engagement.bookmarks();
    const posts = await Promise.all(page.items.filter((b) => b.targetType === "POST").map((b) => Posts.get(b.targetId).catch(() => null)));
    const videos = await Promise.all(page.items.filter((b) => b.targetType === "VIDEO").map((b) => Videos.get(b.targetId).catch(() => null)));
    return { posts: posts.filter(Boolean), videos: videos.filter(Boolean) };
  }, []);
  if (saved.loading) return <Spinner />;
  if (!saved.data.posts.length && !saved.data.videos.length) return <EmptyState icon="🔖" title="Nothing saved">Tap Save on posts and videos to keep them here.</EmptyState>;
  return (
    <div className="grid gap-6">
      {saved.data.videos.length > 0 && <div className="grid gap-4">{saved.data.videos.map((v) => <VideoCard key={v.id} video={v} compactLayout />)}</div>}
      {saved.data.posts.length > 0 && <PostGrid posts={saved.data.posts} />}
    </div>
  );
}
