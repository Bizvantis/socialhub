import { useParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Library } from "../api/endpoints";
import { VideoCard } from "../components/cards";
import { EmptyState, ErrorBox, Spinner, UserName } from "../components/ui";
import { useQuery } from "../lib/hooks";

export default function PlaylistPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const pl = useQuery(() => Library.playlist(Number(id)), [id]);
  if (pl.loading) return <Spinner />;
  if (pl.error) return <ErrorBox error={pl.error.status === 404 ? new Error("Playlist not found.") : pl.error} />;
  const { playlist, videos } = pl.data;
  const mine = user?.userId === playlist.ownerId;
  return (
    <div className="grid gap-5 lg:grid-cols-[300px_1fr]">
      <div className="card h-fit p-5">
        <h1 className="font-serif text-2xl font-semibold tracking-tight">{playlist.title}</h1>
        <p className="mt-1 text-sm"><UserName userId={playlist.ownerId} /></p>
        <p className="text-sm text-zinc-500">{playlist.videoCount} videos · {playlist.visibility.toLowerCase()}</p>
        {playlist.description && <p className="mt-2 text-sm">{playlist.description}</p>}
      </div>
      <div className="grid content-start gap-3">
        {videos.length === 0 && <EmptyState icon="music" title="This playlist is empty" />}
        {videos.map((v, i) => (
          <div key={v.id} className="flex items-center gap-3">
            <span className="w-6 text-center text-sm text-zinc-500">{i + 1}</span>
            <div className="flex-1"><VideoCard video={v} compactLayout /></div>
            {mine && <button className="btn-ghost" onClick={async () => { await Library.removeFromPlaylist(playlist.id, v.id); pl.reload(); }}>Remove</button>}
          </div>
        ))}
      </div>
    </div>
  );
}
