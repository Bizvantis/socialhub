import { useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Engagement } from "../api/endpoints";
import { timeAgo, useCursorList } from "../lib/hooks";
import { RichText } from "./cards";
import { Avatar, ErrorBox, Spinner, UserName } from "./ui";

/** Comment thread for a VIDEO or POST: newest/top sorting, one level of replies. */
export default function Comments({ type, id, ownerId }) {
  const { user } = useAuth();
  const [sort, setSort] = useState("new");
  const list = useCursorList((cursor) => Engagement.comments(type, id, sort, cursor), [type, id, sort]);

  return (
    <section aria-label="Comments" className="grid gap-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Comments</h2>
        <select className="input w-auto py-1.5" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort comments">
          <option value="new">Newest</option>
          <option value="top">Top</option>
        </select>
      </div>
      {user ? <Composer onSubmit={async (body) => {
        const c = await Engagement.comment(type, id, body);
        list.setItems((items) => [c, ...items]);
      }} /> : <p className="text-sm text-zinc-500"><Link to="/login" className="text-violet-brand">Log in</Link> to comment.</p>}
      <ErrorBox error={list.error} onRetry={list.reload} />
      <ul className="grid gap-4">
        {list.items.map((c) => (
          <CommentItem key={c.id} comment={c} type={type} id={id} ownerId={ownerId}
            onDeleted={() => list.setItems((items) => items.filter((x) => x.id !== c.id))} />
        ))}
      </ul>
      {list.loading && <Spinner />}
      {!list.loading && !list.done && <button className="btn-secondary justify-self-center" onClick={list.loadMore}>More comments</button>}
      {!list.loading && list.done && list.items.length === 0 && <p className="text-sm text-zinc-500">No comments yet. Start the conversation.</p>}
    </section>
  );
}

function Composer({ onSubmit, placeholder = "Add a comment…", autoFocus = false, onCancel }) {
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  async function submit(e) {
    e.preventDefault();
    if (!body.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onSubmit(body.trim());
      setBody("");
      onCancel?.();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit} className="grid gap-2">
      <textarea className="input min-h-[2.75rem] resize-y" rows={1} maxLength={2200} value={body} autoFocus={autoFocus}
        onChange={(e) => setBody(e.target.value)} placeholder={placeholder} aria-label={placeholder} />
      {error && <ErrorBox error={error} />}
      {body && (
        <div className="flex justify-end gap-2">
          {onCancel && <button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button>}
          <button className="btn-primary" disabled={busy}>{busy ? "Posting…" : "Comment"}</button>
        </div>
      )}
    </form>
  );
}

function CommentItem({ comment, type, id, ownerId, onDeleted, isReply = false }) {
  const { user } = useAuth();
  const [c, setC] = useState(comment);
  const [replying, setReplying] = useState(false);
  const [showReplies, setShowReplies] = useState(false);
  const [extraReplies, setExtraReplies] = useState([]);
  const canDelete = user && (user.userId === c.authorId || user.userId === ownerId);

  async function toggleLike() {
    const liked = c.likedByMe;
    setC({ ...c, likedByMe: !liked, likeCount: c.likeCount + (liked ? -1 : 1) });
    try {
      await (liked ? Engagement.unreact("COMMENT", c.id) : Engagement.react("COMMENT", c.id, "LIKE"));
    } catch {
      setC(c);
    }
  }

  return (
    <li className="flex gap-3">
      <Avatar userId={c.authorId} size={isReply ? 28 : 36} />
      <div className="min-w-0 flex-1">
        <div className="text-sm">
          <UserName userId={c.authorId} />
          {c.byCreator && <span className="ml-1.5 rounded bg-zinc-200 px-1.5 text-[10px] font-semibold uppercase dark:bg-zinc-800">Creator</span>}
          <span className="ml-2 text-xs text-zinc-500">{timeAgo(c.createdAt)}{c.editedAt && " (edited)"}</span>
        </div>
        <div className="mt-0.5 text-sm">
          {c.replyToUserId && isReply && <span className="mr-1 text-violet-brand">@<UserName userId={c.replyToUserId} link={false} className="font-normal" /></span>}
          <RichText text={c.body} />
        </div>
        <div className="mt-1 flex items-center gap-3 text-xs text-zinc-500">
          <button onClick={toggleLike} disabled={!user} aria-pressed={c.likedByMe} className="hover:text-brand-500">
            {c.likedByMe ? "❤️" : "🤍"} {c.likeCount || ""}
          </button>
          {user && <button onClick={() => setReplying(!replying)} className="font-semibold hover:text-zinc-800 dark:hover:text-zinc-200">Reply</button>}
          {canDelete && <button className="hover:text-red-500" onClick={async () => { await Engagement.deleteComment(c.id); onDeleted(); }}>Delete</button>}
        </div>
        {replying && (
          <div className="mt-2">
            <Composer autoFocus placeholder="Write a reply…" onCancel={() => setReplying(false)} onSubmit={async (body) => {
              const r = await Engagement.comment(type, id, body, c.id);
              setExtraReplies((x) => [...x, r]);
              setShowReplies(true);
            }} />
          </div>
        )}
        {!isReply && (c.replyCount > 0 || extraReplies.length > 0) && (
          <div className="mt-2">
            <button className="text-sm font-semibold text-violet-brand" onClick={() => setShowReplies(!showReplies)}>
              {showReplies ? "Hide replies" : `View ${Math.max(c.replyCount, extraReplies.length)} replies`}
            </button>
            {showReplies && <Replies parent={c} type={type} id={id} ownerId={ownerId} extra={extraReplies} />}
          </div>
        )}
      </div>
    </li>
  );
}

function Replies({ parent, type, id, ownerId, extra }) {
  const list = useCursorList((cursor) => Engagement.replies(parent.id, cursor), [parent.id]);
  const shown = [...list.items, ...extra.filter((e) => !list.items.some((i) => i.id === e.id))];
  return (
    <ul className="mt-3 grid gap-3">
      {shown.map((r) => (
        <CommentItem key={r.id} comment={r} type={type} id={id} ownerId={ownerId} isReply
          onDeleted={() => list.setItems((items) => items.filter((x) => x.id !== r.id))} />
      ))}
      {list.loading && <Spinner className="py-2" />}
      {!list.loading && !list.done && <button className="justify-self-start text-sm text-violet-brand" onClick={list.loadMore}>More replies</button>}
    </ul>
  );
}
