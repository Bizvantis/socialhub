import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Channels, Posts, Stories, Videos } from "../api/endpoints";
import { ErrorBox, Tabs } from "../components/ui";
import { useQuery } from "../lib/hooks";
import { IMAGE_TYPES, VIDEO_TYPES, uploadFile } from "../lib/upload";

const CATEGORIES = ["EDUCATION", "TECH", "MUSIC", "GAMING", "ENTERTAINMENT", "SPORTS", "NEWS", "TRAVEL", "FOOD", "COMEDY", "LIFESTYLE", "OTHER"];

export default function Upload() {
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") || "post";
  return (
    <div className="mx-auto grid max-w-2xl gap-5">
      <h1 className="text-2xl font-bold">Create</h1>
      <Tabs value={tab} onChange={(t) => setParams({ tab: t })} tabs={[
        { value: "post", label: "Post" }, { value: "reel", label: "Reel" }, { value: "story", label: "Story" }, { value: "video", label: "Video" },
      ]} />
      {tab === "post" && <PostForm />}
      {tab === "reel" && <ReelForm />}
      {tab === "story" && <StoryForm />}
      {tab === "video" && <VideoForm />}
    </div>
  );
}

/** File picker with drag & drop, preview and client-side type/size checks. */
function FilePicker({ accept, multiple = false, maxMb, files, onChange, label }) {
  const [error, setError] = useState(null);
  function pick(list) {
    const arr = [...list];
    const bad = arr.find((f) => !accept.includes(f.type) || f.size > maxMb * 1024 * 1024);
    if (bad) { setError(new Error(`${bad.name}: use ${accept.map((t) => t.split("/")[1]).join(", ")} up to ${maxMb} MB`)); return; }
    setError(null);
    onChange(multiple ? [...files, ...arr].slice(0, 10) : arr.slice(0, 1));
  }
  return (
    <div className="grid gap-3">
      <label onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); pick(e.dataTransfer.files); }}
        className="grid cursor-pointer place-items-center gap-2 rounded-2xl border-2 border-dashed border-zinc-300 p-8 text-center text-sm text-zinc-500 hover:border-violet-brand dark:border-zinc-700">
        <span className="text-3xl" aria-hidden="true">⬆️</span>
        <span>{label} - drag & drop or click</span>
        <input type="file" className="sr-only" accept={accept.join(",")} multiple={multiple} onChange={(e) => pick(e.target.files)} />
      </label>
      {files.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {files.map((f, i) => (
            <div key={i} className="relative h-24 w-24 overflow-hidden rounded-xl bg-zinc-200 dark:bg-zinc-800">
              {f.type.startsWith("image/") ? <img src={URL.createObjectURL(f)} alt="" className="h-full w-full object-cover" />
                : <div className="grid h-full place-items-center p-1 text-center text-xs">{f.name}</div>}
              <button type="button" onClick={() => onChange(files.filter((_, j) => j !== i))} aria-label={`Remove ${f.name}`}
                className="absolute right-1 top-1 rounded-full bg-black/60 px-1.5 text-xs text-white">✕</button>
            </div>
          ))}
        </div>
      )}
      <ErrorBox error={error} />
    </div>
  );
}

function Progress({ value, label }) {
  return (
    <div className="grid gap-1 text-xs text-zinc-500" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <span>{label} {value}%</span>
      <div className="h-2 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800">
        <div className="h-full bg-linear-to-r from-violet-brand to-brand-500 transition-all" style={{ width: `${value}%` }} />
      </div>
    </div>
  );
}

/** Shared submit flow: upload every file to S3 (with progress), then create the content. */
function useSubmit() {
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState(null);
  async function run(files, purpose, create) {
    setError(null);
    try {
      const ids = [];
      for (let i = 0; i < files.length; i++) {
        const m = await uploadFile(purpose, files[i], (p) => setProgress({ value: p, label: `Uploading ${i + 1}/${files.length}` }));
        ids.push(m.id);
      }
      setProgress({ value: 100, label: "Publishing" });
      return await create(ids);
    } catch (e) {
      setError(e);
      return null;
    } finally {
      setProgress(null);
    }
  }
  return { progress, error, run };
}

function PostForm() {
  const navigate = useNavigate();
  const [files, setFiles] = useState([]);
  const [caption, setCaption] = useState("");
  const [visibility, setVisibility] = useState("PUBLIC");
  const s = useSubmit();
  return (
    <form className="card grid gap-4 p-5" onSubmit={async (e) => {
      e.preventDefault();
      if (!files.length) return;
      const post = await s.run(files, "POST_IMAGE", (ids) => Posts.create({
        type: ids.length > 1 ? "CAROUSEL" : "IMAGE", mediaIds: ids, caption: caption || undefined, visibility,
      }));
      if (post) navigate(`/p/${post.id}`);
    }}>
      <FilePicker accept={IMAGE_TYPES} multiple maxMb={15} files={files} onChange={setFiles} label="Up to 10 photos" />
      <textarea className="input" rows={3} maxLength={2200} placeholder="Write a caption… use #hashtags and @mentions" value={caption} onChange={(e) => setCaption(e.target.value)} />
      <select className="input" value={visibility} onChange={(e) => setVisibility(e.target.value)} aria-label="Who can see this">
        <option value="PUBLIC">Everyone</option>
        <option value="FOLLOWERS">Followers only</option>
      </select>
      {s.progress && <Progress {...s.progress} />}
      <ErrorBox error={s.error} />
      <button className="btn-primary" disabled={!files.length || !!s.progress}>Share {files.length > 1 ? "carousel" : "post"}</button>
    </form>
  );
}

