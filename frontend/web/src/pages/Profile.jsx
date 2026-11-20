import { useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Posts, Users } from "../api/endpoints";
import { FollowButton } from "../components/cards";
import { PostGrid } from "../components/grids";
import { Avatar, ErrorBox, LoadMore, Modal, Spinner, UserName } from "../components/ui";
import { compact, useCursorList, useInView, useQuery } from "../lib/hooks";

export default function Profile() {
  const { username } = useParams();
  const { user } = useAuth();
  const profile = useQuery(() => Users.byUsername(username), [username]);
  const p = profile.data;
  const posts = useCursorList((cursor) => (p ? Posts.byUser(p.userId, cursor) : Promise.resolve({ items: [], nextCursor: null })), [p?.userId]);
  const sentinel = useInView(() => posts.loadMore());
  const [list, setList] = useState(null); // "followers" | "following"

  if (profile.loading) return <Spinner />;
  if (profile.error) return <ErrorBox error={profile.error.status === 404 ? new Error("User not found.") : profile.error} />;
  const mine = user?.userId === p.userId;

  return (
    <div className="mx-auto grid max-w-4xl gap-8">
      <header className="flex flex-wrap items-center gap-6 sm:gap-10">
        <Avatar userId={p.userId} size={110} ring />
        <div className="grid flex-1 gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-xl font-semibold">{p.username}</h1>
            {mine ? <Link to="/settings" className="btn-secondary">Edit profile</Link>
              : <FollowButton userId={p.userId} initiallyFollowing={p.followedByMe}
                  onChange={(f) => profile.setData({ ...p, followedByMe: f, followerCount: p.followerCount + (f ? 1 : -1) })} />}
          </div>
          <div className="flex gap-6 text-sm">
            <button onClick={() => setList("followers")}><b>{compact(p.followerCount)}</b> followers</button>
            <button onClick={() => setList("following")}><b>{compact(p.followingCount)}</b> following</button>
          </div>
          <div className="text-sm">
            <p className="font-semibold">{p.displayName}</p>
            {p.bio && <p className="whitespace-pre-wrap text-zinc-600 dark:text-zinc-400">{p.bio}</p>}
            {p.privateAccount && <p className="mt-1 text-xs text-zinc-500">🔒 Private account</p>}
          </div>
        </div>
      </header>
      <section aria-label="Posts">
        {(posts.items.length > 0 || posts.done) && <PostGrid posts={posts.items} empty={mine ? "Share your first post" : "No posts yet"} />}
        <LoadMore list={posts} sentinelRef={sentinel} />
      </section>
      <FollowList userId={p.userId} kind={list} onClose={() => setList(null)} />
    </div>
  );
}

function FollowList({ userId, kind, onClose }) {
  const data = useQuery(() => (kind ? (kind === "followers" ? Users.followers(userId) : Users.following(userId)) : Promise.resolve(null)), [userId, kind]);
  return (
    <Modal open={!!kind} onClose={onClose} title={kind === "followers" ? "Followers" : "Following"}>
      {data.loading ? <Spinner /> : (
        <ul className="grid gap-3">
          {data.data?.content?.map((u) => (
            <li key={u.userId} className="flex items-center gap-3" onClick={onClose}>
              <Avatar userId={u.userId} size={36} />
              <div className="text-sm"><UserName userId={u.userId} /><p className="text-zinc-500">{u.displayName}</p></div>
            </li>
          ))}
          {data.data?.content?.length === 0 && <p className="text-sm text-zinc-500">Nobody yet.</p>}
        </ul>
      )}
    </Modal>
  );
}
