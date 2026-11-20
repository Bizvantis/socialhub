import { useCallback, useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Channels, Library, Moderation, Videos } from "../api/endpoints";
import { ActionBar, ChannelAvatar, RichText, VideoCard } from "../components/cards";
import Comments from "../components/Comments";
import HlsPlayer from "../components/HlsPlayer";
import { ErrorBox, Modal, Spinner } from "../components/ui";
import { compact, timeAgo, useMedia, useQuery } from "../lib/hooks";
import { Icon } from "../components/icons";

export default function Watch() {
  const { id } = useParams();
  const videoId = Number(id);
  const { user } = useAuth();
  const video = useQuery(() => Videos.get(videoId), [videoId]);
  const related = useQuery(() => Videos.related(videoId), [videoId]);
  const v = video.data;
  const get = useMedia([v?.mediaId]);
  const media = v ? get(v.mediaId) : null;
  const [showMore, setShowMore] = useState(false);

  // First report counts the view (deduplicated server-side); later ones save the resume position.
  const onProgress = useCallback((seconds) => { Videos.view(videoId, seconds).catch(() => {}); }, [videoId]);

  if (video.loading) return <Spinner />;
  if (video.error) return <ErrorBox error={video.error.status === 404 ? new Error("This video is unavailable.") : video.error} />;

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
      <div className="grid min-w-0 gap-4">
        {v.status !== "READY" ? (
          <div className="grid aspect-video place-items-center rounded-2xl bg-zinc-900 text-zinc-300">
            {v.status === "PROCESSING" ? "This video is still being processed. Check back in a minute." : "Processing failed for this video."}
          </div>
        ) : media?.playbackUrl ? (
          <HlsPlayer src={media.playbackUrl} poster={media.thumbnailUrl} startAt={v.resumeAtSeconds ?? 0} onProgress={onProgress} />
        ) : <div className="aspect-video animate-pulse rounded-2xl bg-zinc-800" />}

        <h1 className="text-xl font-semibold leading-snug">{v.title}</h1>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <ChannelRow channel={v.channel} />
          <ActionBar type="VIDEO" id={v.id} onComment={() => document.getElementById("comments")?.scrollIntoView({ behavior: "smooth" })} />
        </div>
        {user && <ExtraActions video={v} />}

        <div className="card bg-zinc-100 p-4 text-sm dark:bg-zinc-900">
          <p className="font-semibold">{compact(v.viewCount)} views · {timeAgo(v.publishedAt)} ago
            {v.visibility !== "PUBLIC" && <span className="ml-2 rounded bg-zinc-200 px-1.5 text-xs dark:bg-zinc-800">{v.visibility.toLowerCase()}</span>}
          </p>
          <div className={showMore ? "" : "line-clamp-3"}><RichText text={v.description} /></div>
          {v.tags?.length > 0 && <p className="mt-2 flex flex-wrap gap-2">{v.tags.map((t) => <span key={t} className="text-sm font-medium text-accent">#{t}</span>)}</p>}
          {v.description?.length > 200 && <button className="mt-1 font-semibold" onClick={() => setShowMore(!showMore)}>{showMore ? "Show less" : "…more"}</button>}
        </div>

        <div id="comments"><Comments type="VIDEO" id={v.id} ownerId={v.channel.ownerId} /></div>
      </div>

      <aside className="grid content-start gap-3" aria-label="Related videos">
        <h2 className="font-semibold">Up next</h2>
        {related.data?.map((r) => <VideoCard key={r.id} video={r} compactLayout />)}
        {related.data?.length === 0 && <p className="text-sm text-zinc-500">No related videos yet.</p>}
      </aside>
    </div>
  );
}

function ChannelRow({ channel }) {
  const { user } = useAuth();
  const c = useQuery(() => Channels.get(channel.id), [channel.id]);
  const [busy, setBusy] = useState(false);
  const data = c.data ?? { ...channel, subscribed: false };
  const mine = user?.userId === channel.ownerId;

  async function toggle() {
    setBusy(true);
    try {
      c.setData(await (data.subscribed ? Channels.unsubscribe(channel.id) : Channels.subscribe(channel.id)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex items-center gap-3">
      <Link to={`/c/${channel.handle}`}><ChannelAvatar channel={channel} size={42} /></Link>
      <div>
        <Link to={`/c/${channel.handle}`} className="font-semibold hover:underline">{channel.name}</Link>
        <p className="text-xs text-zinc-500">{compact(data.subscriberCount)} subscribers</p>
      </div>
      {user && !mine && (
        <button className={data.subscribed ? "btn-secondary ml-2" : "btn ml-2 bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"} onClick={toggle} disabled={busy}>
          {data.subscribed ? "Subscribed" : "Subscribe"}
        </button>
      )}
      {mine && <Link to="/studio" className="btn-secondary ml-2">Manage</Link>}
    </div>
  );
}

function ExtraActions({ video }) {
  const [later, setLater] = useState(!!video.inWatchLater);
  const [playlistOpen, setPlaylistOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  return (
    <div className="flex flex-wrap gap-2">
      <button className="btn-secondary" aria-pressed={later} onClick={async () => {
        await (later ? Library.removeWatchLater(video.id) : Library.addWatchLater(video.id));
        setLater(!later);
      }}><Icon name={later ? "check" : "clock"} size={17} />Watch later</button>
      <button className="btn-secondary" onClick={() => setPlaylistOpen(true)}><Icon name="plus" size={17} />Save to playlist</button>
      <button className="btn-ghost" onClick={() => setReportOpen(true)}><Icon name="flag" size={17} />Report</button>
      <SaveToPlaylist open={playlistOpen} onClose={() => setPlaylistOpen(false)} videoId={video.id} />
      <ReportDialog open={reportOpen} onClose={() => setReportOpen(false)} targetType="VIDEO" targetId={video.id} />
    </div>
  );
}

function SaveToPlaylist({ open, onClose, videoId }) {
  const lists = useQuery(() => (open ? Library.myPlaylists() : Promise.resolve(null)), [open]);
  const [title, setTitle] = useState("");
  const [saved, setSaved] = useState({});
  return (
    <Modal open={open} onClose={onClose} title="Save to playlist">
      <ul className="grid gap-2">
        {lists.data?.content?.map((p) => (
          <li key={p.id} className="flex items-center justify-between text-sm">
            <span>{p.title} <span className="text-zinc-500">({p.videoCount})</span></span>
            <button className="btn-secondary" disabled={saved[p.id]} onClick={async () => { await Library.addToPlaylist(p.id, videoId); setSaved({ ...saved, [p.id]: true }); }}>
              {saved[p.id] ? "Saved" : "Add"}
            </button>
          </li>
        ))}
      </ul>
      <form className="mt-4 flex gap-2" onSubmit={async (e) => {
        e.preventDefault();
        if (!title.trim()) return;
        const p = await Library.createPlaylist({ title: title.trim(), visibility: "PRIVATE" });
        await Library.addToPlaylist(p.id, videoId);
        setTitle("");
        lists.reload();
      }}>
        <input className="input" placeholder="New playlist name" value={title} onChange={(e) => setTitle(e.target.value)} />
        <button className="btn-primary">Create</button>
      </form>
    </Modal>
  );
}

const REASONS = ["SPAM", "HARASSMENT", "HATE", "NUDITY", "VIOLENCE", "MISINFORMATION", "COPYRIGHT", "OTHER"];

export function ReportDialog({ open, onClose, targetType, targetId }) {
  const [reason, setReason] = useState("SPAM");
  const [details, setDetails] = useState("");
  const [state, setState] = useState(null);
  return (
    <Modal open={open} onClose={() => { onClose(); setState(null); }} title="Report">
      {state === "done" ? <p className="text-sm">Thanks. Our moderators will review it.</p> : (
        <form className="grid gap-3" onSubmit={async (e) => {
          e.preventDefault();
          try {
            await Moderation.report(targetType, targetId, reason, details || undefined);
            setState("done");
          } catch (err) {
            setState(err);
          }
        }}>
          <fieldset className="grid gap-1.5 text-sm">
            {REASONS.map((r) => (
              <label key={r} className="flex items-center gap-2">
                <input type="radio" name="reason" value={r} checked={reason === r} onChange={() => setReason(r)} />
                {r[0] + r.slice(1).toLowerCase()}
              </label>
            ))}
          </fieldset>
          <textarea className="input" rows={3} maxLength={1000} placeholder="Details (optional)" value={details} onChange={(e) => setDetails(e.target.value)} />
          {state instanceof Error && <ErrorBox error={state} />}
          <button className="btn-primary">Submit report</button>
        </form>
      )}
    </Modal>
  );
}