function ReelForm() {
  const navigate = useNavigate();
  const [files, setFiles] = useState([]);
  const [caption, setCaption] = useState("");
  const s = useSubmit();
  return (
    <form className="card grid gap-4 p-5" onSubmit={async (e) => {
      e.preventDefault();
      const post = await s.run(files, "REEL", (ids) => Posts.create({ type: "REEL", mediaIds: ids, caption: caption || undefined }));
      if (post) navigate(`/p/${post.id}`);
    }}>
      <FilePicker accept={VIDEO_TYPES} maxMb={200} files={files} onChange={setFiles} label="A vertical video (up to 200 MB)" />
      <textarea className="input" rows={2} maxLength={2200} placeholder="Caption" value={caption} onChange={(e) => setCaption(e.target.value)} />
      <p className="text-xs text-zinc-500">Reels are transcoded for streaming; they appear once processing finishes (usually under a minute).</p>
      {s.progress && <Progress {...s.progress} />}
      <ErrorBox error={s.error} />
      <button className="btn-primary" disabled={!files.length || !!s.progress}>Share reel</button>
    </form>
  );
}

function StoryForm() {
  const navigate = useNavigate();
  const [files, setFiles] = useState([]);
  const [caption, setCaption] = useState("");
  const s = useSubmit();
  const isVideo = files[0]?.type.startsWith("video/");
  return (
    <form className="card grid gap-4 p-5" onSubmit={async (e) => {
      e.preventDefault();
      const story = await s.run(files, isVideo ? "STORY_VIDEO" : "STORY_IMAGE", ([id]) => Stories.create({ mediaId: id, caption: caption || undefined }));
      if (story) navigate("/");
    }}>
      <FilePicker accept={[...IMAGE_TYPES, ...VIDEO_TYPES]} maxMb={100} files={files} onChange={setFiles} label="A photo or short video" />
      <input className="input" maxLength={500} placeholder="Add text (optional)" value={caption} onChange={(e) => setCaption(e.target.value)} />
      <p className="text-xs text-zinc-500">Stories are visible to your followers for 24 hours.</p>
      {s.progress && <Progress {...s.progress} />}
      <ErrorBox error={s.error} />
      <button className="btn-primary" disabled={!files.length || !!s.progress}>Share to story</button>
    </form>
  );
}

function VideoForm() {
  const navigate = useNavigate();
  const channel = useQuery(() => Channels.mine().catch((e) => (e.status === 404 ? null : Promise.reject(e))), []);
  const [files, setFiles] = useState([]);
  const [form, setForm] = useState({ title: "", description: "", tags: "", category: "EDUCATION", visibility: "PUBLIC" });
  const s = useSubmit();

  if (channel.data === null) {
    return <div className="card p-6 text-center text-sm">You need a channel to upload videos. <Link to="/studio" className="text-violet-brand">Create one</Link>.</div>;
  }
  return (
    <form className="card grid gap-4 p-5" onSubmit={async (e) => {
      e.preventDefault();
      const video = await s.run(files, "VIDEO", ([id]) => Videos.create({
        mediaId: id, title: form.title.trim(), description: form.description || undefined, category: form.category, visibility: form.visibility,
        tags: form.tags.split(/[,\s]+/).map((t) => t.replace(/^#/, "")).filter(Boolean).slice(0, 15),
      }));
      if (video) navigate(`/watch/${video.id}`);
    }}>
      <FilePicker accept={VIDEO_TYPES} maxMb={2048} files={files} onChange={setFiles} label="Video file (MP4, MOV or WebM, up to 2 GB)" />
      <input className="input" required maxLength={150} placeholder="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
      <textarea className="input" rows={4} maxLength={5000} placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      <input className="input" placeholder="Tags, separated by commas" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} />
      <div className="grid gap-3 sm:grid-cols-2">
        <select className="input" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} aria-label="Category">
          {CATEGORIES.map((c) => <option key={c} value={c}>{c[0] + c.slice(1).toLowerCase()}</option>)}
        </select>
        <select className="input" value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value })} aria-label="Visibility">
          <option value="PUBLIC">Public</option>
          <option value="UNLISTED">Unlisted (link only)</option>
          <option value="PRIVATE">Private</option>
        </select>
      </div>
      {s.progress && <Progress {...s.progress} />}
      <ErrorBox error={s.error} />
      <button className="btn-primary" disabled={!files.length || !form.title.trim() || !!s.progress}>Upload video</button>
    </form>
  );
}
