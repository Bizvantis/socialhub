import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Notifications, Users } from "../api/endpoints";
import { Avatar, ErrorBox } from "../components/ui";
import { usersCache } from "../lib/hooks";
import { IMAGE_TYPES, uploadFile } from "../lib/upload";

export default function Settings() {
  const { user, reloadProfile } = useAuth();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ displayName: user.displayName ?? "", bio: user.bio ?? "", privateAccount: !!user.privateAccount });
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState(null);
  const [email, setEmail] = useState(true);
  const [photo, setPhoto] = useState({ busy: false, progress: 0, error: null });

  async function changePhoto(file) {
    if (!file) return;
    if (!IMAGE_TYPES.includes(file.type)) return setPhoto({ busy: false, progress: 0, error: new Error("Use a JPEG, PNG or WebP image") });
    if (file.size > 5 * 1024 * 1024) return setPhoto({ busy: false, progress: 0, error: new Error("Profile photos can be up to 5 MB") });
    setPhoto({ busy: true, progress: 0, error: null });
    try {
      const media = await uploadFile("AVATAR", file, (progress) => setPhoto((p) => ({ ...p, progress })));
      await Users.updateMe({ avatarMediaId: media.id });
      usersCache.invalidate(user.userId);
      await reloadProfile();
      setPhoto({ busy: false, progress: 0, error: null });
    } catch (err) {
      setPhoto({ busy: false, progress: 0, error: err });
    }
  }

  async function removePhoto() {
    await Users.updateMe({ removeAvatar: true });
    usersCache.invalidate(user.userId);
    await reloadProfile();
  }

  return (
    <div className="mx-auto grid max-w-xl gap-6">
      {params.get("welcome") && (
        <div className="rounded-2xl bg-linear-to-r from-violet-brand to-brand-500 p-5 text-white">
          <p className="text-lg font-bold">Welcome to SocialHub, {user.username}! 🎉</p>
          <p className="text-sm opacity-90">Add a display name and bio, then <Link to="/explore" className="underline">explore</Link> to find people to follow.</p>
        </div>
      )}
      <form className="card grid gap-4 p-5" onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        try {
          await Users.updateMe({ displayName: form.displayName, bio: form.bio, privateAccount: form.privateAccount });
          usersCache.invalidate(user.userId);
          await reloadProfile();
          setSaved(true);
          setTimeout(() => setSaved(false), 2500);
        } catch (err) {
          setError(err);
        }
      }}>
        <h1 className="text-xl font-bold">Edit profile</h1>
        <div className="flex items-center gap-4">
          <Avatar userId={user.userId} size={72} />
          <div className="grid gap-1 text-sm">
            <div className="flex flex-wrap gap-2">
              <label className="btn-secondary cursor-pointer">
                {photo.busy ? `Uploading ${photo.progress}%` : "Change photo"}
                <input type="file" accept={IMAGE_TYPES.join(",")} className="hidden" disabled={photo.busy}
                  onChange={(e) => { changePhoto(e.target.files?.[0]); e.target.value = ""; }} />
              </label>
              {user.avatarMediaId && !photo.busy && <button type="button" className="btn-ghost" onClick={removePhoto}>Remove</button>}
            </div>
            <span className="text-xs text-zinc-500">JPEG, PNG or WebP, up to 5 MB</span>
            <ErrorBox error={photo.error} />
          </div>
        </div>
        <label className="grid gap-1 text-sm font-semibold">Display name
          <input className="input font-normal" maxLength={50} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </label>
        <label className="grid gap-1 text-sm font-semibold">Bio
          <textarea className="input font-normal" rows={3} maxLength={300} value={form.bio} onChange={(e) => setForm({ ...form, bio: e.target.value })} />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.privateAccount} onChange={(e) => setForm({ ...form, privateAccount: e.target.checked })} />
          Private account
        </label>
        <ErrorBox error={error} />
        <button className="btn-primary">{saved ? "Saved ✓" : "Save changes"}</button>
      </form>

      <section className="card grid gap-3 p-5">
        <h2 className="text-lg font-semibold">Notifications</h2>
        <label className="flex items-center justify-between text-sm">
          Email me about follows, comments, replies and mentions
          <input type="checkbox" checked={email} onChange={async (e) => { setEmail(e.target.checked); await Notifications.preferences(e.target.checked); }} />
        </label>
      </section>

      <section className="card grid gap-2 p-5 text-sm">
        <h2 className="text-lg font-semibold">Creator</h2>
        <p className="text-zinc-500">Publish long-form videos from your own channel.</p>
        <Link to="/studio" className="btn-secondary justify-self-start">Open channel studio</Link>
      </section>
    </div>
  );
}
