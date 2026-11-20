import { useState } from "react";
import { Videos as VideosApi } from "../api/endpoints";
import { VideoGrid } from "../components/grids";
import { LoadMore } from "../components/ui";
import { useCursorList, useInView } from "../lib/hooks";

const CATEGORIES = ["MUSIC", "GAMING", "EDUCATION", "ENTERTAINMENT", "SPORTS", "NEWS", "TECH", "TRAVEL", "FOOD", "COMEDY", "LIFESTYLE", "OTHER"];

/** Public video catalogue: category chips + latest/popular sort (offset pages adapted to the cursor hook). */
export default function Videos() {
  const [category, setCategory] = useState(null);
  const [sort, setSort] = useState("latest");
  const list = useCursorList(async (cursor) => {
    const page = Number(cursor ?? 0);
    const r = await VideosApi.list({ category, sort, page, size: 24 });
    return { items: r.content, nextCursor: r.hasNext ? String(page + 1) : null };
  }, [category, sort]);
  const sentinel = useInView(() => list.loadMore());

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Videos</h1>
        <select className="input w-auto" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort videos">
          <option value="latest">Latest</option>
          <option value="popular">Most viewed</option>
        </select>
      </div>
      <div className="flex gap-2 overflow-x-auto pb-1">
        <Chip active={!category} onClick={() => setCategory(null)}>All</Chip>
        {CATEGORIES.map((c) => <Chip key={c} active={category === c} onClick={() => setCategory(c)}>{c[0] + c.slice(1).toLowerCase()}</Chip>)}
      </div>
      {(list.items.length > 0 || list.done) && <VideoGrid videos={list.items} empty="No videos in this category yet" />}
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}

export function Chip({ active, children, onClick }) {
  return (
    <button onClick={onClick} aria-pressed={active}
      className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition ${active ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900" : "bg-zinc-200/70 hover:bg-zinc-200 dark:bg-zinc-800 dark:hover:bg-zinc-700"}`}>
      {children}
    </button>
  );
}
