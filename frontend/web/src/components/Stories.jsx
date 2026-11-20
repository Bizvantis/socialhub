import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Stories } from "../api/endpoints";
import { timeAgo, useMedia, useQuery } from "../lib/hooks";
import { Avatar, Modal, UserName } from "./ui";

const STORY_MS = 5000;

/** Stories tray: one ring per author, unseen rings highlighted with the gradient. */
export function StoriesTray() {
  const { user } = useAuth();
  const tray = useQuery(() => Stories.tray(), []);
  const [open, setOpen] = useState(null); // index of the ring being watched
  const rings = tray.data ?? [];
  const hasOwn = rings.some((r) => r.authorId === user?.userId);

  return (
    <div className="flex gap-4 overflow-x-auto pb-2" aria-label="Stories">
      {!hasOwn && (
        <Link to="/upload?tab=story" className="flex w-16 shrink-0 flex-col items-center gap-1 text-xs">
          <div className="relative"><Avatar userId={user?.userId} size={60} />
            <span className="absolute -bottom-0.5 -right-0.5 grid h-5 w-5 place-items-center rounded-full border-2 border-white bg-violet-brand text-xs font-bold text-white dark:border-zinc-950">+</span>
          </div>
          <span className="truncate">Your story</span>
        </Link>
      )}
      {rings.map((r, i) => (
        <button key={r.authorId} onClick={() => setOpen(i)} className="flex w-16 shrink-0 flex-col items-center gap-1 text-xs">
          <Avatar userId={r.authorId} size={60} ring={!r.allSeen} className={r.allSeen ? "opacity-70" : ""} />
          <span className="w-full truncate text-center">{r.authorId === user?.userId ? "Your story" : <UserName userId={r.authorId} link={false} className="font-normal" />}</span>
        </button>
      ))}
      {open != null && rings[open] && (
        <StoryViewer rings={rings} index={open} onIndex={setOpen} onClose={() => { setOpen(null); tray.reload(); }} />
      )}
    </div>
  );
}

/** Full-screen viewer: auto-advances every 5 s, tap left/right to navigate, marks stories seen. */
function StoryViewer({ rings, index, onIndex, onClose }) {
  const { user } = useAuth();
  const ring = rings[index];
  const [pos, setPos] = useState(0);
  const story = ring.stories[pos];
  const get = useMedia([story?.mediaId]);
  const media = story ? get(story.mediaId) : null;
  const [viewers, setViewers] = useState(null);
  const mine = ring.authorId === user?.userId;

  const next = useCallback(() => {
    if (pos < ring.stories.length - 1) setPos(pos + 1);
    else if (index < rings.length - 1) { onIndex(index + 1); setPos(0); }
    else onClose();
  }, [pos, ring, index, rings.length, onIndex, onClose]);
  const prev = () => (pos > 0 ? setPos(pos - 1) : index > 0 && (onIndex(index - 1), setPos(0)));

  useEffect(() => {
    if (!story) return undefined;
    if (!mine) Stories.view(story.id).catch(() => {});
    if (story.mediaType === "VIDEO" || viewers) return undefined;
    const t = setTimeout(next, STORY_MS);
    return () => clearTimeout(t);
  }, [story, mine, next, viewers]);

  if (!story) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black" role="dialog" aria-modal="true" aria-label="Story viewer">
      <div className="relative h-full max-h-[90vh] w-full max-w-sm overflow-hidden rounded-xl bg-zinc-900">
        <div className="absolute inset-x-2 top-2 z-10 flex gap-1">
          {ring.stories.map((s, i) => (
            <span key={s.id} className="h-0.5 flex-1 overflow-hidden rounded bg-white/30">
              <span key={`${s.id}-${i === pos}`} className={`block h-full bg-white ${i < pos ? "w-full" : i === pos ? "w-full" : "w-0"}`}
                style={i === pos && story.mediaType !== "VIDEO" ? { animation: `storybar ${STORY_MS}ms linear` } : undefined} />
            </span>
          ))}
        </div>
        <div className="absolute inset-x-3 top-5 z-10 flex items-center gap-2 text-sm text-white">
          <Avatar userId={ring.authorId} size={32} />
          <UserName userId={ring.authorId} className="text-white" />
          <span className="text-white/70">{timeAgo(story.createdAt)}</span>
          <button className="ml-auto text-xl" onClick={onClose} aria-label="Close stories">✕</button>
        </div>
        {story.mediaType === "VIDEO"
          ? <video src={media?.url} poster={media?.thumbnailUrl} autoPlay playsInline onEnded={next} className="h-full w-full object-contain" />
          : media?.url ? <img src={media.url} alt="" className="h-full w-full object-contain" /> : <div className="h-full animate-pulse bg-zinc-800" />}
        {story.caption && <p className="absolute inset-x-0 bottom-14 px-4 text-center text-white drop-shadow">{story.caption}</p>}
        <button className="absolute inset-y-0 left-0 w-1/3" onClick={prev} aria-label="Previous story" />
        <button className="absolute inset-y-0 right-0 w-1/3" onClick={next} aria-label="Next story" />
        {mine && (
          <button className="absolute bottom-3 left-3 rounded-full bg-black/50 px-3 py-1 text-sm text-white"
            onClick={async () => setViewers(await Stories.viewers(story.id))}>
            👁 {story.viewCount ?? 0} viewers
          </button>
        )}
      </div>
      <style>{"@keyframes storybar { from { width: 0 } to { width: 100% } }"}</style>
      <Modal open={!!viewers} onClose={() => setViewers(null)} title="Viewers">
        {viewers?.length ? (
          <ul className="grid gap-3">{viewers.map((v) => (
            <li key={v.viewerId} className="flex items-center gap-3 text-sm"><Avatar userId={v.viewerId} size={32} /><UserName userId={v.viewerId} /><span className="ml-auto text-xs text-zinc-500">{timeAgo(v.viewedAt)}</span></li>
          ))}</ul>
        ) : <p className="text-sm text-zinc-500">No views yet.</p>}
      </Modal>
    </div>
  );
}
