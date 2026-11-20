import { useState } from "react";
import { Link } from "react-router";
import { Channels, Videos } from "../api/endpoints";
import { VideoThumb } from "../components/cards";
import { ErrorBox, Spinner } from "../components/ui";
import { compact, timeAgo, useQuery } from "../lib/hooks";
import { uploadFile, IMAGE_TYPES } from "../lib/upload";

/** Creator studio: create the channel once, then manage videos (visibility, delete) and branding. */
export default function Studio() {
  const channel = useQuery(() => Channels.mine().catch((e) => (e.status === 404 ? null : Promise.reject(e))), []);
  if (channel.loading) return <Spinner />;
  if (channel.error) return <ErrorBox error={channel.error} onRetry={channel.reload} />;
  if (!channel.data) return <CreateChannel onCreated={channel.setData} />;
  return <Manage channel={channel.data} onChange={channel.setData} />;
}

function CreateChannel({ onCreated }) {
  const [form, setForm] = useState({ handle: "", name: "", description: "" });
  const [error, setError] = useState(null);
  return (
    <form className="card mx-auto grid max-w-md gap-4 p-6" onSubmit={async (e) => {
      e.preventDefault();
      try { onCreated(await Channels.create(form)); } catch (err) { setError(err); }
    }}>
      <h1 className="font-serif text-2xl font-semibold tracking-tight">Create your channel</h1>
      <p className="text-sm text-zinc-500">You need a channel to publish long-form videos. One channel per account.</p>
      <label className="grid gap-1 text-sm font-semibold">Handle
        <input className="input font-normal" required pattern="[A-Za-z0-9_.]{3,30}" placeholder="mychannel" value={form.handle} onChange={(e) => setForm({ ...form, handle: e.target.value })} />
      </label>
      <label className="grid gap-1 text-sm font-semibold">Channel name
        <input className="input font-normal" required maxLength={80} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </label>
      <label className="grid gap-1 text-sm font-semibold">Description
        <textarea className="input font-normal" rows={3} maxLength={1000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </label>
      <ErrorBox error={error} />
      <button className="btn-primary">Create channel</button>
    </form>
  );
}

function Manage({ channel, onChange }) {
  const videos = useQuery(() => Channels.videos(channel.id, 0), [channel.id]);
  const [busy, setBusy] = useState(null);

  async function brand(kind, file) {
    if (!file) return;
    setBusy(kind);
    try {
      const media = await uploadFile(kind === "avatar" ? "AVATAR" : "BANNER", file);
      onChange(await Channels.update(channel.id, kind === "avatar" ? { avatarMediaId: media.id } : { bannerMediaId: media.id }));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-serif text-3xl font-semibold tracking-tight">Channel studio</h1>
          <Link to={`/c/${channel.handle}`} className="text-sm text-violet-brand">@{channel.handle} · {compact(channel.subscriberCount)} subscribers</Link>
        </div>
        <div className="flex flex-wrap gap-2">
          <label className="btn-secondary cursor-pointer">{busy === "avatar" ? "Uploading…" : "Change avatar"}
            <input type="file" accept={IMAGE_TYPES.join(",")} className="sr-only" onChange={(e) => brand("avatar", e.target.files[0])} />
          </label>
          <label className="btn-secondary cursor-pointer">{busy === "banner" ? "Uploading…" : "Change banner"}
            <input type="file" accept={IMAGE_TYPES.join(",")} className="sr-only" onChange={(e) => brand("banner", e.target.files[0])} />
          </label>
          <Link to="/upload?tab=video" className="btn-primary">Upload video</Link>
        </div>
      </div>
      {videos.loading ? <Spinner /> : (
        <div className="card divide-y divide-zinc-200 dark:divide-zinc-800">
          {videos.data?.content?.length === 0 && <p className="p-6 text-center text-sm text-zinc-500">No videos yet.</p>}
          {videos.data?.content?.map((v) => <VideoRow key={v.id} video={v} onChanged={videos.reload} />)}
        </div>
      )}
    </div>
  );
}

function VideoRow({ video, onChanged }) {
  const [vis, setVis] = useState(video.visibility);
  return (
    <div className="flex flex-wrap items-center gap-4 p-3">
      <Link to={`/watch/${video.id}`} className="w-40 shrink-0"><VideoThumb video={video} /></Link>
      <div className="min-w-0 flex-1">
        <Link to={`/watch/${video.id}`} className="font-semibold hover:underline">{video.title}</Link>
        <p className="text-xs text-zinc-500">{video.status} · {compact(video.viewCount)} views · {timeAgo(video.publishedAt)}</p>
      </div>
      <select className="input w-auto" value={vis} aria-label="Visibility" onChange={async (e) => {
        const value = e.target.value;
        setVis(value);
        await Videos.update(video.id, { visibility: value });
      }}>
        <option value="PUBLIC">Public</option>
        <option value="UNLISTED">Unlisted</option>
        <option value="PRIVATE">Private</option>
      </select>
      <button className="btn-ghost text-red-500" onClick={async () => {
        if (window.confirm(`Delete "${video.title}"? This cannot be undone.`)) { await Videos.remove(video.id); onChanged(); }
      }}>Delete</button>
    </div>
  );
}
